import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.IOException;

/**
 * Main.java
 * -----------------------------------------------------
 * Entry point for the E-Commerce Inventory & Order Management
 * backend. Starts a lightweight HTTP server on port 8080 and
 * registers TWO handlers — one for products, one for orders —
 * each responsible for its own collection.
 * -----------------------------------------------------
 */
public class Main {

    public static void main(String[] args) throws IOException {

        int port = 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Requests to "/api/products..." go to ProductHandler.
        server.createContext("/api/products", new ProductHandler());

        // Requests to "/api/orders..." go to OrderHandler.
        server.createContext("/api/orders", new OrderHandler());

        server.setExecutor(null);

        // Fail fast if MongoDB isn't reachable.
        MongoConnection.getDatabase();

        server.start();

        System.out.println("=================================================");
        System.out.println(" InvenTrack - E-Commerce Inventory Backend Server");
        System.out.println(" Running at: http://localhost:" + port);
        System.out.println(" Products API: http://localhost:" + port + "/api/products");
        System.out.println(" Orders API:   http://localhost:" + port + "/api/orders");
        System.out.println(" Press Ctrl+C to stop the server.");
        System.out.println("=================================================");
    }
}
