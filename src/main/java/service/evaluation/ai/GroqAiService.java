package service.evaluation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Groq AI Service for generating academic recommendations and insights.
 * Uses Groq Cloud API with Llama models.
 */
public class GroqAiService {

    private static final String DEFAULT_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    
    // Configured API Key
    private static final String API_KEY = "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";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GroqAiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Checks if the Groq API key is present.
     */
    public boolean isConfigured() {
        return API_KEY != null && !API_KEY.isBlank();
    }

    private String getApiKey() {
        return API_KEY;
    }

    /**
     * Sends a request to the Groq API.
     */
    public String ask(String systemPrompt, String userPrompt, int maxTokens) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "### AI UNAVAILABLE ###\n\n" +
                   "The Groq AI API key is not configured.";
        }

        String apiUrl = envOrDefault("GROQ_API_URL", DEFAULT_API_URL);
        String model = envOrDefault("GROQ_MODEL", DEFAULT_MODEL);

        try {
            String payload = buildPayload(model, systemPrompt, userPrompt, maxTokens);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 401) {
                return "### INVALID API KEY ###\n\nThe provided GROQ_API_KEY is invalid or expired.";
            }
            
            if (response.statusCode() >= 400) {
                return "### GROQ API ERROR (" + response.statusCode() + ") ###\n\n" + safeBody(response.body());
            }
            
            return parseTextResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "### SYSTEM ERROR ###\n\nOperation was interrupted: " + e.getMessage();
        } catch (IOException e) {
            return "### NETWORK ERROR ###\n\nCould not connect to Groq AI: " + e.getMessage();
        }
    }

    /**
     * AI-powered spelling and grammar correction, including profanity filtering.
     */
    public String correctSpellingAndGrammar(String text) {
        if (text == null || text.isBlank()) return "";
        return ask(
            "You are a professional writing assistant. Correct the spelling and grammar of the following text while maintaining its original meaning and tone. Additionally, strictly filter and remove any profanity, bad words, or inappropriate language, in both French and English. Return ONLY the corrected and filtered text, no explanations.",
            text,
            Math.max(500, text.length() * 2)
        );
    }

    private String buildPayload(String model, String systemPrompt, String userPrompt, int maxTokens) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.4);
        root.put("max_tokens", maxTokens);

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        return objectMapper.writeValueAsString(root);
    }

    private String parseTextResponse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "### EMPTY RESPONSE ###\n\nGroq AI returned no suggestions for this query.";
        }
        String content = choices.get(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            return "### EMPTY CONTENT ###\n\nGroq AI returned an empty response body.";
        }
        return content.trim();
    }

    private String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String safeBody(String body) {
        if (body == null) return "";
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
