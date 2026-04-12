package controller;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import security.UserSession;
import service.AuthenticationService;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label messageLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messageLabel.setText("");
    }

    @FXML
    private void handleLogin() {
        String email = normalize(emailField.getText());
        String password = normalize(passwordField.getText());

        if (email == null || password == null) {
            setMessage("Email and password are required.", true);
            return;
        }

        try {
            User authenticated = authenticationService.authenticate(email, password);
            UserSession.setCurrentUser(authenticated);
            setMessage("Welcome " + safeText(authenticated.getEmail()), false);
            AppNavigator.loginSuccess(authenticated);
        } catch (Exception exception) {
            setMessage(safeErrorMessage(exception), true);
        }
    }

    @FXML
    private void handleForgotPassword() {
        AppNavigator.showPasswordResetRequest();
    }

    private void setMessage(String text, boolean isError) {
        messageLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #1f8b4c;");
        messageLabel.setText(text);
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
}
