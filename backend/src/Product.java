import org.bson.Document;

/**
 * Product.java
 * -----------------------------------------------------
 * Model (POJO) class representing ONE product document in
 * the MongoDB "products" collection.
 *
 * Same pattern as Student.java from the Student Management
 * System project: private fields + getters/setters, plus
 * toDocument() / fromDocument() to convert between this Java
 * object and MongoDB's Document format.
 * -----------------------------------------------------
 */
public class Product {

    private String id;           // MongoDB's auto-generated _id, as a String
    private String productId;    // custom ID like "PRD001"
    private String name;
    private String category;
    private double price;
    private int stockQuantity;
    private int reorderLevel;    // stock threshold that triggers a "Low Stock" alert
    private String supplier;
    private String description;

    public Product() { }

    public Product(String productId, String name, String category, double price,
                    int stockQuantity, int reorderLevel, String supplier, String description) {
        this.productId = productId;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.reorderLevel = reorderLevel;
        this.supplier = supplier;
        this.description = description;
    }

    // ---- Getters and Setters ----
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public int getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Converts this Product into a MongoDB Document for insert/update operations. */
    public Document toDocument() {
        return new Document()
                .append("productId", productId)
                .append("name", name)
                .append("category", category)
                .append("price", price)
                .append("stockQuantity", stockQuantity)
                .append("reorderLevel", reorderLevel)
                .append("supplier", supplier)
                .append("description", description);
    }

    /** Builds a Product object FROM a MongoDB Document (used when reading query results). */
    public static Product fromDocument(Document doc) {
        Product p = new Product();
        p.setId(doc.getObjectId("_id").toString());
        p.setProductId(doc.getString("productId"));
        p.setName(doc.getString("name"));
        p.setCategory(doc.getString("category"));

        Object priceObj = doc.get("price");
        p.setPrice(priceObj != null ? ((Number) priceObj).doubleValue() : 0.0);

        Object stockObj = doc.get("stockQuantity");
        p.setStockQuantity(stockObj != null ? ((Number) stockObj).intValue() : 0);

        Object reorderObj = doc.get("reorderLevel");
        p.setReorderLevel(reorderObj != null ? ((Number) reorderObj).intValue() : 0);

        p.setSupplier(doc.getString("supplier"));
        p.setDescription(doc.getString("description"));
        return p;
    }
}
