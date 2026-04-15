package controller;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label summaryLabel;

    @FXML
    private Button openUsersButton;

    @FXML
    private Button openProfileButton;

    @FXML
    private Button openChangePasswordButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        welcomeLabel.setText("Welcome to UniLearn");
        roleLabel.setText("Role: -");
        summaryLabel.setText("Your desktop workspace is ready.");
    }

    public void setUser(User user) {
        String email = user == null || user.getEmail() == null ? "User" : user.getEmail();
        String role = user == null || user.getRole() == null ? "USER" : user.getRole().replace("ROLE_", "");

        welcomeLabel.setText("Welcome, " + email);
        roleLabel.setText("Role: " + role);
        summaryLabel.setText(roleSpecificSummary(role));

        openUsersButton.setDisable(!"ADMIN".equalsIgnoreCase(role));
    }

    @FXML
    private void onOpenUsers() {
        AppNavigator.showUsers();
    }

    @FXML
    private void onOpenProfile() {
        AppNavigator.showProfile();
    }

    @FXML
    private void onOpenChangePassword() {
        AppNavigator.showChangePassword();
    }

    private String roleSpecificSummary(String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return "You can manage users, roles, and platform operations from this desktop workspace.";
        }
        if ("TEACHER".equalsIgnoreCase(role)) {
            return "You can access teaching modules, assessments, and learner progress from here.";
        }
        if ("STUDENT".equalsIgnoreCase(role)) {
            return "You can track courses, grades, and personal progress from your desktop home.";
        }
        if ("PARTNER".equalsIgnoreCase(role)) {
            return "You can coordinate partner workflows and learning programs from this dashboard.";
        }

        return "Your desktop workspace is ready.";
    }
}
