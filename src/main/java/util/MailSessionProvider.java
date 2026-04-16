package util;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;

public final class MailSessionProvider {

    private static final String MAIL_CONFIG_PATH = "confg/mail.properties";
    private static final String MAIL_LOCAL_CONFIG_PATH = "confg/mail.local.properties";
    private static final String SRC_MAIL_CONFIG_PATH = "src/main/resources/confg/mail.properties";
    private static final String SRC_MAIL_LOCAL_CONFIG_PATH = "src/main/resources/confg/mail.local.properties";

    private static final String MAIL_USERNAME_KEY = "mail.username";
    private static final String MAIL_PASSWORD_KEY = "mail.password";
    private static final String MAIL_USERNAME_BASE64_KEY = "mail.username.base64";
    private static final String MAIL_PASSWORD_BASE64_KEY = "mail.password.base64";

    private static final String ENV_MAIL_USERNAME_PRIMARY = "UNILEARN_MAIL_USERNAME";
    private static final String ENV_MAIL_PASSWORD_PRIMARY = "UNILEARN_MAIL_PASSWORD";
    private static final String ENV_MAIL_USERNAME_FALLBACK = "MAIL_USERNAME";
    private static final String ENV_MAIL_PASSWORD_FALLBACK = "MAIL_PASSWORD";

    private MailSessionProvider() {
    }

    public static Session getSession() {
        return createSession();
    }

    public static String getSenderAddress() {
        Properties properties = loadMailProperties();

        String configuredFrom = normalize(properties.getProperty("mail.from"));
        String username = resolveUsername(properties);

        if (configuredFrom == null || configuredFrom.endsWith("@unilearn.local")) {
            if (username != null && username.contains("@")) {
                return username;
            }
        }

        return configuredFrom == null ? "no-reply@unilearn.local" : configuredFrom;
    }

    public static String validateConfiguration() {
        Properties source = loadMailProperties();

        String host = normalize(source.getProperty("mail.smtp.host"));
        if (host == null) {
            return "SMTP host is missing in confg/mail.properties.";
        }

        boolean authEnabled = Boolean.parseBoolean(source.getProperty("mail.smtp.auth", "false"));
        if (!authEnabled) {
            return null;
        }

        if (resolveUsername(source) == null) {
            return "SMTP auth is enabled but username is missing. Set mail.username/mail.username.base64 in mail.properties (or mail.local.properties), use UNILEARN_MAIL_USERNAME, or switch to an SMTP relay with mail.smtp.auth=false.";
        }

        if (resolvePassword(source) == null) {
            return "SMTP auth is enabled but password is missing. Set mail.password/mail.password.base64 in mail.properties (or mail.local.properties), use UNILEARN_MAIL_PASSWORD, or switch to an SMTP relay with mail.smtp.auth=false.";
        }

        return null;
    }

    public static String configurationSummary() {
        Properties source = loadMailProperties();

        boolean authEnabled = Boolean.parseBoolean(source.getProperty("mail.smtp.auth", "false"));
        String host = normalize(source.getProperty("mail.smtp.host"));
        String username = resolveUsername(source);
        String password = resolvePassword(source);

        return String.format(
                "SMTP(host=%s, auth=%s, username_set=%s, password_set=%s)",
                host == null ? "<missing>" : host,
                authEnabled,
                username != null,
                password != null
        );
    }

    public static String getConfiguredUsername() {
        return resolveUsername(loadMailProperties());
    }

    public static String getConfiguredFromAddress() {
        return normalize(loadMailProperties().getProperty("mail.from"));
    }

    public static void saveLocalCredentials(String username, String password, String fromAddress) {
        String normalizedUsername = normalize(username);
        String normalizedPassword = normalize(password);

        if (normalizedUsername == null) {
            throw new IllegalArgumentException("SMTP username is required");
        }
        if (normalizedPassword == null) {
            throw new IllegalArgumentException("SMTP password is required");
        }

        Path localConfigPath = Paths.get(MAIL_LOCAL_CONFIG_PATH);
        ensureParentDirectory(localConfigPath);

        Properties localOverrides = new Properties();
        if (Files.exists(localConfigPath) && Files.isRegularFile(localConfigPath)) {
            try (InputStream inputStream = Files.newInputStream(localConfigPath)) {
                localOverrides.load(inputStream);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read local mail config: " + localConfigPath, exception);
            }
        }

        localOverrides.setProperty("mail.smtp.host", valueOrDefault(localOverrides.getProperty("mail.smtp.host"), "smtp.gmail.com"));
        localOverrides.setProperty("mail.smtp.port", valueOrDefault(localOverrides.getProperty("mail.smtp.port"), "587"));
        localOverrides.setProperty("mail.smtp.auth", "true");
        localOverrides.setProperty("mail.smtp.starttls.enable", valueOrDefault(localOverrides.getProperty("mail.smtp.starttls.enable"), "true"));
        localOverrides.setProperty("mail.smtp.ssl.trust", valueOrDefault(localOverrides.getProperty("mail.smtp.ssl.trust"), "smtp.gmail.com"));

        localOverrides.setProperty(MAIL_USERNAME_KEY, normalizedUsername);
        localOverrides.setProperty(MAIL_PASSWORD_KEY, normalizedPassword);

        String normalizedFrom = normalize(fromAddress);
        if (normalizedFrom != null) {
            localOverrides.setProperty("mail.from", normalizedFrom);
        } else {
            localOverrides.setProperty("mail.from", normalizedUsername);
        }

        try (OutputStream outputStream = Files.newOutputStream(localConfigPath)) {
            localOverrides.store(outputStream, "Local SMTP overrides for UniLearn");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write local mail config: " + localConfigPath, exception);
        }
    }

    private static Session createSession() {
        Properties source = loadMailProperties();
        Properties smtpProperties = new Properties();

        for (String key : source.stringPropertyNames()) {
            if (key.startsWith("mail.smtp.")) {
                smtpProperties.put(key, source.getProperty(key));
            }
        }

        String username = resolveUsername(source);
        String password = resolvePassword(source);

        if (username != null) {
            String resolvedPassword = password == null ? "" : password;
            return Session.getInstance(smtpProperties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, resolvedPassword);
                }
            });
        }

        return Session.getInstance(smtpProperties);
    }

    private static String resolveUsername(Properties source) {
        String fromProperties = normalize(source.getProperty(MAIL_USERNAME_KEY));
        if (fromProperties != null) {
            return fromProperties;
        }

        String fromBase64 = decodeBase64Property(source, MAIL_USERNAME_BASE64_KEY);
        if (fromBase64 != null) {
            return fromBase64;
        }

        String envPrimary = normalize(System.getenv(ENV_MAIL_USERNAME_PRIMARY));
        if (envPrimary != null) {
            return envPrimary;
        }

        return normalize(System.getenv(ENV_MAIL_USERNAME_FALLBACK));
    }

    private static String resolvePassword(Properties source) {
        String fromProperties = normalize(source.getProperty(MAIL_PASSWORD_KEY));
        if (fromProperties != null) {
            return fromProperties;
        }

        String fromBase64 = decodeBase64Property(source, MAIL_PASSWORD_BASE64_KEY);
        if (fromBase64 != null) {
            return fromBase64;
        }

        String envPrimary = normalize(System.getenv(ENV_MAIL_PASSWORD_PRIMARY));
        if (envPrimary != null) {
            return envPrimary;
        }

        return normalize(System.getenv(ENV_MAIL_PASSWORD_FALLBACK));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String decodeBase64Property(Properties source, String key) {
        String encodedValue = normalize(source.getProperty(key));
        if (encodedValue == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encodedValue);
            return normalize(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Properties loadMailProperties() {
        Properties merged = new Properties();
        merged.putAll(PropertiesLoader.load(MAIL_CONFIG_PATH));

        overlayFileIfPresent(merged, Paths.get(SRC_MAIL_CONFIG_PATH));
        overlayFileIfPresent(merged, Paths.get(SRC_MAIL_LOCAL_CONFIG_PATH));
        overlayFileIfPresent(merged, Paths.get(MAIL_CONFIG_PATH));
        overlayFileIfPresent(merged, Paths.get(MAIL_LOCAL_CONFIG_PATH));

        return merged;
    }

    private static void overlayFileIfPresent(Properties target, Path path) {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return;
        }

        Properties overrides = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            overrides.load(inputStream);
        } catch (IOException exception) {
            return;
        }

        for (String key : overrides.stringPropertyNames()) {
            String value = normalize(overrides.getProperty(key));
            if (value != null) {
                target.setProperty(key, value);
            }
        }
    }

    private static void ensureParentDirectory(Path path) {
        if (path == null) {
            return;
        }

        Path parent = path.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create directory for local mail config: " + parent, exception);
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        String normalizedValue = normalize(value);
        return normalizedValue == null ? fallback : normalizedValue;
    }
}
