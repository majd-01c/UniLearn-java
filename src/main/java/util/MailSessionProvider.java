package util;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import java.util.Properties;

public final class MailSessionProvider {

    private static final Session SESSION = createSession();

    private MailSessionProvider() {
    }

    public static Session getSession() {
        return SESSION;
    }

    public static String getSenderAddress() {
        Properties properties = PropertiesLoader.load("confg/mail.properties");
        return properties.getProperty("mail.from", "no-reply@unilearn.local");
    }

    private static Session createSession() {
        Properties source = PropertiesLoader.load("confg/mail.properties");
        Properties smtpProperties = new Properties();

        for (String key : source.stringPropertyNames()) {
            if (key.startsWith("mail.smtp.")) {
                smtpProperties.put(key, source.getProperty(key));
            }
        }

        String username = source.getProperty("mail.username", "").trim();
        String password = source.getProperty("mail.password", "").trim();

        if (!username.isEmpty()) {
            return Session.getInstance(smtpProperties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        }

        return Session.getInstance(smtpProperties);
    }
}
