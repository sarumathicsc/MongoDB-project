# 🛒 InvenTrack – E-Commerce Inventory & Order Management System

A full-stack inventory and order management application built using **Core Java, MongoDB, HTML, CSS, and JavaScript**. The system enables users to manage products, process customer orders, and automatically update inventory in real time through a RESTful backend.

This project was developed to gain hands-on experience with backend development, MongoDB document databases, REST API design, and frontend-backend integration. It also demonstrates MongoDB aggregation using **$lookup** to generate sales reports and business insights.

---

## ✨ Features

- 📦 Product inventory management (Create, Read, Update, Delete)
- 🛒 Customer order management
- 🔄 Automatic inventory updates when orders are placed
- ↩️ Stock restoration when an order is cancelled
- 📊 Sales reports using MongoDB Aggregation (`$lookup`, `$group`, `$unwind`)
- 🔍 Search and filter products and orders
- 🌐 RESTful APIs built with Java HttpServer
- 💻 Responsive frontend using HTML, CSS, and JavaScript

---

## 🛠️ Tech Stack

### Frontend
- HTML5
- CSS3
- JavaScript

### Backend
- Core Java
- Java HttpServer

### Database
- MongoDB
- MongoDB Java Driver

### Tools
- MongoDB Compass
- VS Code
- Git & GitHub
- Postman

---

## 🎯 Learning Outcomes

Through this project, I gained practical experience in:

- Designing and developing RESTful APIs using Core Java
- Integrating Java applications with MongoDB
- Performing CRUD operations on multiple collections
- Implementing inventory management and business validation logic
- Using MongoDB Aggregation Framework (`$lookup`, `$group`, `$unwind`)
- Building a modular backend architecture
- Connecting frontend and backend components using HTTP requests
- Managing projects with Git and GitHub

---
## 📁 Folder Structure

```
EcommerceInventorySystem/
│
├── frontend/
│   ├── index.html          → Home page
│   ├── products.html       → Product inventory (CRUD + low-stock dashboard)
│   ├── orders.html         → Order creation, tracking, and sales reports
│   ├── css/style.css       → Responsive teal-theme styling
│   └── js/
│       ├── common.js       → Shared nav toggle, toast, formatting helpers
│       ├── products.js     → Product page logic
│       └── orders.js       → Order page logic (dynamic item rows, reports)
│
├── backend/
│   ├── src/
│   │   ├── Product.java         → Product model
│   │   ├── OrderItem.java       → One line-item inside an order
│   │   ├── Order.java           → Order model (embeds a list of OrderItem)
│   │   ├── OrderException.java  → Custom checked exception (stock/validation errors)
│   │   ├── MongoConnection.java → Singleton MongoDB connection
│   │   ├── ProductDAO.java      → Product CRUD + stock logic + aggregation
│   │   ├── OrderDAO.java        → Order CRUD + $lookup aggregation reports
│   │   ├── HttpUtils.java       → Shared request/response helper methods
│   │   ├── ProductHandler.java  → REST routes for /api/products
│   │   ├── OrderHandler.java    → REST routes for /api/orders
│   │   └── Main.java            → Starts the server (entry point)
│   └── lib/                     → External .jar libraries go here
│
├── database/
│   ├── sampleProducts.json               → 10 sample products, ready to import
│   └── sampleOrders_reference_only.json  → Example order structure (see note below)
│
├── docs/
│   └── Documentation.md    → MongoDB tutorial ($lookup focus), architecture, academic docs, viva Q&A
│
└── README.md
```

> **Note on sample orders:** Unlike products, don't bulk-import `sampleOrders_reference_only.json` directly into MongoDB — it's provided only as a reference for the document structure. Orders must be placed **through the app's UI** (`orders.html`) so that the Java backend correctly deducts product stock and generates order IDs. This mirrors how a real e-commerce system works.

---

## ✅ Prerequisites

Same as the first project — if you already set these up, skip to Step 1:
1. **JDK 17+** — https://adoptium.net
2. **VS Code** with "Extension Pack for Java" and "Live Server" extensions
3. **MongoDB Community Server** (running as a Windows Service)
4. **MongoDB Compass**

---

## 🗄️ Step 1 — Set Up the Database

1. Open **MongoDB Compass**, connect to `mongodb://localhost:27017`.
2. Create a new database named **`EcommerceInventoryDB`** with a collection named **`products`**.
3. Inside `EcommerceInventoryDB`, create a second collection named **`orders`** (Compass: right-click the database → "Create Collection").
4. Import sample data into `products`:
   - Click the `products` collection → **Add Data → Import File** → select `database/sampleProducts.json` → file type **JSON** → **Import**.
5. Leave `orders` empty for now — you'll create orders through the app itself.

---

## ⚙️ Step 2 — Set Up the Backend

### 2.1 Reuse or Download the Required Libraries

If you still have the `.jar` files from the Student Management System project (`mongodb-driver-sync`, `mongodb-driver-core`, `bson`, `json-20240303.jar`), just **copy them** into this project's `backend/lib/` folder — they work identically here.

Otherwise, download them fresh (see Project 1's README for direct links to https://mvnrepository.com) and place them in `backend/lib/`.

### 2.2 Compile the Backend

```bash
cd backend
javac -cp "lib/*" -d out src/*.java
```

### 2.3 Run the Backend Server

```bash
java -cp "out;lib/*" Main
```

You should see:
```
=================================================
 InvenTrack - E-Commerce Inventory Backend Server
 Running at: http://localhost:8080
 Products API: http://localhost:8080/api/products
 Orders API:   http://localhost:8080/api/orders
 Press Ctrl+C to stop the server.
=================================================
```

**Keep this terminal open.**

### 2.4 Quick Test

Visit `http://localhost:8080/api/products` in your browser — you should see your 10 imported products as JSON.

---

## 🌐 Step 3 — Run the Frontend

1. In VS Code, right-click `frontend/index.html` → **"Open with Live Server"**.
2. From the Home page, go to **Products** to review/manage inventory, or **Orders** to place a test order.
3. Try placing an order on `orders.html`: pick 1–2 products, set quantities, submit. Watch the product's stock quantity drop when you check `products.html` afterward — and watch the **Sales by Category** and **Top Selling Products** reports populate on the Orders page.
4. Try changing an order's status to **Cancelled** and confirm the stock is restored.

---

## 🔌 API Reference

### Products
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/products` | Get all products |
| GET | `/api/products/{id}` | Get one product |
| GET | `/api/products/search?query=&category=&stockStatus=` | Search/filter (`stockStatus`: `low` or `out`) |
| GET | `/api/products/stats` | Aggregation: total products, stock value, low-stock count, category count |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |

### Orders
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/orders` | Get all orders (newest first) |
| GET | `/api/orders/{id}` | Get one order |
| GET | `/api/orders/search?query=&status=` | Search/filter orders |
| GET | `/api/orders/stats` | Aggregation: total orders, revenue, pending count, avg order value |
| GET | `/api/orders/reports/sales-by-category` | **$lookup** report: revenue/units sold per product category |
| GET | `/api/orders/reports/top-products?limit=5` | Top-selling products by revenue |
| POST | `/api/orders` | Place a new order (deducts stock automatically) |
| PUT | `/api/orders/{id}/status` | Update order status (cancelling restores stock) |
| DELETE | `/api/orders/{id}` | Delete an order |

---

## 🛠️ Troubleshooting

Same tips as Project 1's README apply — check `services.msc` for MongoDB, ensure the backend terminal is running, and use Live Server (not double-clicking HTML files) to avoid CORS issues.

**New for this project:** if placing an order fails with "Insufficient stock" or "Product not found", that's the `OrderException` business-logic check working correctly — try a product with available stock (check `products.html` for current quantities).

---

See `docs/Documentation.md` for the full report: a focused `$lookup`/`$unwind` MongoDB tutorial, architecture diagrams, academic documentation, and 50+ viva questions.
---

## 🚀 Future Improvements

- User authentication and role-based access
- Product image upload
- Sales analytics dashboard
- Barcode and QR code support
- Email notifications
- Docker deployment
- Cloud database support

---

## 👩‍💻 Author

**Sarumathi H**

- 🎓 B.Tech – Artificial Intelligence & Data Science
- 💼 Aspiring Software Engineer | AI & Cloud Enthusiast

**GitHub:** https://github.com/sarumathicsc

**LinkedIn:** https://www.linkedin.com/in/sarumathi0710

---

⭐ If you found this project useful, consider giving it a star.
