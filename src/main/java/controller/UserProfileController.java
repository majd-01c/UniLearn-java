package controller;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import security.UserSession;
import service.UserService;
import util.AppNavigator;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class UserProfileController implements Initializable {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML
    private Label roleLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField nameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField locationField;

    @FXML
    private Button saveProfileButton;

    @FXML
    private Button backToDashboardButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Label messageLabel;

    private final UserService userService = new UserService();

    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messageLabel.setText("");
        loadCurrentUserFromSession();
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            loadCurrentUserFromSession();
            return;
        }

        currentUser = userService.getUserById(user.getId().longValue()).orElse(user);
        bindUser();
    }

    @FXML
    private void handleSaveProfile() {
        if (currentUser == null || currentUser.getId() == null) {
            setMessage("No active session user. Please login again.", true);
            return;
        }

        String name = normalize(nameField.getText());
        String email = normalize(emailField.getText());
        String phone = normalize(phoneField.getText());
        String location = normalize(locationField.getText());

        if (name == null) {
            setMessage("Name is required.", true);
            return;
        }

        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            setMessage("A valid email is required.", true);
            return;
        }

        Optional<User> existing = userService.getUserByEmail(email);
        if (existing.isPresent() && !existing.get().getId().equals(currentUser.getId())) {
            setMessage("This email is already used by another account.", true);
            return;
        }

        try {
            currentUser.setName(name);
            currentUser.setEmail(email.toLowerCase());
            currentUser.setPhone(blankToNull(phone));
            currentUser.setLocation(blankToNull(location));

            currentUser = userService.updateUser(currentUser);
            UserSession.setCurrentUser(currentUser);

            setMessage("Profile updated successfully.", false);
            bindUser();
        } catch (Exception exception) {
            setMessage(safeErrorMessage(exception), true);
        }
    }

    @FXML
    private void handleBackToDashboard() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleLogout() {
        AppNavigator.logout();
    }

    private void loadCurrentUserFromSession() {
        Optional<Integer> userId = UserSession.getCurrentUserId();
        if (userId.isEmpty()) {
            setMessage("No active session user. Please login.", true);
            disableForm(true);
            return;
        }

        currentUser = userService.getUserById(userId.get().longValue()).orElse(null);
        if (currentUser == null) {
            setMessage("Session user not found. Please login again.", true);
            disableForm(true);
            return;
        }

        disableForm(false);
        bindUser();
    }

    private void bindUser() {
        if (currentUser == null) {
            return;
        }

        roleLabel.setText(formatRole(currentUser.getRole()));
        boolean active = currentUser.getIsActive() == (byte) 1;
        statusLabel.setText(active ? "Active" : "Inactive");
        statusLabel.getStyleClass().removeAll("user-profile-status-active", "user-profile-status-inactive");
        statusLabel.getStyleClass().add(active ? "user-profile-status-active" : "user-profile-status-inactive");

        nameField.setText(safeText(currentUser.getName()));
        emailField.setText(safeText(currentUser.getEmail()));
        phoneField.setText(safeText(currentUser.getPhone()));
        locationField.setText(safeText(currentUser.getLocation()));
    }

    private void disableForm(boolean disable) {
        nameField.setDisable(disable);
        emailField.setDisable(disable);
        phoneField.setDisable(disable);
        locationField.setDisable(disable);
        saveProfileButton.setDisable(disable);
    }

    private String formatRole(String role) {
        if (role == null || role.isBlank()) {
            return "-";
        }

        String normalized = role.toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToNull(String value) {
        return normalize(value);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private void setMessage(String message, boolean error) {
        messageLabel.getStyleClass().removeAll("form-feedback-error", "form-feedback-success");
        messageLabel.getStyleClass().add(error ? "form-feedback-error" : "form-feedback-success");
        messageLabel.setText(message);
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Unable to update profile" : message;
    }
}
