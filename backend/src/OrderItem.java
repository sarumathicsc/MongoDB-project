import org.bson.Document;

/**
 * OrderItem.java
 * -----------------------------------------------------
 * Represents ONE line item inside an order (one product +
 * the quantity purchased). An Order contains a LIST of these.
 *
 * Note: productName and unitPrice are stored as a SNAPSHOT
 * at the time the order was placed. This is standard
 * e-commerce practice — if the product's price changes later,
 * old orders should still show the price the customer actually
 * paid, not today's price.
 * -----------------------------------------------------
 */
public class OrderItem {

    private String productId;
    private String productName;
    private int quantity;
    private double unitPrice;
    private double subtotal; // quantity * unitPrice, calculated when the order is created

    public OrderItem() { }

    public OrderItem(String productId, String productName, int quantity, double unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = quantity * unitPrice;
    }

    // ---- Getters and Setters ----
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    /** Converts this OrderItem into a MongoDB Document (used as an entry inside the "items" array). */
    public Document toDocument() {
        return new Document()
                .append("productId", productId)
                .append("productName", productName)
                .append("quantity", quantity)
                .append("unitPrice", unitPrice)
                .append("subtotal", subtotal);
    }

    /** Builds an OrderItem FROM a MongoDB Document (one entry read back from the "items" array). */
    public static OrderItem fromDocument(Document doc) {
        OrderItem item = new OrderItem();
        item.setProductId(doc.getString("productId"));
        item.setProductName(doc.getString("productName"));

        Object qtyObj = doc.get("quantity");
        item.setQuantity(qtyObj != null ? ((Number) qtyObj).intValue() : 0);

        Object priceObj = doc.get("unitPrice");
        item.setUnitPrice(priceObj != null ? ((Number) priceObj).doubleValue() : 0.0);

        Object subtotalObj = doc.get("subtotal");
        item.setSubtotal(subtotalObj != null ? ((Number) subtotalObj).doubleValue() : 0.0);

        return item;
    }
}
