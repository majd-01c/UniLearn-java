package controller;

import entities.ResetToken;
import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import service.AuthenticationService;
import service.EmailService;
import service.UserService;
import util.AppNavigator;

import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class PasswordResetRequestController implements Initializable {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML
    private TextField emailField;

    @FXML
    private Label messageLabel;

    private final UserService userService = new UserService();
    private final AuthenticationService authenticationService = new AuthenticationService();
    private final EmailService emailService = new EmailService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messageLabel.setText("");
    }

    @FXML
    private void onSendResetLink() {
        String email = normalize(emailField.getText());
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            showAlert(Alert.AlertType.ERROR, "Invalid Email", "Enter a valid email address.");
            return;
        }

        try {
            Optional<User> userOptional = userService.getUserByEmail(email);
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                ResetToken resetToken = authenticationService.generatePasswordResetToken(user);

                String token = resetToken.getToken();
                String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
                String resetLink = "https://unilearn.local/reset-password?token=" + encodedToken;

                String body = "Hello,\n\n"
                        + "A password reset was requested for your account.\n"
                        + "Use this reset link:\n" + resetLink + "\n\n"
                        + "Or paste this token in the reset screen:\n" + token + "\n\n"
                        + "If you did not request this, you can ignore this email.\n";

                emailService.sendNotificationEmail(
                        user.getEmail(),
                        "Password Reset Link - UniLearn",
                        body
                );
            }
        } catch (Exception ignored) {
            // Keep a generic response to avoid disclosing whether an email exists.
        }

        setSuccessMessage("If email exists, reset link sent");
    }

    @FXML
    private void onGoToLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void onOpenResetPassword() {
        AppNavigator.showPasswordReset(null);
    }

    private void setSuccessMessage(String message) {
        messageLabel.setStyle("-fx-text-fill: #1f8b4c;");
        messageLabel.setText(message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
