package util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class PropertiesLoader {

    private PropertiesLoader() {
    }

    public static Properties load(String resourcePath) {
        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourcePath);

        if (inputStream == null) {
            throw new IllegalStateException("Missing config resource: " + resourcePath);
        }

        Properties properties = new Properties();
        try (InputStream stream = inputStream) {
            properties.load(stream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load config resource: " + resourcePath, exception);
        }
    }
}
