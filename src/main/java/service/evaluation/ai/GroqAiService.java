package service.evaluation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;

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
    
    // Configured API Key environment variable name
    private static final String GROQ_API_KEY_ENV_VAR = "GROQ_API_KEY";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Dotenv dotenv;

    public GroqAiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.dotenv = loadDotenv();
    }

    /**
     * Checks if the Groq API key is present.
     */
    public boolean isConfigured() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    private String getApiKey() {
        String key = dotenvValue(GROQ_API_KEY_ENV_VAR);
        return (key != null && !key.isBlank()) ? key : System.getenv(GROQ_API_KEY_ENV_VAR);
    }

    /**
     * Sends a request to the Groq API.
     */
    public String ask(String systemPrompt, String userPrompt, int maxTokens) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "### AI CONFIGURATION ERROR ###\n\n" +
                   "The Groq AI API key is not set. Please ensure 'GROQ_API_KEY' is provided via environment variables or .env file.";
        }

        String apiUrl = envOrDefault("GROQ_API_URL", DEFAULT_API_URL);
        String model = envOrDefault("GROQ_MODEL", DEFAULT_MODEL);

        try {
            String payload = buildPayload(model, systemPrompt, userPrompt, maxTokens);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 401) {
                return "### AUTHENTICATION ERROR ###\n\nThe provided GROQ_API_KEY is invalid or unauthorized.";
            }
            
            if (response.statusCode() >= 400) {
                return "### GROQ API ERROR (" + response.statusCode() + ") ###\n\n" + safeBody(response.body());
            }
            
            return parseTextResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "### SYSTEM ERROR ###\n\nThe AI request was interrupted: " + e.getMessage();
        } catch (IOException e) {
            return "### NETWORK ERROR ###\n\nCould not connect to the AI service: " + e.getMessage();
        }
    }

    /**
     * AI-powered spelling and grammar correction, including profanity filtering (censorship with ****).
     */
    public String correctSpellingAndGrammar(String text) {
        if (text == null || text.isBlank()) return "";
        return ask(
            "You are a professional writing assistant. Correct the spelling and grammar of the following text while maintaining its original meaning and tone. Additionally, detect and censor any profanity, bad words, or inappropriate language in both French and English by replacing them with asterisks (****). Return ONLY the corrected and censored text, no explanations.",
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
            return "### RESPONSE ERROR ###\n\nGroq AI returned an invalid or empty response structure.";
        }
        String content = choices.get(0).path("message").path("content").asText();
        if (content == null || content.isBlank()) {
            return "### EMPTY RESPONSE ###\n\nGroq AI generated no content.";
        }
        return content.trim();
    }

    private String envOrDefault(String key, String fallback) {
        String value = dotenvValue(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private Dotenv loadDotenv() {
        try {
            return Dotenv.configure().ignoreIfMissing().load();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private String dotenvValue(String key) {
        return dotenv == null ? null : dotenv.get(key);
    }

    private String safeBody(String body) {
        if (body == null) return "No details provided.";
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
