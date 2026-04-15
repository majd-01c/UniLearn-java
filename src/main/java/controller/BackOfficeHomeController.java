package controller;

import entities.User;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import service.UserService;
import util.AppNavigator;

import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import javafx.util.Duration;

public class BackOfficeHomeController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label usersCountLabel;

    @FXML
    private Label pendingActionsLabel;

    @FXML
    private Label recentActivityLabel;

    @FXML
    private Label totalUsersStatLabel;

    @FXML
    private Label activeUsersStatLabel;

    @FXML
    private Label inactiveUsersStatLabel;

    @FXML
    private Label recentUsersStatLabel;

    private final UserService userService = new UserService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        welcomeLabel.setText("Welcome to BackOffice");
        roleLabel.setText("Role: ADMIN");
        usersCountLabel.setText("Total users: -");
        pendingActionsLabel.setText("Pending actions: -");
        recentActivityLabel.setText("Recent activity: -");

        if (totalUsersStatLabel != null) {
            totalUsersStatLabel.setText("-");
        }
        if (activeUsersStatLabel != null) {
            activeUsersStatLabel.setText("-");
        }
        if (inactiveUsersStatLabel != null) {
            inactiveUsersStatLabel.setText("-");
        }
        if (recentUsersStatLabel != null) {
            recentUsersStatLabel.setText("-");
        }

        Platform.runLater(this::playEntryAnimation);
    }

    public void setUser(User user) {
        String displayName = normalize(user == null ? null : user.getName());
        if (displayName == null) {
            displayName = normalize(user == null ? null : user.getEmail());
        }
        if (displayName == null) {
            displayName = "Admin";
        }

        welcomeLabel.setText("Welcome, " + displayName);
        roleLabel.setText("Role: " + toDisplayRole(user == null ? null : user.getRole()));
        refreshStats();
    }

    @FXML
    private void onOpenUsers() {
        AppNavigator.showUsers();
    }

    @FXML
    private void onOpenProgramManagement() {
        showInfo("Shortcut", "Program/Class management module shortcut is ready for wiring to the dedicated screen.");
    }

    @FXML
    private void onOpenModeration() {
        showInfo("Shortcut", "Moderation shortcut is ready for wiring to forum/content moderation screens.");
    }

    @FXML
    private void onOpenJobOfferManagement() {
        AppNavigator.showJobOffers();
    }

    @FXML
    private void onOpenProfile() {
        AppNavigator.showProfile();
    }

    private void refreshStats() {
        try {
            List<User> users = userService.searchUsers(null, null, null);
            long totalUsers = users.size();
            long inactiveUsers = users.stream().filter(user -> user.getIsActive() != (byte) 1).count();
            long activeUsers = totalUsers - inactiveUsers;

            long recentUsers = users.stream()
                    .filter(user -> user.getCreatedAt() != null)
                    .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                    .limit(5)
                    .count();

            usersCountLabel.setText("Users: " + totalUsers + " total | " + activeUsers + " active");
            pendingActionsLabel.setText("Pending actions: " + inactiveUsers + " inactive account(s)");
            recentActivityLabel.setText("Recent activity: " + Math.min(totalUsers, 10) + " latest user records loaded");

            if (totalUsersStatLabel != null) {
                totalUsersStatLabel.setText(String.valueOf(totalUsers));
            }
            if (activeUsersStatLabel != null) {
                activeUsersStatLabel.setText(String.valueOf(activeUsers));
            }
            if (inactiveUsersStatLabel != null) {
                inactiveUsersStatLabel.setText(String.valueOf(inactiveUsers));
            }
            if (recentUsersStatLabel != null) {
                recentUsersStatLabel.setText(String.valueOf(recentUsers));
            }
        } catch (Exception exception) {
            usersCountLabel.setText("Users: unavailable");
            pendingActionsLabel.setText("Pending actions: unavailable");
            recentActivityLabel.setText("Recent activity: unavailable");

            if (totalUsersStatLabel != null) {
                totalUsersStatLabel.setText("-");
            }
            if (activeUsersStatLabel != null) {
                activeUsersStatLabel.setText("-");
            }
            if (inactiveUsersStatLabel != null) {
                inactiveUsersStatLabel.setText("-");
            }
            if (recentUsersStatLabel != null) {
                recentUsersStatLabel.setText("-");
            }
        }
    }

    private void playEntryAnimation() {
        if (rootContainer == null) {
            return;
        }

        FadeTransition fadeTransition = new FadeTransition(Duration.millis(220), rootContainer);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);
        fadeTransition.play();

        int index = 0;
        for (Node node : rootContainer.lookupAll(".stagger-card")) {
            node.setOpacity(0.0);
            node.setTranslateY(14);

            FadeTransition cardFade = new FadeTransition(Duration.millis(220), node);
            cardFade.setFromValue(0.0);
            cardFade.setToValue(1.0);
            cardFade.setDelay(Duration.millis(70L * index));

            TranslateTransition cardSlide = new TranslateTransition(Duration.millis(220), node);
            cardSlide.setFromY(14);
            cardSlide.setToY(0);
            cardSlide.setDelay(Duration.millis(70L * index));

            cardFade.play();
            cardSlide.play();
            index++;
        }
    }

    private String toDisplayRole(String role) {
        if (role == null || role.isBlank()) {
            return "ADMIN";
        }

        String normalized = role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring("ROLE_".length());
        }

        return normalized;
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
}
