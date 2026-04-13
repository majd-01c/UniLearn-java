package controller;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import security.UserSession;
import service.AuthenticationService;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public class LoginController implements Initializable {

    private static final String PREF_NODE = "unilearn.desktop.login";
    private static final String PREF_REMEMBERED_EMAIL = "rememberedEmail";

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField passwordVisibleField;

    @FXML
    private Button loginButton;

    @FXML
    private Button passwordToggleButton;

    @FXML
    private CheckBox rememberMeCheckBox;

    @FXML
    private Label messageLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();
    private final Preferences preferences = Preferences.userRoot().node(PREF_NODE);

    private boolean passwordVisible;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messageLabel.setText("");
        loadRememberedEmail();

        if (passwordVisibleField != null && passwordField != null) {
            passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        }

        setPasswordVisibility(false);
        loginButton.setDefaultButton(true);

        emailField.textProperty().addListener((obs, oldValue, newValue) -> messageLabel.setText(""));
        passwordField.textProperty().addListener((obs, oldValue, newValue) -> messageLabel.setText(""));
    }

    @FXML
    private void handleLogin() {
        String email = normalize(emailField.getText());
        String password = normalize(resolvePasswordInput());

        if (!validateInputs(email, password)) {
            return;
        }

        try {
            User authenticated = authenticate(email, password);
            UserSession.setCurrentUser(authenticated);
            persistRememberedEmail(email);
            setMessage("Welcome " + safeText(authenticated.getEmail()), false);
            routeByRole(authenticated);
        } catch (Exception exception) {
            setMessage(safeErrorMessage(exception), true);
        }
    }

    @FXML
    private void handleTogglePasswordVisibility() {
        setPasswordVisibility(!passwordVisible);
    }

    @FXML
    private void handleForgotPassword() {
        AppNavigator.showPasswordResetRequest();
    }

    private void setMessage(String text, boolean isError) {
        messageLabel.getStyleClass().removeAll("form-feedback-error", "form-feedback-success");
        messageLabel.getStyleClass().add(isError ? "form-feedback-error" : "form-feedback-success");
        messageLabel.setText(text);
    }

    private boolean validateInputs(String email, String password) {
        if (email == null || password == null) {
            setMessage("Email and password are required.", true);
            return false;
        }

        if (!email.contains("@") || !email.contains(".")) {
            setMessage("Enter a valid email address.", true);
            return false;
        }

        return true;
    }

    private User authenticate(String email, String password) {
        return authenticationService.authenticate(email, password);
    }

    private void routeByRole(User authenticatedUser) {
        AppNavigator.loginSuccess(authenticatedUser);
    }

    private String resolvePasswordInput() {
        return passwordVisible ? passwordVisibleField.getText() : passwordField.getText();
    }

    private void setPasswordVisibility(boolean visible) {
        passwordVisible = visible;

        if (passwordField != null) {
            passwordField.setManaged(!visible);
            passwordField.setVisible(!visible);
        }
        if (passwordVisibleField != null) {
            passwordVisibleField.setManaged(visible);
            passwordVisibleField.setVisible(visible);
        }
        if (passwordToggleButton != null) {
            passwordToggleButton.setText(visible ? "Hide" : "Show");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Unable to sign in" : message;
    }

    private void loadRememberedEmail() {
        String rememberedEmail = normalize(preferences.get(PREF_REMEMBERED_EMAIL, null));
        if (rememberedEmail == null) {
            return;
        }

        emailField.setText(rememberedEmail);
        if (rememberMeCheckBox != null) {
            rememberMeCheckBox.setSelected(true);
        }
    }

    private void persistRememberedEmail(String email) {
        if (rememberMeCheckBox != null && rememberMeCheckBox.isSelected()) {
            preferences.put(PREF_REMEMBERED_EMAIL, email);
            return;
        }

        preferences.remove(PREF_REMEMBERED_EMAIL);
    }
}
