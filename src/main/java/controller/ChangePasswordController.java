package controller;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import security.UserSession;
import service.PasswordManagementService;
import service.UserService;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class ChangePasswordController implements Initializable {

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[!@#$%^&*].*");

    @FXML
    private PasswordField currentPasswordField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private ProgressBar passwordStrengthBar;

    @FXML
    private Label passwordStrengthLabel;

    @FXML
    private Label currentPasswordErrorLabel;

    @FXML
    private Label newPasswordErrorLabel;

    @FXML
    private Label confirmPasswordErrorLabel;

    @FXML
    private Label successLabel;

    private final UserService userService = new UserService();
    private final PasswordManagementService passwordManagementService = new PasswordManagementService();

    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        passwordStrengthBar.setProgress(0.0);
        passwordStrengthLabel.setText("Strength: -");
        passwordStrengthBar.setStyle("-fx-accent: #c0392b;");

        clearInlineErrors();
        successLabel.setText("");

        newPasswordField.textProperty().addListener((obs, oldValue, newValue) -> updateStrengthIndicator(newValue));
        resolveCurrentUserFromSession();
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            return;
        }

        currentUser = userService.getUserById(user.getId().longValue()).orElse(null);
    }

    @FXML
    private void onChangePassword() {
        clearInlineErrors();
        successLabel.setText("");

        if (currentUser == null) {
            resolveCurrentUserFromSession();
        }

        if (currentUser == null) {
            currentPasswordErrorLabel.setText("No logged-in user session found. Please log in again.");
            return;
        }

        String currentPassword = normalize(currentPasswordField.getText());
        String newPassword = normalize(newPasswordField.getText());
        String confirmPassword = normalize(confirmPasswordField.getText());

        boolean valid = true;

        if (currentPassword == null) {
            currentPasswordErrorLabel.setText("Current password is required.");
            valid = false;
        }

        if (newPassword == null) {
            newPasswordErrorLabel.setText("New password is required.");
            valid = false;
        }

        if (confirmPassword == null) {
            confirmPasswordErrorLabel.setText("Please confirm the new password.");
            valid = false;
        }

        if (!valid) {
            return;
        }

        if (!passwordManagementService.validatePassword(currentPassword, currentUser.getPassword())) {
            currentPasswordErrorLabel.setText("Current password is incorrect.");
            return;
        }

        if (currentPassword.equals(newPassword)) {
            newPasswordErrorLabel.setText("New password must be different from current password.");
            return;
        }

        if (!passwordManagementService.validatePasswordStrength(newPassword)) {
            newPasswordErrorLabel.setText("Use at least 8 chars with uppercase, lowercase, number, and special (!@#$%^&*).");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordErrorLabel.setText("New password and confirmation do not match.");
            return;
        }

        try {
            currentUser.setPassword(passwordManagementService.hashPassword(newPassword));
            currentUser.setMustChangePassword((byte) 0);
            currentUser = userService.updateUser(currentUser);

            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            updateStrengthIndicator("");

            successLabel.setText("Password changed successfully.");
            showInfo("Password Updated", "Your password has been changed successfully.");
        } catch (Exception exception) {
            newPasswordErrorLabel.setText(safeErrorMessage(exception));
        }
    }

    @FXML
    private void onCancel() {
        AppNavigator.showHome();
    }

    private void resolveCurrentUserFromSession() {
        UserSession.getCurrentUserId().ifPresent(userId ->
                currentUser = userService.getUserById(userId.longValue()).orElse(null)
        );
    }

    private void updateStrengthIndicator(String password) {
        int score = 0;

        if (password != null && !password.isBlank()) {
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
            passwordStrengthBar.setStyle("-fx-accent: #c0392b;");
        } else if (score <= 3) {
            passwordStrengthLabel.setText("Strength: Weak");
            passwordStrengthLabel.setStyle("-fx-text-fill: #d35400;");
            passwordStrengthBar.setStyle("-fx-accent: #d35400;");
        } else if (score == 4) {
            passwordStrengthLabel.setText("Strength: Medium");
            passwordStrengthLabel.setStyle("-fx-text-fill: #f39c12;");
            passwordStrengthBar.setStyle("-fx-accent: #f39c12;");
        } else if (score == 5) {
            passwordStrengthLabel.setText("Strength: Strong");
            passwordStrengthLabel.setStyle("-fx-text-fill: #27ae60;");
            passwordStrengthBar.setStyle("-fx-accent: #27ae60;");
        } else {
            passwordStrengthLabel.setText("Strength: Very Strong");
            passwordStrengthLabel.setStyle("-fx-text-fill: #1e8449;");
            passwordStrengthBar.setStyle("-fx-accent: #1e8449;");
        }
    }

    private void clearInlineErrors() {
        currentPasswordErrorLabel.setText("");
        newPasswordErrorLabel.setText("");
        confirmPasswordErrorLabel.setText("");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Unable to change password right now.";
        }
        return message;
    }
}
