package util;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration provider that loads properties from environment variables or system properties.
 * Supports fallback to default values.
 */
public final class ConfigurationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationProvider.class);
    private static Dotenv dotenv;

    static {
        // Load .env file at startup
        try {
            dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            LOGGER.info("Loaded configuration from .env file");
        } catch (Exception e) {
            LOGGER.warn("Could not load .env file: {}", e.getMessage());
            dotenv = null;
        }
    }

    private ConfigurationProvider() {
    }

    /**
     * Get a configuration property from .env, environment variables, or system properties.
     *
     * @param key Property key
     * @param defaultValue Default value if not found
     * @return Property value or default value
     */
    public static String getProperty(String key, String defaultValue) {
        if (key == null || key.isBlank()) {
            return defaultValue;
        }

        // Try .env file first
        if (dotenv != null) {
            String envFileValue = dotenv.get(key);
            if (envFileValue != null && !envFileValue.isBlank()) {
                LOGGER.debug("Loaded {} from .env file", key);
                return envFileValue;
            }
        }

        // Try resilient .env parsing as a fallback. This handles IDE launch
        // directories, duplicate keys, inline comments, and parser failures.
        String dotEnvValue = getPropertyFromDotEnvFiles(key);
        if (dotEnvValue != null && !dotEnvValue.isBlank()) {
            LOGGER.debug("Loaded {} from fallback .env parser", key);
            return dotEnvValue;
        }

        // Try environment variable
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            LOGGER.debug("Loaded {} from environment variable", key);
            return envValue;
        }

        // Try system property
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            LOGGER.debug("Loaded {} from system property", key);
            return systemValue;
        }

        // Return default
        LOGGER.debug("Using default value for property {}", key);
        return defaultValue;
    }

    private static String getPropertyFromDotEnvFiles(String key) {
        String value = null;
        for (Path envFile : dotEnvCandidates()) {
            String candidate = readDotEnvValue(envFile, key);
            if (candidate != null && !candidate.isBlank()) {
                value = candidate;
            }
        }
        return value;
    }

    private static Set<Path> dotEnvCandidates() {
        Set<Path> candidates = new LinkedHashSet<>();
        addDotEnvCandidate(candidates, Path.of(System.getProperty("user.dir", ".")));
        addDotEnvCandidate(candidates, Path.of(".").toAbsolutePath().normalize());

        Path cursor = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; cursor != null && depth < 6; depth++) {
            addDotEnvCandidate(candidates, cursor);
            cursor = cursor.getParent();
        }

        return candidates;
    }

    private static void addDotEnvCandidate(Set<Path> candidates, Path directory) {
        if (directory == null) {
            return;
        }
        candidates.add(directory.resolve(".env").toAbsolutePath().normalize());
    }

    private static String readDotEnvValue(Path envFile, String key) {
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
            String value = null;
            String prefix = key + "=";
            for (String line : lines) {
                if (line == null) {
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.startsWith(prefix)) {
                    continue;
                }

                value = normalizeDotEnvValue(trimmed.substring(prefix.length()));
            }
            return value;
        } catch (IOException exception) {
            LOGGER.debug("Unable to read .env candidate {}", envFile, exception);
            return null;
        }
    }

    private static String normalizeDotEnvValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        int inlineCommentIndex = value.indexOf(" #");
        if (inlineCommentIndex >= 0) {
            value = value.substring(0, inlineCommentIndex).trim();
        }

        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    /**
     * Get a configuration property without default.
     *
     * @param key Property key
     * @return Property value or empty string
     */
    public static String getProperty(String key) {
        return getProperty(key, "");
    }
}
