import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ProductDAO.java  (DAO = Data Access Object)
 * -----------------------------------------------------
 * All database operations for the "products" collection:
 * Create, Read, Update, Delete, Search/Filter, and Aggregation
 * (stock value, low-stock alerts, category counts).
 *
 * Also used internally by OrderDAO when placing orders
 * (to check/deduct stock) — this is why reduceStock() and
 * restoreStock() exist here rather than in OrderDAO.
 * -----------------------------------------------------
 */
public class ProductDAO {

    private final MongoCollection<Document> collection;

    public ProductDAO() {
        MongoDatabase db = MongoConnection.getDatabase();
        this.collection = db.getCollection("products");
    }

    /* =====================================================
       CREATE  ->  insertOne()
       ===================================================== */
    public String insertProduct(Product product) {
        Document doc = product.toDocument();
        collection.insertOne(doc);
        return doc.getObjectId("_id").toString();
    }

    /* =====================================================
       READ (ALL)  ->  find()
       ===================================================== */
    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        try (MongoCursor<Document> cursor = collection.find().sort(Sorts.ascending("name")).iterator()) {
            while (cursor.hasNext()) {
                products.add(Product.fromDocument(cursor.next()));
            }
        }
        return products;
    }

    /* =====================================================
       READ (ONE) by MongoDB _id
       ===================================================== */
    public Product getProductById(String id) {
        Document doc = collection.find(Filters.eq("_id", new ObjectId(id))).first();
        return doc != null ? Product.fromDocument(doc) : null;
    }

    /* =====================================================
       READ (ONE) by custom productId (e.g. "PRD001")
       Used internally when creating an order.
       ===================================================== */
    public Product getProductByProductId(String productId) {
        Document doc = collection.find(Filters.eq("productId", productId)).first();
        return doc != null ? Product.fromDocument(doc) : null;
    }

    /* =====================================================
       UPDATE  ->  updateOne()
       ===================================================== */
    public boolean updateProduct(String id, Product product) {
        Document updateOperation = new Document("$set", product.toDocument());
        UpdateResult result = collection.updateOne(Filters.eq("_id", new ObjectId(id)), updateOperation);
        return result.getModifiedCount() > 0;
    }

    /**
     * Reduces a product's stock by the given quantity (used when an order is placed).
     * Uses MongoDB's $inc operator to atomically decrement the field.
     */
    public void reduceStock(String productId, int quantityToReduce) {
        collection.updateOne(
                Filters.eq("productId", productId),
                Updates.inc("stockQuantity", -quantityToReduce) // $inc with a negative number = subtraction
        );
    }

    /**
     * Restores (adds back) stock — used when an order is cancelled.
     */
    public void restoreStock(String productId, int quantityToRestore) {
        collection.updateOne(
                Filters.eq("productId", productId),
                Updates.inc("stockQuantity", quantityToRestore)
        );
    }

    /* =====================================================
       DELETE  ->  deleteOne()
       ===================================================== */
    public boolean deleteProduct(String id) {
        DeleteResult result = collection.deleteOne(Filters.eq("_id", new ObjectId(id)));
        return result.getDeletedCount() > 0;
    }

    /* =====================================================
       SEARCH / FILTER  ->  find() with $regex / $eq / $lte / $and / $or
       ===================================================== */
    public List<Product> searchProducts(String keyword, String category, String stockStatus) {
        List<Bson> conditions = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            Bson nameMatch = Filters.regex("name", keyword, "i");
            Bson idMatch = Filters.regex("productId", keyword, "i");
            Bson categoryMatch = Filters.regex("category", keyword, "i");
            conditions.add(Filters.or(nameMatch, idMatch, categoryMatch)); // $or
        }

        if (category != null && !category.isBlank()) {
            conditions.add(Filters.eq("category", category)); // $eq
        }

        // "low" -> stockQuantity > 0 AND stockQuantity <= reorderLevel (uses $expr to compare two fields)
        // "out" -> stockQuantity <= 0
        if ("low".equalsIgnoreCase(stockStatus)) {
            conditions.add(Filters.expr(new Document("$and", Arrays.asList(
                    new Document("$gt", Arrays.asList("$stockQuantity", 0)),
                    new Document("$lte", Arrays.asList("$stockQuantity", "$reorderLevel"))
            ))));
        } else if ("out".equalsIgnoreCase(stockStatus)) {
            conditions.add(Filters.lte("stockQuantity", 0)); // $lte
        }

        Bson finalFilter = conditions.isEmpty() ? new Document() : Filters.and(conditions); // $and

        List<Product> results = new ArrayList<>();
        try (MongoCursor<Document> cursor = collection.find(finalFilter).sort(Sorts.ascending("name")).iterator()) {
            while (cursor.hasNext()) {
                results.add(Product.fromDocument(cursor.next()));
            }
        }
        return results;
    }

    /* =====================================================
       AGGREGATION: DASHBOARD STATS
       Pipeline stages used: $match, $group
       ===================================================== */
    public Document getProductStats() {
        // Stage 1: compute overall totals across ALL products.
        List<Bson> overallPipeline = Arrays.asList(
                new Document("$group", new Document("_id", null)
                        .append("totalProducts", new Document("$sum", 1))
                        // $multiply lets us compute price * stockQuantity PER document before summing.
                        .append("totalStockValue", new Document("$sum",
                                new Document("$multiply", Arrays.asList("$price", "$stockQuantity"))))
                        .append("categories", new Document("$addToSet", "$category"))
                )
        );
        Document overallResult = collection.aggregate(overallPipeline).first();

        // Stage 2: count how many products are at/below their reorder level ($match + $expr + $count-like $sum).
        List<Bson> lowStockPipeline = Arrays.asList(
                new Document("$match", new Document("$expr", new Document("$lte",
                        Arrays.asList("$stockQuantity", "$reorderLevel")))),
                new Document("$count", "lowStockCount")
        );
        Document lowStockResult = collection.aggregate(lowStockPipeline).first();

        Document stats = new Document();
        if (overallResult != null) {
            stats.append("totalProducts", overallResult.getInteger("totalProducts", 0));
            Double stockValue = overallResult.getDouble("totalStockValue");
            stats.append("totalStockValue", stockValue != null ? stockValue : 0.0);
            List<?> categories = (List<?>) overallResult.get("categories");
            stats.append("categoryCount", categories != null ? categories.size() : 0);
        } else {
            stats.append("totalProducts", 0).append("totalStockValue", 0.0).append("categoryCount", 0);
        }
        stats.append("lowStockCount", lowStockResult != null ? lowStockResult.getInteger("lowStockCount", 0) : 0);

        return stats;
    }
}
