package service.iarooms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import util.PropertiesLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class EspritScrapeClient {

    private static final String CONFIG_RESOURCE = "confg/iarooms.properties";
    private static final String DEFAULT_BACKEND_URL = "http://127.0.0.1:8001";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String configuredBackendUrl() {
        try {
            Properties properties = PropertiesLoader.load(CONFIG_RESOURCE);
            String configured = properties.getProperty("iarooms.backend.url");
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
        } catch (RuntimeException ignored) {
            // Keep the Java screen usable even when the optional IArooms config is missing.
        }
        return DEFAULT_BACKEND_URL;
    }

    public ScrapePayload scrape(String backendUrl, String studentId, String password, double timeoutSeconds) {
        String baseUrl = normalizeBackendUrl(backendUrl);
        int requestTimeout = Math.max(5, (int) Math.ceil(timeoutSeconds));

        try {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(requestTimeout + 5L))
                    .build();

            ensureBackendReachable(client, baseUrl);

            ObjectNode requestPayload = objectMapper.createObjectNode();
            putNullable(requestPayload, "student_id", studentId);
            putNullable(requestPayload, "password", password);
            requestPayload.putNull("captcha");
            requestPayload.put("timeout_seconds", timeoutSeconds);

            HttpRequest scrapeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/scrape/esprit"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(requestTimeout + 10L))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestPayload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> scrapeResponse = client.send(scrapeRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode scrapeJson = readJson(scrapeResponse.body());
            if (scrapeResponse.statusCode() >= 400) {
                throw new IllegalStateException("Esprit scraping backend returned an error: " + extractErrorDetail(scrapeJson));
            }

            HttpRequest bookingsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/bookings"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(requestTimeout + 10L))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> bookingsResponse = client.send(bookingsRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode bookingsJson = readJson(bookingsResponse.body());
            if (bookingsResponse.statusCode() >= 400) {
                throw new IllegalStateException("Failed to retrieve scraped bookings: " + extractErrorDetail(bookingsJson));
            }

            List<BookingPayload> bookings = objectMapper
                    .readerForListOf(BookingPayload.class)
                    .readValue(bookingsJson.path("bookings"));

            return new ScrapePayload(
                    intValue(scrapeJson, "total_pages_read"),
                    intValue(scrapeJson, "total_bookings_extracted"),
                    intValue(scrapeJson, "total_physical_rooms_found"),
                    intValue(scrapeJson, "ignored_online_sessions"),
                    stringValue(scrapeJson, "source_url"),
                    stringList(scrapeJson.path("warnings")),
                    bookings);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Esprit scrape was interrupted.", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("Failed to trigger the Esprit scraping backend at " + baseUrl
                    + ": " + describeException(exception), exception);
        }
    }

    private void ensureBackendReachable(HttpClient client, String baseUrl) {
        try {
            HttpRequest healthRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
            if (healthResponse.statusCode() >= 400) {
                throw new IllegalStateException("IArooms Python backend at " + baseUrl
                        + " answered with HTTP " + healthResponse.statusCode() + ". " + backendStartHint());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Backend health check was interrupted.", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot reach IArooms Python backend at " + baseUrl
                    + ". " + backendStartHint() + " Cause: " + describeException(exception), exception);
        }
    }

    private void putNullable(ObjectNode node, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value.trim());
        }
    }

    private String normalizeBackendUrl(String backendUrl) {
        String normalized = backendUrl == null || backendUrl.isBlank() ? configuredBackendUrl() : backendUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private JsonNode readJson(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String backendStartHint() {
        return "Start it with: Set-Location -LiteralPath \"" + System.getProperty("user.dir")
                + "\\IArooms\"; python -m uvicorn backend.main:app --host 127.0.0.1 --port 8001";
    }

    private String describeException(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private String extractErrorDetail(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "Unknown backend error";
        }
        JsonNode detail = node.path("detail");
        if (!detail.isMissingNode() && !detail.isNull()) {
            return detail.isTextual() ? detail.asText() : detail.toString();
        }
        return node.toString();
    }

    private int intValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? value.asInt() : 0;
    }

    private String stringValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() ? value.asText() : "";
    }

    private List<String> stringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                values.add(item.asText());
            }
        }
        return values;
    }

    public record ScrapePayload(
            int totalPagesRead,
            int totalBookingsExtracted,
            int totalPhysicalRoomsFound,
            int ignoredOnlineSessions,
            String sourceUrl,
            List<String> warnings,
            List<BookingPayload> bookings
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingPayload(
            @JsonProperty("group_name") String groupName,
            @JsonProperty("course_name") String courseName,
            @JsonProperty("room_name") String roomName,
            @JsonProperty("date") String date,
            @JsonProperty("day_name") String dayName,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            @JsonProperty("source_page") int sourcePage
    ) {
    }
}
