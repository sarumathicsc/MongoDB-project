import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * ProductHandler.java
 * -----------------------------------------------------
 * Controller for every request under "/api/products".
 * Same overall pattern as StudentHandler.java from the Student
 * Management System project: read the HTTP method + path,
 * route to the right ProductDAO method, return JSON.
 *
 * Routes:
 *   GET    /api/products                                -> list all products
 *   GET    /api/products/search?query=&category=&stockStatus= -> search/filter
 *   GET    /api/products/stats                           -> aggregation dashboard stats
 *   GET    /api/products/{id}                            -> get one product
 *   POST   /api/products                                 -> create a new product
 *   PUT    /api/products/{id}                             -> update a product
 *   DELETE /api/products/{id}                             -> delete a product
 * -----------------------------------------------------
 */
public class ProductHandler implements HttpHandler {

    private final ProductDAO productDAO = new ProductDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        HttpUtils.addCorsHeaders(exchange);
        String method = exchange.getRequestMethod();

        if (method.equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            switch (method) {
                case "GET": handleGet(exchange, path); break;
                case "POST": handlePost(exchange, path); break;
                case "PUT": handlePut(exchange, path); break;
                case "DELETE": handleDelete(exchange, path); break;
                default: HttpUtils.sendResponse(exchange, 405, HttpUtils.errorJson("Method not allowed"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtils.sendResponse(exchange, 500, HttpUtils.errorJson("Server error: " + e.getMessage()));
        }
    }

    private void handleGet(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/products")) {
            List<Product> products = productDAO.getAllProducts();
            HttpUtils.sendResponse(exchange, 200, productsToJsonArray(products).toString());

        } else if (path.equals("/api/products/search")) {
            Map<String, String> params = HttpUtils.parseQueryParams(exchange.getRequestURI());
            List<Product> results = productDAO.searchProducts(
                    params.get("query"), params.get("category"), params.get("stockStatus"));
            HttpUtils.sendResponse(exchange, 200, productsToJsonArray(results).toString());

        } else if (path.equals("/api/products/stats")) {
            Document stats = productDAO.getProductStats();
            HttpUtils.sendResponse(exchange, 200, stats.toJson());

        } else if (path.startsWith("/api/products/")) {
            String id = HttpUtils.extractId(path, "/api/products/");
            Product product = productDAO.getProductById(id);
            if (product == null) {
                HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Product not found"));
            } else {
                HttpUtils.sendResponse(exchange, 200, productToJson(product).toString());
            }
        } else {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
        }
    }

    private void handlePost(HttpExchange exchange, String path) throws IOException {
        if (!path.equals("/api/products")) {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
            return;
        }

        JSONObject body = HttpUtils.readRequestBodyAsJson(exchange);
        Product product = jsonToProduct(body);

        String validationError = validateProduct(product);
        if (validationError != null) {
            HttpUtils.sendResponse(exchange, 400, HttpUtils.errorJson(validationError));
            return;
        }

        String newId = productDAO.insertProduct(product);
        JSONObject response = new JSONObject();
        response.put("message", "Product created successfully");
        response.put("id", newId);
        HttpUtils.sendResponse(exchange, 201, response.toString());
    }

    private void handlePut(HttpExchange exchange, String path) throws IOException {
        if (!path.startsWith("/api/products/")) {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
            return;
        }

        String id = HttpUtils.extractId(path, "/api/products/");
        JSONObject body = HttpUtils.readRequestBodyAsJson(exchange);
        Product product = jsonToProduct(body);

        String validationError = validateProduct(product);
        if (validationError != null) {
            HttpUtils.sendResponse(exchange, 400, HttpUtils.errorJson(validationError));
            return;
        }

        boolean updated = productDAO.updateProduct(id, product);
        if (updated) {
            HttpUtils.sendResponse(exchange, 200, "{\"message\":\"Product updated successfully\"}");
        } else {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Product not found or no changes made"));
        }
    }

    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        if (!path.startsWith("/api/products/")) {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
            return;
        }

        String id = HttpUtils.extractId(path, "/api/products/");
        boolean deleted = productDAO.deleteProduct(id);
        if (deleted) {
            HttpUtils.sendResponse(exchange, 200, "{\"message\":\"Product deleted successfully\"}");
        } else {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Product not found"));
        }
    }

    /* ---- JSON conversion helpers ---- */

    private Product jsonToProduct(JSONObject json) {
        Product p = new Product();
        p.setProductId(json.optString("productId", "").trim());
        p.setName(json.optString("name", "").trim());
        p.setCategory(json.optString("category", "").trim());
        p.setPrice(json.optDouble("price", 0.0));
        p.setStockQuantity(json.optInt("stockQuantity", 0));
        p.setReorderLevel(json.optInt("reorderLevel", 0));
        p.setSupplier(json.optString("supplier", "").trim());
        p.setDescription(json.optString("description", "").trim());
        return p;
    }

    private JSONObject productToJson(Product p) {
        JSONObject json = new JSONObject();
        json.put("_id", p.getId());
        json.put("productId", p.getProductId());
        json.put("name", p.getName());
        json.put("category", p.getCategory());
        json.put("price", p.getPrice());
        json.put("stockQuantity", p.getStockQuantity());
        json.put("reorderLevel", p.getReorderLevel());
        json.put("supplier", p.getSupplier());
        json.put("description", p.getDescription());
        return json;
    }

    private JSONArray productsToJsonArray(List<Product> products) {
        JSONArray array = new JSONArray();
        for (Product p : products) array.put(productToJson(p));
        return array;
    }

    private String validateProduct(Product p) {
        if (p.getProductId() == null || p.getProductId().isEmpty()) return "productId is required";
        if (p.getName() == null || p.getName().isEmpty()) return "name is required";
        if (p.getCategory() == null || p.getCategory().isEmpty()) return "category is required";
        if (p.getPrice() < 0) return "price cannot be negative";
        if (p.getStockQuantity() < 0) return "stockQuantity cannot be negative";
        if (p.getReorderLevel() < 0) return "reorderLevel cannot be negative";
        return null;
    }
}
