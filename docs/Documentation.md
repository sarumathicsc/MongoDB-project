# InvenTrack — Complete Documentation

---

# PART A — MongoDB Tutorial: Relationships & $lookup

This project introduces a concept the Student Management System didn't need: **relationships between two collections** (`products` and `orders`). MongoDB offers two ways to model relationships:

## 1. Embedding vs. Referencing

**Embedding** (used for Order → OrderItems): Each order document contains its items directly, nested inside an `items` array:
```json
{
  "orderId": "ORD1001",
  "items": [
    { "productId": "PRD001", "productName": "Wireless Mouse", "quantity": 2, "unitPrice": 599, "subtotal": 1198 }
  ]
}
```
**Why embed here?** Order items belong exclusively to one order, are always read together with it, and should keep a historical snapshot of price/name even if the product changes later. Embedding is fast (one query gets everything) and matches how the data is actually used.

**Referencing** (used for Order → Product): Each order item stores only a `productId` (a *reference*, like a foreign key), not the full product document:
```json
{ "productId": "PRD001", "quantity": 2 }
```
**Why reference here?** Products are shared across MANY orders and are managed independently (their category, supplier, and current stock change over time) — duplicating the entire product document into every order would waste space and go stale.

This mix of embedding + referencing in one schema is a very common, realistic MongoDB design pattern.

## 2. `$lookup` — Joining Collections

**Syntax:**
```js
{
  $lookup: {
    from: "products",          // the collection to join with
    localField: "items.productId",  // field in THIS collection (orders)
    foreignField: "productId",      // matching field in the "from" collection (products)
    as: "productInfo"               // name of the new array field holding matched documents
  }
}
```
**Purpose:** Performs a LEFT JOIN — for every document in the pipeline, finds matching document(s) in `products` and attaches them as an array field. This is MongoDB's equivalent of SQL's `JOIN`.

**Sample data:**
```json
// orders collection (after $unwind, one item per document)
{ "items": { "productId": "PRD001", "quantity": 2, "subtotal": 1198 } }

// products collection
{ "productId": "PRD001", "name": "Wireless Mouse", "category": "Electronics" }
```

**Expected output after $lookup:**
```json
{
  "items": { "productId": "PRD001", "quantity": 2, "subtotal": 1198 },
  "productInfo": [
    { "productId": "PRD001", "name": "Wireless Mouse", "category": "Electronics" }
  ]
}
```
Notice `productInfo` is an ARRAY (even though only one product matched) — that's why our pipeline follows `$lookup` with another `$unwind` to flatten it into a plain object before grouping by category.

## 3. Our Full "Sales by Category" Pipeline (from `OrderDAO.getSalesByCategoryReport()`)

```js
db.orders.aggregate([
  { $match: { orderStatus: { $ne: "Cancelled" } } },  // ignore cancelled orders
  { $unwind: "$items" },                               // one document per order ITEM
  { $lookup: {
      from: "products",
      localField: "items.productId",
      foreignField: "productId",
      as: "productInfo"
  }},
  { $unwind: "$productInfo" },                          // flatten the joined array
  { $group: {
      _id: "$productInfo.category",
      unitsSold: { $sum: "$items.quantity" },
      revenue: { $sum: "$items.subtotal" }
  }},
  { $sort: { revenue: -1 } }
])
```
**Expected Output (example):**
```json
{ "_id": "Electronics", "unitsSold": 6, "revenue": 5695.00 }
{ "_id": "Books", "unitsSold": 1, "revenue": 649.00 }
{ "_id": "Groceries", "unitsSold": 2, "revenue": 1798.00 }
```

## 4. Other Operators Used in This Project

| Operator | Used in | Purpose |
|---|---|---|
| `$inc` | `ProductDAO.reduceStock()` / `restoreStock()` | Atomically increases/decreases a numeric field (e.g., `stockQuantity`) without a separate read-then-write |
| `$expr` | `ProductDAO.searchProducts()` (low-stock filter) | Lets a filter COMPARE TWO FIELDS in the same document (`stockQuantity <= reorderLevel`), which plain `$lte` can't do since it only compares a field to a fixed value |
| `$multiply` | `ProductDAO.getProductStats()` | Multiplies two fields (`price * stockQuantity`) inside an aggregation to compute total stock value |
| `$addToSet` | Stats aggregations | Collects only the UNIQUE values of a field across a group (used to count distinct categories) |
| `$count` | Low-stock count pipeline | Shorthand stage that outputs a single document with the count of documents that reached this stage |
| `$ne` | Revenue aggregation | Excludes cancelled orders (`orderStatus` "not equal to" `"Cancelled"`) from revenue totals |

## 5. CRUD Recap (same operators as Project 1, applied to two collections)

```js
// Create a product
db.products.insertOne({ productId: "PRD011", name: "Bluetooth Speaker", category: "Electronics", price: 1499, stockQuantity: 40, reorderLevel: 10, supplier: "TechSupply Co.", description: "Portable speaker" })

// Read: all electronics under ₹1000
db.products.find({ category: "Electronics", price: { $lt: 1000 } })

// Update: restock a product
db.products.updateOne({ productId: "PRD005" }, { $inc: { stockQuantity: 50 } })

// Delete: remove a discontinued product
db.products.deleteOne({ productId: "PRD099" })
```

---

# PART B — Architecture & Integration

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         BROWSER (Client)                         │
│  ┌────────────┐  ┌──────────────┐  ┌───────────────────────────┐│
│  │ index.html │  │ products.html│  │ orders.html                ││
│  └────────────┘  └──────────────┘  └───────────────────────────┘│
│         common.js / products.js / orders.js / style.css          │
└───────────────────────────┬────────────────────────────────────┘
                             │  fetch() -> HTTP (JSON)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              JAVA BACKEND  (http://localhost:8080)                │
│  ┌───────────┐  ┌────────────────┐   ┌──────────────────────┐    │
│  │ Main.java │─▶│ ProductHandler  │──▶│ ProductDAO            │   │
│  │           │  │ /api/products   │   │ (CRUD + stock $inc +  │   │
│  │           │  └────────────────┘   │  aggregation)          │   │
│  │           │  ┌────────────────┐   └──────────┬─────────────┘   │
│  │           │─▶│ OrderHandler    │──▶┌──────────▼─────────────┐  │
│  └───────────┘  │ /api/orders     │   │ OrderDAO                │  │
│                  └────────────────┘   │ (CRUD + $lookup reports)│  │
│                                        │ calls ProductDAO for    │  │
│                                        │ stock checks/updates    │  │
│                                        └──────────┬─────────────┘  │
│                                        ┌───────────▼─────────────┐ │
│                                        │  MongoConnection.java    │ │
│                                        └───────────┬─────────────┘ │
└────────────────────────────────────────────────────┼───────────────┘
                                                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                MongoDB  (localhost:27017)                         │
│   Database: EcommerceInventoryDB                                  │
│   Collections:  products  ◀── referenced by ──▶  orders.items[]   │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow — "Placing an Order" (the most complex flow in this project)

1. **User Input:** On `orders.html`, the user fills customer details and clicks "Add Item" one or more times, picking a product and quantity from each dropdown (populated from live `/api/products` data).
2. **Client-side Total Calculation:** `orders.js` recalculates a running total in real time as the user changes selections, purely for display — the actual authoritative price comes from the server.
3. **Submit:** On form submit, `orders.js` collects `{ productId, quantity }` pairs (NOT price — the client never sends prices, preventing manipulation) and POSTs them to `/api/orders`.
4. **Routing:** `Main.java`'s server routes the request to `OrderHandler.handlePost()`.
5. **Parsing:** The handler reads customer fields and the items array, generates a unique `orderId`, and calls `orderDAO.createOrder(...)`.
6. **Validation Pass (STEP 1 in `OrderDAO`):** For EVERY requested item, the DAO looks up the live `Product` via `ProductDAO.getProductByProductId()`. If any product doesn't exist or has insufficient stock, an `OrderException` is thrown immediately — BEFORE any database writes happen, so nothing is left half-done.
7. **Stock Deduction (STEP 2):** Once all items pass validation, the DAO loops through them again and calls `ProductDAO.reduceStock()` for each, using MongoDB's `$inc` operator.
8. **Order Insert (STEP 3):** The DAO builds the final `Order` document (with server-confirmed prices and names) and inserts it into the `orders` collection.
9. **Response:** `OrderHandler` returns HTTP 201 with the new order's ID. If an `OrderException` was thrown instead, it returns HTTP 400 with a clear message (e.g., "Insufficient stock for 'Yoga Mat'. Available: 0, Requested: 2").
10. **Frontend Refresh:** `orders.js` shows a success/error toast, resets the form, and reloads the product dropdown (to reflect new stock), the orders table, dashboard stats, and both aggregation reports — all via separate `fetch()` calls.

---

# PART C — Academic Documentation

## Aim
To design and develop a full-stack **E-Commerce Inventory & Order Management System** that enables businesses to manage product stock and process customer orders through a responsive web interface, backed by a NoSQL database with real-time sales analytics.

## Objectives
- Model a realistic multi-collection relationship (Products ↔ Orders) in MongoDB.
- Implement atomic, consistent stock management tied to order placement and cancellation.
- Demonstrate MongoDB's `$lookup` aggregation stage for cross-collection reporting.
- Apply custom exception handling for predictable business-rule violations.
- Build a responsive, professional inventory and order-tracking UI.

## Abstract
InvenTrack is a full-stack web application for managing product inventory and customer orders. It uses HTML5, CSS3, and JavaScript on the frontend; Core Java (via Java's native HTTP server) on the backend; and MongoDB as the database, storing data across two related collections — `products` and `orders`. The system validates stock availability before confirming an order, automatically deducts inventory upon purchase, and restores it if an order is cancelled — all handled through server-side business logic and a custom `OrderException` class. A key highlight is its use of MongoDB's aggregation framework, particularly the `$lookup` stage, to generate a "Sales by Category" report that joins order line-items with product metadata not stored in the order itself, alongside a "Top Selling Products" leaderboard — demonstrating how NoSQL databases can still support relational-style reporting when needed.

## Problem Statement
Small retailers and online sellers often manage inventory and orders using disconnected spreadsheets, leading to overselling out-of-stock items, no visibility into which categories or products actually drive revenue, and manual, error-prone stock adjustments when orders are cancelled.

## Existing System
- Manual stock tracking in spreadsheets, disconnected from actual sales.
- No automatic stock deduction — sellers must remember to update quantities.
- No easy way to see which product categories are most profitable.
- Cancelled orders require manually re-adding stock, often forgotten.

## Proposed System
- Centralized product and order data in MongoDB, always in sync.
- Automatic, validated stock deduction at the moment of purchase.
- Automatic stock restoration when an order is cancelled.
- Real-time low-stock alerts based on a configurable reorder level.
- Cross-collection sales analytics (`$lookup`) without needing a separate reporting tool.

## Advantages
- Prevents overselling by validating stock before confirming any order.
- Reduces manual bookkeeping errors through automated stock adjustment.
- Provides actionable business insight (top categories, top products) instantly.
- Reuses a lightweight, framework-free architecture — easy to learn, deploy, and extend.

## Modules
1. **Home Module** — Landing page introducing the system.
2. **Product Inventory Module** — Add/edit/delete products, view stock, low-stock alerts.
3. **Search & Filter Module (Products)** — Keyword, category, and stock-status filtering.
4. **Order Placement Module** — Multi-item order creation with live stock validation.
5. **Order Tracking Module** — Status updates (Pending → Shipped → Delivered/Cancelled).
6. **Order Management Module** — View order details, search/filter, delete orders.
7. **Sales Analytics Module** — `$lookup`-based category report and top-products leaderboard.

## Software Requirements
| Component | Requirement |
|---|---|
| Operating System | Windows 11 |
| Backend Language | Java (JDK 17+) |
| Database | MongoDB Community Server |
| Database GUI | MongoDB Compass |
| Code Editor | Visual Studio Code |
| Browser | Google Chrome / Microsoft Edge (latest) |

## Hardware Requirements
| Component | Minimum Requirement |
|---|---|
| Processor | Dual-core 2 GHz or higher |
| RAM | 4 GB (8 GB recommended) |
| Storage | 2 GB free disk space |
| Display | 1366×768 resolution or higher |

## Architecture Diagram
See **Part B** above.

## Flowchart — "Place Order" Operation
```
              ┌─────────────┐
              │    START    │
              └──────┬──────┘
                     ▼
       ┌──────────────────────────┐
       │ User adds items + fills   │
       │ customer details          │
       └──────────┬────────────────┘
                  ▼
             ◇ Form valid? ◇
            NO │           │ YES
        ┌───────┘           └────────┐
        ▼                            ▼
┌───────────────┐         ┌───────────────────────┐
│ Show inline    │         │ POST /api/orders        │
│ error messages │         └───────────┬─────────────┘
└───────┬────────┘                     ▼
        │              ┌───────────────────────────────┐
        │              │ For each item: does product     │
        │              │ exist AND have enough stock?     │
        │              └───────────────┬───────────────────┘
        │                     NO │              │ YES (all items)
        │              ┌─────────▼─────┐        ▼
        │              │ Throw          │  ┌─────────────────────┐
        │              │ OrderException │  │ Deduct stock for      │
        │              │ -> HTTP 400    │  │ each item ($inc)       │
        │              └───────┬────────┘  └───────────┬───────────┘
        │                      │                        ▼
        │                      │           ┌──────────────────────┐
        │                      │           │ Insert order document  │
        │                      │           │ -> HTTP 201             │
        │                      │           └───────────┬───────────┘
        │                      ▼                        ▼
        │              ┌────────────────┐   ┌──────────────────────┐
        │              │ Show error toast│   │ Show success toast,    │
        │              │                 │   │ refresh table/stats/   │
        │              │                 │   │ reports & product list │
        │              └────────┬────────┘   └───────────┬───────────┘
        └─────────────────────► └─────────────┬───────────┘
                                                ▼
                                        ┌──────────────┐
                                        │     END      │
                                        └──────────────┘
```

## Algorithm — Place Order
```
1. START
2. User fills customer details and adds one or more order items (product + quantity).
3. Frontend validates required fields and that at least one item is present.
4. IF validation fails: show inline errors, GOTO step 2.
5. ELSE: send POST /api/orders with customer details + items[{productId, quantity}].
6. Backend generates a unique orderId.
7. FOR EACH requested item:
       a. Look up the product by productId.
       b. IF product does not exist: throw OrderException, GOTO step 10.
       c. IF requested quantity > available stock: throw OrderException, GOTO step 10.
       d. Record the item with the product's CURRENT name and price.
8. Calculate totalAmount as the sum of all item subtotals.
9. FOR EACH validated item: reduce the product's stockQuantity by the ordered quantity.
10. IF an OrderException was thrown:
        Return HTTP 400 with the specific error message.
    ELSE:
        Insert the order document (status = "Pending") into MongoDB.
        Return HTTP 201 with the new order's ID.
11. Frontend shows a success or error toast accordingly.
12. IF success: reset the form, and reload the product list, order table, stats, and reports.
13. END
```

## Conclusion
InvenTrack extends the concepts learned in the Student Management System project into a more realistic, multi-collection business domain. It demonstrates how MongoDB's flexible document model can represent both embedded (order items) and referenced (product lookups) relationships within the same schema, and how the aggregation framework's `$lookup` stage enables SQL-style joins when cross-collection reporting is needed. The project also reinforces solid Java backend practices: custom checked exceptions for business-rule violations, atomic field updates, and a clean separation between the DAO (data access), Handler (API routing), and Model (data structure) layers.

## Future Enhancements
- Add supplier purchase-order generation when stock falls below reorder level.
- Add user authentication for admin vs. customer-facing views.
- Add a shopping-cart style multi-step checkout instead of a single form.
- Add pagination and CSV/PDF export for large order histories.
- Add real-time low-stock email/SMS alerts.
- Deploy to a cloud platform (MongoDB Atlas + cloud VM) for remote, multi-user access.

---

# PART D — Viva Questions & Answers (50+)

## MongoDB Relationships & Aggregation (New Concepts)
1. **What is the difference between embedding and referencing in MongoDB?**
   Embedding nests related data directly inside a document (fast reads, used for order items); referencing stores just an ID pointing to a document in another collection (avoids duplication, used for products).
2. **When should you choose embedding over referencing?**
   When the nested data "belongs to" and is always accessed together with the parent, doesn't change independently, and won't grow unboundedly — like order line-items.
3. **What does the `$lookup` aggregation stage do?**
   Performs a left outer join, matching a field in the current collection with a field in another collection and attaching the matched documents as an array.
4. **Why did we need $lookup in this project if MongoDB is "schema-less"?**
   Because order items intentionally do NOT store product category (a referenced field) — we must join back to `products` to retrieve it for category-based reporting.
5. **Why does $lookup's output need a second $unwind?**
   Because $lookup always returns an ARRAY of matches (even if there's only one), so $unwind flattens that array into a plain embedded object for further processing.
6. **What does $unwind do in general?**
   Deconstructs an array field, outputting one separate document per array element — turning an order with 3 items into 3 documents.
7. **What is the purpose of $inc in an update operation?**
   Atomically increases (or decreases, with a negative number) a numeric field's value without needing to read the current value first, avoiding race conditions.
8. **Why is $inc safer for stock updates than reading the value in Java, subtracting, then writing it back?**
   Because two simultaneous orders could both read the same stock value before either writes back, causing a "lost update"; $inc lets MongoDB itself perform the arithmetic atomically.
9. **What does $expr allow you to do that a normal filter cannot?**
   Compare two FIELDS within the same document against each other (e.g., `stockQuantity <= reorderLevel`), whereas normal query operators only compare a field to a fixed value.
10. **What is $multiply used for in this project?**
    Inside an aggregation, it multiplies two fields (`price * stockQuantity`) to calculate each product's contribution to total stock value.
11. **What does $addToSet accomplish inside a $group stage?**
    Collects only the DISTINCT values of a field across the group — used here to count how many unique categories exist.
12. **How does $ne differ from $eq?**
    `$ne` matches documents where the field is NOT equal to a value; `$eq` matches where it IS equal.
13. **In `getSalesByCategoryReport()`, why do we $match orderStatus != "Cancelled" FIRST?**
    Filtering early (before $unwind/$lookup) reduces the number of documents the rest of the pipeline has to process, which is both more efficient and ensures cancelled orders never inflate revenue figures.
14. **What would happen if we skipped $match and included cancelled orders in revenue stats?**
    Revenue/average-order-value figures would be artificially inflated by orders that were never actually fulfilled.

## Java / Backend Concepts (New in This Project)
15. **What is a custom checked exception, and why did we create OrderException?**
    A subclass of `Exception` (not `RuntimeException`) representing a specific, expected failure case; we created `OrderException` so the compiler forces `OrderHandler` to explicitly handle stock/validation failures, and so we can safely return their message directly as an HTTP 400 (vs. an unexpected 500 error).
16. **What is the difference between a checked and an unchecked exception in Java?**
    Checked exceptions (extending `Exception`) must be declared with `throws` or caught at compile time; unchecked exceptions (extending `RuntimeException`) do not require this.
17. **Why does `OrderDAO.createOrder()` validate ALL items before deducting ANY stock?**
    To avoid a partially-completed order — if item #3 of 5 is out of stock, we don't want items #1 and #2 to have already had their stock deducted for an order that ultimately fails.
18. **What design pattern do ProductDAO and OrderDAO both follow, and why?**
    The DAO (Data Access Object) pattern — isolating all database code from the HTTP/API layer, making each class single-purpose and easier to test or modify.
19. **Why does OrderDAO have a ProductDAO field?**
    Placing an order requires cross-collection logic (checking and adjusting product stock), so OrderDAO composes ProductDAO rather than duplicating its database code.
20. **What is the purpose of the HttpUtils class?**
    It centralizes request-parsing and response-writing code shared by both ProductHandler and OrderHandler, following the "Don't Repeat Yourself" (DRY) principle.
21. **How does the backend prevent a client from manipulating order prices?**
    The client only sends `productId` and `quantity`; `OrderDAO.createOrder()` always looks up the CURRENT price from the `products` collection itself rather than trusting any price sent by the browser.
22. **What HTTP status code is returned when stock is insufficient, and why not 500?**
    HTTP 400 (Bad Request) — because it's a predictable, client-correctable error (not enough stock), not an unexpected server failure, which is what 500 signals.

## Reused Concepts from the Student Management System (Quick Recap)
23. **What is the Singleton pattern, and where is it used here?**
    A pattern ensuring only one instance of a class exists — `MongoConnection` uses it so the whole app shares one MongoDB connection.
24. **What is a POJO? Give an example from this project.**
    A Plain Old Java Object with private fields and public getters/setters — `Product`, `Order`, and `OrderItem` are all POJOs.
25. **What is a REST API, and which HTTP methods does this project use?**
    An architecture using standard HTTP methods on resources; this project uses GET, POST, PUT, and DELETE.
26. **Why do both handlers add CORS headers?**
    So the browser allows JavaScript running on a different origin (Live Server's port) to call the API on port 8080.
27. **Why is validation performed both in JavaScript and in Java?**
    JavaScript validation gives instant user feedback; Java validation protects the system even if the API is called directly, bypassing the browser.
28. **What is the purpose of escapeHtml() in the frontend JS?**
    Prevents malicious HTML/script content from being injected into the page when rendering user-supplied text (basic XSS protection).

## HTML / CSS / JavaScript (Applied Differently Here)
29. **Why does orders.html generate item rows dynamically with JavaScript instead of hardcoding them?**
    Because the number of products in an order isn't known in advance — JavaScript's `insertAdjacentHTML()` lets us add/remove rows on demand.
30. **What does `selectedOption.dataset.price` retrieve?**
    A custom `data-price` attribute stored on each `<option>` element, letting JavaScript read the product's price without a separate lookup.
31. **What is `Array.prototype.map()` used for in products.js/orders.js?**
    Transforms an array of data objects (like products or orders) into an array of HTML row strings, which are then joined into the table body.
32. **Why is the search bar's `<select>` for stock status using values like "low"/"out" instead of full labels?**
    Short, fixed values are easier and safer to match exactly on the backend than parsing full display text, and they stay stable even if display wording changes later.
33. **What CSS technique creates the responsive stat-card grid?**
    CSS Grid with `grid-template-columns: repeat(auto-fit, minmax(220px, 1fr))`, which automatically wraps cards onto new rows as the screen narrows.
34. **How does the "Low Stock" badge visually differ, and how is that decided?**
    JavaScript's `getStockStatus()` compares `stockQuantity` to `reorderLevel` and assigns a CSS class (`badge-low-stock`, `badge-out-stock`, or `badge-in-stock`) accordingly.

## General MongoDB CRUD (Recap, applied to two collections)
35. **What MongoDB method retrieves a single document from the products collection by its custom ID?**
    `db.products.findOne({ productId: "PRD001" })` — mirrored in Java by `ProductDAO.getProductByProductId()`.
36. **What is the purpose of `Filters.and()` and `Filters.or()` in the Java driver?**
    They build compound MongoDB query filters (`$and` / `$or`) programmatically instead of writing raw JSON query strings.
37. **How would you find all Pending orders placed by a specific customer?**
    `db.orders.find({ orderStatus: "Pending", customerName: "Arjun Kumar" })` — an implicit `$and` between the two conditions.
38. **What does `Sorts.descending("orderDate")` achieve in OrderDAO.getAllOrders()?**
    Ensures the most recently placed orders appear first in the results — equivalent to `{ $sort: { orderDate: -1 } }`.
39. **How is the "Cancel order restores stock" feature implemented?**
    `OrderDAO.updateOrderStatus()` checks if the new status is "Cancelled" and the order wasn't already cancelled; if so, it loops through the order's items and calls `ProductDAO.restoreStock()` for each before updating the status field.
40. **What does `collection.countDocuments(filter)` do, and where is it used?**
    Returns the number of documents matching a filter without retrieving them — used in `OrderDAO.getOrderStats()` to count total and pending orders efficiently.

---

*End of Documentation.*
