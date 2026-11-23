package ua.edu.nupp.kpp.waste.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** The JavaFX client reaches the data solely through this HTTP/JSON boundary. */
public final class ApiClient {
    private static final Logger LOGGER = Logger.getLogger(ApiClient.class.getName());
    private static final String DEFAULT_URL = "http://127.0.0.1:8087/waste-collection-server/api";
    private final String root;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ApiClient() {
        this(System.getProperty("api.baseUrl", System.getenv().getOrDefault("API_BASE_URL", DEFAULT_URL)));
    }

    public ApiClient(String root) {
        URI uri = URI.create(root.strip());
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Вкажіть коректну адресу HTTP API.");
        }
        this.root = uri.toString().replaceAll("/+$", "");
    }

    public <T> List<T> list(String resource, Class<T> type) {
        String reply = send("GET", resource, null);
        try {
            return json.readValue(reply, json.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new ApiException(0, "Сервер повернув дані в неочікуваному форматі.", e);
        }
    }

    public void save(String resource, Long id, Object payload) {
        send(id == null ? "POST" : "PUT", id == null ? resource : resource + "/" + id, payload);
    }

    public void delete(String resource, long id) {
        send("DELETE", resource + "/" + id, null);
    }

    public void advance(long id, Models.RequestStatus status) {
        send("PATCH", "requests/" + id + "/status", Map.of("status", status.name()));
    }

    private String send(String method, String path, Object payload) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(root + "/" + path))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (payload == null) req.method(method, HttpRequest.BodyPublishers.noBody());
            else req.header("Content-Type", "application/json; charset=UTF-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                String detail = "Сервер повернув помилку HTTP " + res.statusCode() + ".";
                try {
                    var body = json.readTree(res.body());
                    if (body != null && body.hasNonNull("message") && body.get("message").isTextual()) {
                        detail = body.get("message").asText();
                    }
                } catch (IOException ignored) {
                    // Keep the safe HTTP status message when no JSON error is available.
                }
                throw new ApiException(res.statusCode(), detail, null);
            }
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Мережеву операцію перервано.", e);
        } catch (IOException e) {
            LOGGER.warning(() -> "HTTP " + method + " failed: " + e.getClass().getSimpleName());
            throw new ApiException(0, "Немає зв’язку із сервером. Перевірте запуск сервера й повторіть спробу.", e);
        }
    }

    public static final class ApiException extends RuntimeException {
        private final int status;
        ApiException(int status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }
        public int status() { return status; }
    }
}
