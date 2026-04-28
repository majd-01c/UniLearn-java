package util;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
