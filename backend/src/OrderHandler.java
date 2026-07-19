import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OrderHandler.java
 * -----------------------------------------------------
 * Controller for every request under "/api/orders".
 *
 * Routes:
 *   GET    /api/orders                                  -> list all orders (newest first)
 *   GET    /api/orders/search?query=&status=              -> search/filter orders
 *   GET    /api/orders/stats                              -> aggregation dashboard stats
 *   GET    /api/orders/reports/sales-by-category           -> $lookup aggregation report
 *   GET    /api/orders/reports/top-products?limit=5        -> top-selling products report
 *   GET    /api/orders/{id}                                -> get one order
 *   POST   /api/orders                                     -> place a new order
 *   PUT    /api/orders/{id}/status                          -> update order status
 *   DELETE /api/orders/{id}                                -> delete an order
 * -----------------------------------------------------
 */
public class OrderHandler implements HttpHandler {

    private final OrderDAO orderDAO = new OrderDAO();

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

        if (path.equals("/api/orders")) {
            List<Order> orders = orderDAO.getAllOrders();
            HttpUtils.sendResponse(exchange, 200, ordersToJsonArray(orders).toString());

        } else if (path.equals("/api/orders/search")) {
            Map<String, String> params = HttpUtils.parseQueryParams(exchange.getRequestURI());
            List<Order> results = orderDAO.searchOrders(params.get("query"), params.get("status"));
            HttpUtils.sendResponse(exchange, 200, ordersToJsonArray(results).toString());

        } else if (path.equals("/api/orders/stats")) {
            Document stats = orderDAO.getOrderStats();
            HttpUtils.sendResponse(exchange, 200, stats.toJson());

        } else if (path.equals("/api/orders/reports/sales-by-category")) {
            List<Document> report = orderDAO.getSalesByCategoryReport();
            HttpUtils.sendResponse(exchange, 200, documentsToJsonArray(report).toString());

        } else if (path.equals("/api/orders/reports/top-products")) {
            Map<String, String> params = HttpUtils.parseQueryParams(exchange.getRequestURI());
            int limit = 5;
            if (params.containsKey("limit")) {
                try { limit = Integer.parseInt(params.get("limit")); } catch (NumberFormatException ignored) { }
            }
            List<Document> report = orderDAO.getTopSellingProducts(limit);
            HttpUtils.sendResponse(exchange, 200, documentsToJsonArray(report).toString());

        } else if (path.startsWith("/api/orders/")) {
            String id = HttpUtils.extractId(path, "/api/orders/");
            Order order = orderDAO.getOrderById(id);
            if (order == null) {
                HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Order not found"));
            } else {
                HttpUtils.sendResponse(exchange, 200, orderToJson(order).toString());
            }
        } else {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
        }
    }

    private void handlePost(HttpExchange exchange, String path) throws IOException {
        if (!path.equals("/api/orders")) {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
            return;
        }

        JSONObject body = HttpUtils.readRequestBodyAsJson(exchange);

        String customerName = body.optString("customerName", "").trim();
        String customerEmail = body.optString("customerEmail", "").trim();
        String customerPhone = body.optString("customerPhone", "").trim();
        String shippingAddress = body.optString("shippingAddress", "").trim();

        // Basic server-side validation of customer fields.
        if (customerName.isEmpty() || customerEmail.isEmpty() || customerPhone.isEmpty() || shippingAddress.isEmpty()) {
            HttpUtils.sendResponse(exchange, 400, HttpUtils.errorJson("All customer fields are required"));
            return;
        }

        // Parse the requested items (only productId + quantity come from the client;
        // OrderDAO.createOrder() fills in the rest using live product data).
        List<OrderItem> requestedItems = new ArrayList<>();
        JSONArray itemsArray = body.optJSONArray("items");
        if (itemsArray != null) {
            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject itemJson = itemsArray.getJSONObject(i);
                OrderItem item = new OrderItem();
                item.setProductId(itemJson.optString("productId", ""));
                item.setQuantity(itemJson.optInt("quantity", 0));
                requestedItems.add(item);
            }
        }

        // Auto-generate a unique, human-readable order ID using the current timestamp.
        String orderId = "ORD" + System.currentTimeMillis();

        try {
            String newId = orderDAO.createOrder(orderId, customerName, customerEmail, customerPhone,
                    shippingAddress, requestedItems);

            JSONObject response = new JSONObject();
            response.put("message", "Order placed successfully");
            response.put("id", newId);
            response.put("orderId", orderId);
            HttpUtils.sendResponse(exchange, 201, response.toString());

        } catch (OrderException e) {
            // OrderException means a predictable business-rule failure
            // (out of stock, invalid product) — safe to show directly to the user.
            HttpUtils.sendResponse(exchange, 400, HttpUtils.errorJson(e.getMessage()));
        }
    }

    private void handlePut(HttpExchange exchange, String path) throws IOException {
        // Expected format: /api/orders/{id}/status
        if (!path.startsWith("/api/orders/") || !path.endsWith("/status")) {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
            return;
        }

        String remainder = path.substring("/api/orders/".length()); // "{id}/status"
        String id = remainder.substring(0, remainder.length() - "/status".length());

        JSONObject body = HttpUtils.readRequestBodyAsJson(exchange);
        String newStatus = body.optString("orderStatus", "").trim();

        if (!newStatus.equals("Pending") && !newStatus.equals("Shipped")
                && !newStatus.equals("Delivered") && !newStatus.equals("Cancelled")) {
            HttpUtils.sendResponse(exchange, 400, HttpUtils.errorJson(
                    "orderStatus must be one of: Pending, Shipped, Delivered, Cancelled"));
            return;
        }

        boolean updated = orderDAO.updateOrderStatus(id, newStatus);
        if (updated) {
            HttpUtils.sendResponse(exchange, 200, "{\"message\":\"Order status updated successfully\"}");
        } else {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Order not found"));
        }
    }

    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        if (!path.startsWith("/api/orders/")) {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Route not found"));
            return;
        }

        String id = HttpUtils.extractId(path, "/api/orders/");
        boolean deleted = orderDAO.deleteOrder(id);
        if (deleted) {
            HttpUtils.sendResponse(exchange, 200, "{\"message\":\"Order deleted successfully\"}");
        } else {
            HttpUtils.sendResponse(exchange, 404, HttpUtils.errorJson("Order not found"));
        }
    }

    /* ---- JSON conversion helpers ---- */

    private JSONObject orderToJson(Order o) {
        JSONObject json = new JSONObject();
        json.put("_id", o.getId());
        json.put("orderId", o.getOrderId());
        json.put("customerName", o.getCustomerName());
        json.put("customerEmail", o.getCustomerEmail());
        json.put("customerPhone", o.getCustomerPhone());
        json.put("shippingAddress", o.getShippingAddress());
        json.put("totalAmount", o.getTotalAmount());
        json.put("orderStatus", o.getOrderStatus());
        json.put("orderDate", o.getOrderDate());

        JSONArray itemsArray = new JSONArray();
        for (OrderItem item : o.getItems()) {
            JSONObject itemJson = new JSONObject();
            itemJson.put("productId", item.getProductId());
            itemJson.put("productName", item.getProductName());
            itemJson.put("quantity", item.getQuantity());
            itemJson.put("unitPrice", item.getUnitPrice());
            itemJson.put("subtotal", item.getSubtotal());
            itemsArray.put(itemJson);
        }
        json.put("items", itemsArray);

        return json;
    }

    private JSONArray ordersToJsonArray(List<Order> orders) {
        JSONArray array = new JSONArray();
        for (Order o : orders) array.put(orderToJson(o));
        return array;
    }

    private JSONArray documentsToJsonArray(List<Document> docs) {
        JSONArray array = new JSONArray();
        for (Document d : docs) array.put(new JSONObject(d.toJson()));
        return array;
    }
}
