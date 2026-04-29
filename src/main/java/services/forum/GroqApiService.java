package services.forum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public class GroqApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroqApiService.class);

    private static final String CONFIG_RESOURCE = "confg/groq.properties";
    private static final String DEFAULT_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    private static final String KEY_SYSTEM_PROPERTY = "groq.api.key";
    private static final String KEY_CONFIG_PROPERTY = "groq.api.key";
    private static final String KEY_CONFIG_ENV_STYLE = "GROQ_API_KEY";
    private static final String ENV_GROQ_API_KEY = "GROQ_API_KEY";
    private static final String ENV_UNILEARN_GROQ_API_KEY = "UNILEARN_GROQ_API_KEY";

    private static final String[] FILE_CONFIG_PATHS = {
            "src/main/resources/confg/groq.properties",
            "src/main/resources/confg/groq.local.properties",
            "confg/groq.properties",
            "confg/groq.local.properties"
    };

    private static final String[] DOTENV_PATHS = {
            ".env",
            "src/main/resources/confg/.env"
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public GroqApiService() {
        Properties properties = loadProperties();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = resolveApiKey(properties);
        this.apiUrl = valueOrDefault(properties.getProperty("groq.api.url"), DEFAULT_API_URL);
        this.model = valueOrDefault(properties.getProperty("groq.model"), DEFAULT_MODEL);
        this.temperature = parseDouble(properties.getProperty("groq.temperature"), 0.7);
        this.maxTokens = parseInt(properties.getProperty("groq.max_tokens"), 1024);
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.equals("your_groq_api_key_here");
    }

    public String generateContent(String prompt) {
        if (!isAvailable()) {
            LOGGER.warn("Groq API key is not configured. Set GROQ_API_KEY or confg/groq.local.properties.");
            return null;
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", temperature);
            payload.put("max_tokens", maxTokens);

            ArrayNode messages = payload.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                LOGGER.error("Groq API returned HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isTextual()) {
                return contentNode.asText();
            }

            LOGGER.error("Unexpected Groq API response: {}", response.body());
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.error("Groq API call was interrupted", exception);
            return null;
        } catch (Exception exception) {
            LOGGER.error("Groq API call failed", exception);
            return null;
        }
    }

    public JsonNode extractJsonObject(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }

        try {
            return objectMapper.readTree(response.substring(start, end + 1));
        } catch (IOException exception) {
            LOGGER.warn("Unable to parse JSON returned by Groq", exception);
            return null;
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        loadResourceIfPresent(CONFIG_RESOURCE, properties);

        for (String candidate : FILE_CONFIG_PATHS) {
            overlayPropertiesFile(properties, Path.of(candidate));
        }
        for (String candidate : DOTENV_PATHS) {
            overlayDotEnvFile(properties, Path.of(candidate));
        }

        return properties;
    }

    private static void loadResourceIfPresent(String resourcePath, Properties target) {
        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourcePath);
        if (inputStream == null) {
            return;
        }

        try (InputStream stream = inputStream) {
            Properties resourceProperties = new Properties();
            resourceProperties.load(stream);
            target.putAll(resourceProperties);
        } catch (IOException exception) {
            LOGGER.warn("Unable to read Groq config resource {}", resourcePath, exception);
        }
    }

    private static void overlayPropertiesFile(Properties target, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            Properties overrides = new Properties();
            overrides.load(inputStream);
            for (String key : overrides.stringPropertyNames()) {
                String value = normalize(overrides.getProperty(key));
                if (value != null) {
                    target.setProperty(key, value);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to read Groq config file {}", path, exception);
        }
    }

    private static void overlayDotEnvFile(Properties target, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }

        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }

                int equalsIndex = line.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, equalsIndex).trim();
                String value = stripInlineComment(line.substring(equalsIndex + 1).trim());
                value = stripWrappingQuotes(value);
                if (!key.isEmpty() && normalize(value) != null) {
                    target.setProperty(key, value);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to read .env file {}", path, exception);
        }
    }

    private static String resolveApiKey(Properties properties) {
        String systemProperty = normalize(System.getProperty(KEY_SYSTEM_PROPERTY));
        if (systemProperty != null) {
            return systemProperty;
        }

        String envKey = normalize(System.getenv(ENV_GROQ_API_KEY));
        if (envKey != null) {
            return envKey;
        }

        String unilearnEnvKey = normalize(System.getenv(ENV_UNILEARN_GROQ_API_KEY));
        if (unilearnEnvKey != null) {
            return unilearnEnvKey;
        }

        String configKey = normalize(properties.getProperty(KEY_CONFIG_PROPERTY));
        if (configKey != null) {
            return configKey;
        }

        return normalize(properties.getProperty(KEY_CONFIG_ENV_STYLE));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(valueOrDefault(value, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(valueOrDefault(value, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String stripInlineComment(String value) {
        int spaceComment = value.indexOf(" #");
        int tabComment = value.indexOf("\t#");
        int commentIndex = -1;
        if (spaceComment >= 0 && tabComment >= 0) {
            commentIndex = Math.min(spaceComment, tabComment);
        } else if (spaceComment >= 0) {
            commentIndex = spaceComment;
        } else if (tabComment >= 0) {
            commentIndex = tabComment;
        }
        return commentIndex >= 0 ? value.substring(0, commentIndex).trim() : value.trim();
    }

    private static String stripWrappingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String valueOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
