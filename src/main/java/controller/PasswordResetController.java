package controller;

import entities.ResetToken;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import repository.ResetTokenRepository;
import service.AuthenticationService;
import service.PasswordManagementService;
import util.AppNavigator;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class PasswordResetController implements Initializable {

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[!@#$%^&*].*");

    @FXML
    private TextField tokenField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private ProgressBar passwordStrengthBar;

    @FXML
    private Label passwordStrengthLabel;

    @FXML
    private Label messageLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();
    private final PasswordManagementService passwordManagementService = new PasswordManagementService();
    private final ResetTokenRepository resetTokenRepository = new ResetTokenRepository();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messageLabel.setText("");
        passwordStrengthBar.setProgress(0);
        passwordStrengthLabel.setText("Strength: -");

        newPasswordField.textProperty().addListener((obs, oldValue, newValue) -> updateStrengthIndicator(newValue));
    }

    public void setTokenInput(String tokenOrUrl) {
        String token = extractToken(tokenOrUrl);
        if (token != null) {
            tokenField.setText(token);
        }
    }

    @FXML
    private void onValidateToken() {
        String token = extractToken(tokenField.getText());
        if (token == null) {
            showAlert(Alert.AlertType.ERROR, "Missing Token", "Enter or paste a reset token or reset URL.");
            return;
        }

        ValidationResult result = validateToken(token);
        if (result.valid()) {
            setMessage("Token is valid.", false);
        } else {
            setMessage(result.message(), true);
            showAlert(Alert.AlertType.ERROR, "Invalid Token", result.message());
        }
    }

    @FXML
    private void onResetPassword() {
        String token = extractToken(tokenField.getText());
        String newPassword = normalize(newPasswordField.getText());
        String confirmPassword = normalize(confirmPasswordField.getText());

        if (token == null) {
            showAlert(Alert.AlertType.ERROR, "Missing Token", "Enter or paste a reset token or reset URL.");
            return;
        }

        if (newPassword == null || confirmPassword == null) {
            showAlert(Alert.AlertType.ERROR, "Missing Password", "Both password fields are required.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showAlert(Alert.AlertType.ERROR, "Password Mismatch", "New password and confirm password do not match.");
            return;
        }

        if (!passwordManagementService.validatePasswordStrength(newPassword)) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Weak Password",
                    "Password must be at least 8 characters and include uppercase, lowercase, number, and special character (!@#$%^&*)."
            );
            return;
        }

        ValidationResult validationResult = validateToken(token);
        if (!validationResult.valid()) {
            showAlert(Alert.AlertType.ERROR, "Invalid Token", validationResult.message());
            return;
        }

        try {
            authenticationService.resetPasswordWithToken(token, newPassword);
            showAlert(Alert.AlertType.INFORMATION, "Password Reset", "Password reset successful. You can now login.");
            AppNavigator.showLogin();
        } catch (Exception exception) {
            showAlert(Alert.AlertType.ERROR, "Reset Failed", safeErrorMessage(exception));
        }
    }

    @FXML
    private void onGoToLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void onGoToRequestReset() {
        AppNavigator.showPasswordResetRequest();
    }

    private ValidationResult validateToken(String token) {
        try {
            Optional<ResetToken> tokenOptional = resetTokenRepository.findByToken(token);
            if (tokenOptional.isEmpty()) {
                return new ValidationResult(false, "Reset token not found.");
            }

            ResetToken resetToken = tokenOptional.get();
            authenticationService.validateResetToken(token, resetToken.getUser());
            return new ValidationResult(true, "Token valid");
        } catch (Exception exception) {
            return new ValidationResult(false, safeErrorMessage(exception));
        }
    }

    private void updateStrengthIndicator(String password) {
        int score = 0;

        if (password != null && !password.isEmpty()) {
            if (password.length() >= 8) {
                score++;
            }
            if (password.length() >= 12) {
                score++;
            }
            if (UPPERCASE_PATTERN.matcher(password).matches()) {
                score++;
            }
            if (LOWERCASE_PATTERN.matcher(password).matches()) {
                score++;
            }
            if (DIGIT_PATTERN.matcher(password).matches()) {
                score++;
            }
            if (SPECIAL_PATTERN.matcher(password).matches()) {
                score++;
            }
        }

        double progress = score / 6.0;
        passwordStrengthBar.setProgress(progress);

        if (score <= 1) {
            passwordStrengthLabel.setText("Strength: Very Weak");
            passwordStrengthLabel.setStyle("-fx-text-fill: #c0392b;");
        } else if (score <= 3) {
            passwordStrengthLabel.setText("Strength: Weak");
            passwordStrengthLabel.setStyle("-fx-text-fill: #d35400;");
        } else if (score == 4) {
            passwordStrengthLabel.setText("Strength: Medium");
            passwordStrengthLabel.setStyle("-fx-text-fill: #f39c12;");
        } else if (score == 5) {
            passwordStrengthLabel.setText("Strength: Strong");
            passwordStrengthLabel.setStyle("-fx-text-fill: #27ae60;");
        } else {
            passwordStrengthLabel.setText("Strength: Very Strong");
            passwordStrengthLabel.setStyle("-fx-text-fill: #1e8449;");
        }
    }

    private String extractToken(String tokenOrUrl) {
        String input = normalize(tokenOrUrl);
        if (input == null) {
            return null;
        }

        int tokenIndex = input.indexOf("token=");
        if (tokenIndex >= 0) {
            String part = input.substring(tokenIndex + "token=".length());
            int ampIndex = part.indexOf('&');
            if (ampIndex >= 0) {
                part = part.substring(0, ampIndex);
            }
            String decoded = URLDecoder.decode(part, StandardCharsets.UTF_8);
            return normalize(decoded);
        }

        return input;
    }

    private void setMessage(String text, boolean isError) {
        messageLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #1f8b4c;");
        messageLabel.setText(text);
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

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Operation failed" : message;
    }

    private record ValidationResult(boolean valid, String message) {
    }
}
