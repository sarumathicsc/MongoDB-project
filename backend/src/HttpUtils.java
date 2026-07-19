import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HttpUtils.java
 * -----------------------------------------------------
 * Small helper methods shared by BOTH ProductHandler and
 * OrderHandler, so we don't repeat the same request-parsing
 * and response-writing code in two places.
 *
 * This is the "DRY" principle in action — Don't Repeat Yourself.
 * -----------------------------------------------------
 */
public class HttpUtils {

    /** Extracts the last path segment after a known prefix, e.g. "/api/products/64f1..." -> "64f1..." */
    public static String extractId(String path, String prefix) {
        return path.substring(prefix.length());
    }

    /** Parses "?query=abc&status=xyz" into a simple key-value map. */
    public static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        if (query == null) return params;

        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    /** Reads the raw request body and parses it as a JSONObject. */
    public static JSONObject readRequestBodyAsJson(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        String bodyText = buffer.toString(StandardCharsets.UTF_8);
        return new JSONObject(bodyText.isBlank() ? "{}" : bodyText);
    }

    public static String errorJson(String message) {
        JSONObject json = new JSONObject();
        json.put("error", message);
        return json.toString();
    }

    public static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    /** Sends a JSON response with the given HTTP status code. */
    public static void sendResponse(HttpExchange exchange, int statusCode, String jsonBody) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
