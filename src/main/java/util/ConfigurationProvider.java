package util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration provider that loads properties from environment variables or system properties.
 * Supports fallback to default values.
 */
public final class ConfigurationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationProvider.class);

    private ConfigurationProvider() {
    }

    /**
     * Get a configuration property from environment variables or system properties.
     *
     * @param key Property key
     * @param defaultValue Default value if not found
     * @return Property value or default value
     */
    public static String getProperty(String key, String defaultValue) {
        if (key == null || key.isBlank()) {
            return defaultValue;
        }

        // Try environment variable first
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
