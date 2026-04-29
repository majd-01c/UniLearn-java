package service;

import entities.User;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.MailSessionProvider;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private static final String WELCOME_SUBJECT = "\uD83C\uDF93 Welcome to UniLearn Platform";
    private static final String RESET_SUBJECT = "\uD83D\uDD10 Password Reset";
    private static final String RESET_LINK_SUBJECT = "\uD83D\uDD11 Password Reset Link - UniLearn";
    private static final String VERIFY_SUBJECT = "Email Verification - UniLearn";

    private final ExecutorService emailExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("email-sender-thread");
        thread.setDaemon(true);
        return thread;
    });

    public CompletableFuture<Boolean> sendWelcomeEmail(User user, String tempPassword) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String role = valueOrDefault(user.getRole(), "USER");
        String fullName = valueOrDefault(user.getName(), "Learner");

        String textBody = String.format(
                "Hello %s,%n%n" +
                        "Welcome to UniLearn!%n" +
                        "Your account has been created successfully.%n%n" +
                        "Role: %s%n" +
                        "Temporary password: %s%n%n" +
                        "Login instructions:%n" +
                        "1) Sign in with your email and temporary password.%n" +
                        "2) Change your password immediately after first login.%n" +
                        "3) Complete your profile information.%n%n" +
                        "Regards,%nUniLearn Team",
                fullName,
                role,
                valueOrDefault(tempPassword, "")
        );

        String htmlBody = "<html><body>"
                + "<h2>Welcome to UniLearn</h2>"
                + "<p>Hello <b>" + escapeHtml(fullName) + "</b>,</p>"
                + "<p>Your account has been created successfully.</p>"
                + "<ul>"
                + "<li><b>Role:</b> " + escapeHtml(role) + "</li>"
                + "<li><b>Temporary password:</b> <code>" + escapeHtml(valueOrDefault(tempPassword, "")) + "</code></li>"
                + "</ul>"
                + "<p><b>Login instructions:</b></p>"
                + "<ol>"
                + "<li>Sign in with your email and temporary password.</li>"
                + "<li>Change your password immediately after first login.</li>"
                + "<li>Complete your profile information.</li>"
                + "</ol>"
                + "<p>Regards,<br/>UniLearn Team</p>"
                + "</body></html>";

        return sendEmailAsync(user.getEmail(), WELCOME_SUBJECT, textBody, htmlBody);
    }

    public CompletableFuture<Boolean> sendPasswordResetEmail(User user, String tempPassword) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String fullName = valueOrDefault(user.getName(), "Learner");

        String textBody = String.format(
                "Hello %s,%n%n" +
                        "Your password has been reset.%n" +
                        "New temporary password: %s%n%n" +
                        "Please sign in and change it immediately.%n%n" +
                        "Regards,%nUniLearn Team",
                fullName,
                valueOrDefault(tempPassword, "")
        );

        String htmlBody = "<html><body>"
                + "<h2>Password Reset</h2>"
                + "<p>Hello <b>" + escapeHtml(fullName) + "</b>,</p>"
                + "<p>Your password has been reset.</p>"
                + "<p><b>New temporary password:</b> <code>" + escapeHtml(valueOrDefault(tempPassword, "")) + "</code></p>"
                + "<p>Please sign in and change it immediately.</p>"
                + "<p>Regards,<br/>UniLearn Team</p>"
                + "</body></html>";

        return sendEmailAsync(user.getEmail(), RESET_SUBJECT, textBody, htmlBody);
    }

    public CompletableFuture<Boolean> sendPasswordResetLinkEmail(User user, String token, String resetLink) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String fullName = valueOrDefault(user.getName(), "Learner");
        String safeToken = valueOrDefault(token, "");
        String safeLink = valueOrDefault(resetLink, "");

        String textBody = String.format(
                "Hello %s,%n%n"
                        + "A password reset was requested for your account.%n%n"
                        + "Reset link:%n%s%n%n"
                        + "Reset token:%n%s%n%n"
                        + "If you did not request this, ignore this message.%n"
                        + "This reset token expires in 2 hours.%n%n"
                        + "Regards,%nUniLearn Team",
                fullName,
                safeLink,
                safeToken
        );

        String htmlBody = "<html><body>"
                + "<h2>Password Reset Request</h2>"
                + "<p>Hello <b>" + escapeHtml(fullName) + "</b>,</p>"
                + "<p>A password reset was requested for your account.</p>"
                + "<p><b>Reset link:</b><br/><a href='" + escapeHtml(safeLink) + "'>" + escapeHtml(safeLink) + "</a></p>"
                + "<p><b>Reset token:</b><br/><code>" + escapeHtml(safeToken) + "</code></p>"
                + "<p>If you did not request this, ignore this message.</p>"
                + "<p>This reset token expires in 2 hours.</p>"
                + "<p>Regards,<br/>UniLearn Team</p>"
                + "</body></html>";

        return sendEmailAsync(user.getEmail(), RESET_LINK_SUBJECT, textBody, htmlBody);
    }

    public CompletableFuture<Boolean> sendVerificationCodeEmail(String email, String verificationCode) {
        if (email == null || email.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String textBody = String.format(
                "Your UniLearn email verification code is: %s%n%n" +
                        "This code is time-limited. Do not share it with anyone.",
                valueOrDefault(verificationCode, "")
        );

        String htmlBody = "<html><body>"
                + "<h2>Email Verification</h2>"
                + "<p>Your UniLearn verification code is:</p>"
                + "<h3><code>" + escapeHtml(valueOrDefault(verificationCode, "")) + "</code></h3>"
                + "<p>This code is time-limited. Do not share it with anyone.</p>"
                + "</body></html>";

        return sendEmailAsync(email, VERIFY_SUBJECT, textBody, htmlBody);
    }

    public CompletableFuture<Boolean> sendNotificationEmail(String to, String subject, String message) {
        if (to == null || to.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String safeSubject = valueOrDefault(subject, "UniLearn Notification");
        String textBody = valueOrDefault(message, "");
        String htmlBody = "<html><body><p>" + escapeHtml(textBody).replace("\n", "<br/>") + "</p></body></html>";

        return sendEmailAsync(to, safeSubject, textBody, htmlBody);
    }

    public void shutdown() {
        emailExecutor.shutdown();
    }

    private CompletableFuture<Boolean> sendEmailAsync(String to, String subject, String textBody, String htmlBody) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                sendEmail(to, subject, textBody, htmlBody);
                return true;
            } catch (Exception exception) {
                LOGGER.error("Failed to send email to {} with subject {}", to, subject, exception);
                return false;
            }
        }, emailExecutor);
    }

    private void sendEmail(String to, String subject, String textBody, String htmlBody) throws MessagingException {
        Session session = MailSessionProvider.getSession();
        String sender = MailSessionProvider.getSenderAddress();

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(sender));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject, "UTF-8");

        MimeBodyPart plainPart = new MimeBodyPart();
        plainPart.setText(textBody, "UTF-8");

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

        MimeMultipart multipart = new MimeMultipart("alternative");
        multipart.addBodyPart(plainPart);
        multipart.addBodyPart(htmlPart);

        message.setContent(multipart);
        Transport.send(message);

        LOGGER.info("Email sent successfully to {}", to);
    }

    private String valueOrDefault(String value, String fallback) {
        return Objects.requireNonNullElse(value, fallback);
    }

    private String escapeHtml(String value) {
        String source = valueOrDefault(value, "");
        return source
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
