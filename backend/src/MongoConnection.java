import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * MongoConnection.java
 * -----------------------------------------------------
 * Same Singleton pattern used in the Student Management
 * System project: creates ONE shared connection to MongoDB
 * and reuses it everywhere in this application, instead of
 * opening a new connection for every request.
 * -----------------------------------------------------
 */
public class MongoConnection {

    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "EcommerceInventoryDB";

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    public static MongoDatabase getDatabase() {
        if (mongoClient == null) {
            try {
                mongoClient = MongoClients.create(CONNECTION_STRING);
                database = mongoClient.getDatabase(DATABASE_NAME);
                System.out.println("[MongoConnection] Connected to MongoDB database: " + DATABASE_NAME);
            } catch (Exception e) {
                System.err.println("[MongoConnection] ERROR: Could not connect to MongoDB.");
                System.err.println("Make sure MongoDB Server is running (check services.msc on Windows).");
                throw new RuntimeException("MongoDB connection failed", e);
            }
        }
        return database;
    }

    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("[MongoConnection] Connection closed.");
        }
    }
}
