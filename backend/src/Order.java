import org.bson.Document;
import java.util.ArrayList;
import java.util.List;

/**
 * Order.java
 * -----------------------------------------------------
 * Model (POJO) class representing ONE order document in the
 * MongoDB "orders" collection. Each order contains a LIST of
 * OrderItem objects (the products purchased in that order) —
 * this is called "embedding" in MongoDB, where related data is
 * nested directly inside the parent document instead of being
 * stored in a separate table with a foreign key (as SQL would).
 * -----------------------------------------------------
 */
public class Order {

    private String id;                 // MongoDB's auto-generated _id
    private String orderId;            // custom ID like "ORD1001"
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String shippingAddress;
    private List<OrderItem> items;
    private double totalAmount;
    private String orderStatus;        // "Pending" | "Shipped" | "Delivered" | "Cancelled"
    private String orderDate;          // stored as a String in ISO format, e.g. "2026-07-15 10:30:00"

    public Order() {
        this.items = new ArrayList<>();
    }

    // ---- Getters and Setters ----
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    /** Converts this Order into a MongoDB Document, including its nested list of items. */
    public Document toDocument() {
        List<Document> itemDocs = new ArrayList<>();
        for (OrderItem item : items) {
            itemDocs.add(item.toDocument());
        }

        return new Document()
                .append("orderId", orderId)
                .append("customerName", customerName)
                .append("customerEmail", customerEmail)
                .append("customerPhone", customerPhone)
                .append("shippingAddress", shippingAddress)
                .append("items", itemDocs)
                .append("totalAmount", totalAmount)
                .append("orderStatus", orderStatus)
                .append("orderDate", orderDate);
    }

    /** Builds an Order object FROM a MongoDB Document, including converting each nested item. */
    @SuppressWarnings("unchecked")
    public static Order fromDocument(Document doc) {
        Order order = new Order();
        order.setId(doc.getObjectId("_id").toString());
        order.setOrderId(doc.getString("orderId"));
        order.setCustomerName(doc.getString("customerName"));
        order.setCustomerEmail(doc.getString("customerEmail"));
        order.setCustomerPhone(doc.getString("customerPhone"));
        order.setShippingAddress(doc.getString("shippingAddress"));

        Object totalObj = doc.get("totalAmount");
        order.setTotalAmount(totalObj != null ? ((Number) totalObj).doubleValue() : 0.0);

        order.setOrderStatus(doc.getString("orderStatus"));
        order.setOrderDate(doc.getString("orderDate"));

        List<Document> itemDocs = (List<Document>) doc.get("items");
        List<OrderItem> items = new ArrayList<>();
        if (itemDocs != null) {
            for (Document itemDoc : itemDocs) {
                items.add(OrderItem.fromDocument(itemDoc));
            }
        }
        order.setItems(items);

        return order;
    }
}
