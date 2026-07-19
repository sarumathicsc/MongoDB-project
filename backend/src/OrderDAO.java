import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OrderDAO.java
 * -----------------------------------------------------
 * All database operations for the "orders" collection.
 *
 * This class demonstrates several concepts that go beyond the
 * Student Management System project:
 *  - Cross-collection business logic (checking/updating "products"
 *    stock while creating/cancelling an "orders" document).
 *  - MongoDB's $lookup aggregation stage, which JOINS the "orders"
 *    collection with the "products" collection (something SQL
 *    databases do with JOIN, and MongoDB does with $lookup).
 *  - Custom exception handling via OrderException.
 * -----------------------------------------------------
 */
public class OrderDAO {

    private final MongoCollection<Document> collection;
    private final ProductDAO productDAO; // used to check/adjust stock

    public OrderDAO() {
        MongoDatabase db = MongoConnection.getDatabase();
        this.collection = db.getCollection("orders");
        this.productDAO = new ProductDAO();
    }

    /* =====================================================
       CREATE ORDER
       This is the most important method in the whole project:
       it ties together validation, stock-checking, price lookup,
       and multi-document consistency (order + product stock).
       ===================================================== */
    /**
     * Creates a new order.
     *
     * @param orderId          custom order ID, e.g. "ORD1001"
     * @param customerName     customer's name
     * @param customerEmail    customer's email
     * @param customerPhone    customer's phone number
     * @param shippingAddress  delivery address
     * @param requestedItems   list of OrderItem objects containing ONLY
     *                         productId and quantity (sent by the client) —
     *                         this method fills in productName/unitPrice/subtotal
     *                         itself, using live data from the "products" collection.
     * @return the MongoDB _id of the newly created order
     * @throws OrderException if any product doesn't exist or has insufficient stock
     */
    public String createOrder(String orderId, String customerName, String customerEmail,
                               String customerPhone, String shippingAddress,
                               List<OrderItem> requestedItems) throws OrderException {

        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new OrderException("An order must contain at least one item.");
        }

        List<OrderItem> finalItems = new ArrayList<>();
        double totalAmount = 0.0;

        // STEP 1: Validate every item BEFORE making any changes to the database.
        // This "check everything first" approach avoids leaving the database in
        // a half-updated state if item #3 out of 5 turns out to be invalid.
        for (OrderItem requested : requestedItems) {
            Product product = productDAO.getProductByProductId(requested.getProductId());

            if (product == null) {
                throw new OrderException("Product not found: " + requested.getProductId());
            }
            if (product.getStockQuantity() < requested.getQuantity()) {
                throw new OrderException("Insufficient stock for '" + product.getName() +
                        "'. Available: " + product.getStockQuantity() + ", Requested: " + requested.getQuantity());
            }
            if (requested.getQuantity() <= 0) {
                throw new OrderException("Quantity must be greater than zero for: " + product.getName());
            }

            // Build the final item using LIVE product data (name + current price),
            // exactly as a real checkout system would snapshot the price at purchase time.
            OrderItem finalItem = new OrderItem(
                    product.getProductId(), product.getName(), requested.getQuantity(), product.getPrice()
            );
            finalItems.add(finalItem);
            totalAmount += finalItem.getSubtotal();
        }

        // STEP 2: Now that every item is confirmed valid, deduct stock for each one.
        for (OrderItem item : finalItems) {
            productDAO.reduceStock(item.getProductId(), item.getQuantity());
        }

        // STEP 3: Build and insert the order document.
        Order order = new Order();
        order.setOrderId(orderId);
        order.setCustomerName(customerName);
        order.setCustomerEmail(customerEmail);
        order.setCustomerPhone(customerPhone);
        order.setShippingAddress(shippingAddress);
        order.setItems(finalItems);
        order.setTotalAmount(totalAmount);
        order.setOrderStatus("Pending");
        order.setOrderDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        Document doc = order.toDocument();
        collection.insertOne(doc);
        return doc.getObjectId("_id").toString();
    }

    /* =====================================================
       READ (ALL)  ->  find(), newest first
       ===================================================== */
    public List<Order> getAllOrders() {
        List<Order> orders = new ArrayList<>();
        try (MongoCursor<Document> cursor = collection.find().sort(Sorts.descending("orderDate")).iterator()) {
            while (cursor.hasNext()) {
                orders.add(Order.fromDocument(cursor.next()));
            }
        }
        return orders;
    }

    /* =====================================================
       READ (ONE)
       ===================================================== */
    public Order getOrderById(String id) {
        Document doc = collection.find(Filters.eq("_id", new ObjectId(id))).first();
        return doc != null ? Order.fromDocument(doc) : null;
    }

    /* =====================================================
       UPDATE STATUS
       Special business rule: if the order is being cancelled
       (and wasn't already cancelled), restore the stock that
       was deducted when the order was originally placed.
       ===================================================== */
    public boolean updateOrderStatus(String id, String newStatus) {
        Order existing = getOrderById(id);
        if (existing == null) return false;

        boolean isBecomingCancelled = "Cancelled".equalsIgnoreCase(newStatus)
                && !"Cancelled".equalsIgnoreCase(existing.getOrderStatus());

        if (isBecomingCancelled) {
            for (OrderItem item : existing.getItems()) {
                productDAO.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        collection.updateOne(
                Filters.eq("_id", new ObjectId(id)),
                Updates.set("orderStatus", newStatus)
        );
        return true;
    }

    /* =====================================================
       DELETE
       ===================================================== */
    public boolean deleteOrder(String id) {
        DeleteResult result = collection.deleteOne(Filters.eq("_id", new ObjectId(id)));
        return result.getDeletedCount() > 0;
    }

    /* =====================================================
       SEARCH / FILTER
       ===================================================== */
    public List<Order> searchOrders(String keyword, String status) {
        List<Bson> conditions = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            Bson idMatch = Filters.regex("orderId", keyword, "i");
            Bson nameMatch = Filters.regex("customerName", keyword, "i");
            conditions.add(Filters.or(idMatch, nameMatch));
        }
        if (status != null && !status.isBlank()) {
            conditions.add(Filters.eq("orderStatus", status));
        }

        Bson finalFilter = conditions.isEmpty() ? new Document() : Filters.and(conditions);

        List<Order> results = new ArrayList<>();
        try (MongoCursor<Document> cursor = collection.find(finalFilter).sort(Sorts.descending("orderDate")).iterator()) {
            while (cursor.hasNext()) {
                results.add(Order.fromDocument(cursor.next()));
            }
        }
        return results;
    }

    /* =====================================================
       AGGREGATION: DASHBOARD STATS
       Pipeline stages: $match, $group
       ===================================================== */
    public Document getOrderStats() {
        List<Bson> pipeline = Arrays.asList(
                // Exclude cancelled orders from revenue calculations — only "real" sales count.
                new Document("$match", new Document("orderStatus", new Document("$ne", "Cancelled"))),
                new Document("$group", new Document("_id", null)
                        .append("totalRevenue", new Document("$sum", "$totalAmount"))
                        .append("averageOrderValue", new Document("$avg", "$totalAmount"))
                )
        );
        Document revenueResult = collection.aggregate(pipeline).first();

        long totalOrders = collection.countDocuments();
        long pendingOrders = collection.countDocuments(Filters.eq("orderStatus", "Pending"));

        Document stats = new Document();
        stats.append("totalOrders", totalOrders);
        stats.append("pendingOrders", pendingOrders);
        if (revenueResult != null) {
            Double revenue = revenueResult.getDouble("totalRevenue");
            Double avgValue = revenueResult.getDouble("averageOrderValue");
            stats.append("totalRevenue", revenue != null ? revenue : 0.0);
            stats.append("averageOrderValue", avgValue != null ? avgValue : 0.0);
        } else {
            stats.append("totalRevenue", 0.0).append("averageOrderValue", 0.0);
        }
        return stats;
    }

    /* =====================================================
       AGGREGATION: SALES BY CATEGORY  (the $lookup showcase!)
       Pipeline stages: $unwind, $lookup, $unwind, $group, $sort
       ===================================================== */
    /**
     * This report answers: "How much revenue did each PRODUCT
     * CATEGORY generate?" — but our order items only store
     * productId/productName/quantity, NOT category. So we must
     * JOIN each order item back to the "products" collection to
     * find out its category. That join is exactly what $lookup does.
     */
    public List<Document> getSalesByCategoryReport() {
        List<Bson> pipeline = Arrays.asList(

                // Exclude cancelled orders from the sales report.
                new Document("$match", new Document("orderStatus", new Document("$ne", "Cancelled"))),

                // STEP 1: $unwind turns each order's "items" ARRAY into separate
                // documents — one per item — so we can process them individually.
                // Example: an order with 3 items becomes 3 separate documents.
                new Document("$unwind", "$items"),

                // STEP 2: $lookup performs a LEFT JOIN against the "products" collection,
                // matching "items.productId" (from orders) with "productId" (in products).
                // The matched product document(s) are placed into a new array field "productInfo".
                new Document("$lookup", new Document("from", "products")
                        .append("localField", "items.productId")
                        .append("foreignField", "productId")
                        .append("as", "productInfo")),

                // STEP 3: $lookup always returns an ARRAY (even if only one match),
                // so we $unwind it too, turning "productInfo": [ {...} ] into "productInfo": {...}
                new Document("$unwind", "$productInfo"),

                // STEP 4: Now that each item document has its matched product's category
                // attached, group everything by category and sum up quantity and revenue.
                new Document("$group", new Document("_id", "$productInfo.category")
                        .append("unitsSold", new Document("$sum", "$items.quantity"))
                        .append("revenue", new Document("$sum", "$items.subtotal"))
                ),

                // STEP 5: Highest revenue category first.
                new Document("$sort", new Document("revenue", -1))
        );

        List<Document> rawResults = new ArrayList<>();
        collection.aggregate(pipeline).into(rawResults);

        // Rename "_id" to "category" so the frontend receives a clearer field name.
        List<Document> formatted = new ArrayList<>();
        for (Document doc : rawResults) {
            formatted.add(new Document("category", doc.getString("_id"))
                    .append("unitsSold", doc.getInteger("unitsSold", 0))
                    .append("revenue", doc.getDouble("revenue") != null ? doc.getDouble("revenue") : 0.0));
        }
        return formatted;
    }

    /* =====================================================
       AGGREGATION: TOP SELLING PRODUCTS
       Pipeline stages: $unwind, $group, $sort, $limit
       ===================================================== */
    public List<Document> getTopSellingProducts(int limit) {
        List<Bson> pipeline = Arrays.asList(
                new Document("$match", new Document("orderStatus", new Document("$ne", "Cancelled"))),
                new Document("$unwind", "$items"),
                new Document("$group", new Document("_id", "$items.productName")
                        .append("unitsSold", new Document("$sum", "$items.quantity"))
                        .append("revenue", new Document("$sum", "$items.subtotal"))
                ),
                new Document("$sort", new Document("revenue", -1)),
                new Document("$limit", limit)
        );

        List<Document> rawResults = new ArrayList<>();
        collection.aggregate(pipeline).into(rawResults);

        List<Document> formatted = new ArrayList<>();
        for (Document doc : rawResults) {
            formatted.add(new Document("productName", doc.getString("_id"))
                    .append("unitsSold", doc.getInteger("unitsSold", 0))
                    .append("revenue", doc.getDouble("revenue") != null ? doc.getDouble("revenue") : 0.0));
        }
        return formatted;
    }
}
