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
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.util.Duration;

public class FrontOfficeHomeController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label nextClassLabel;

    @FXML
    private Label notificationsLabel;

    @FXML
    private Label tasksLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        welcomeLabel.setText("Welcome to FrontOffice");
        roleLabel.setText("Role: USER");
        nextClassLabel.setText("Next class/event: not scheduled");
        notificationsLabel.setText("Notifications: 0 unread");
        tasksLabel.setText("Tasks: no pending tasks");

        Platform.runLater(this::playEntryAnimation);
    }

    public void setUser(User user) {
        String displayName = normalize(user == null ? null : user.getName());
        if (displayName == null) {
            displayName = normalize(user == null ? null : user.getEmail());
        }
        if (displayName == null) {
            displayName = "Learner";
        }

        String role = toDisplayRole(user == null ? null : user.getRole());
        welcomeLabel.setText("Welcome, " + displayName);
        roleLabel.setText("Role: " + role);

        updateWidgetsByRole(role);
    }

    @FXML
    private void onOpenCourses() {
        showInfo("Courses", "Courses module shortcut is ready for wiring.");
    }

    @FXML
    private void onOpenSchedule() {
        showInfo("Schedule", "Schedule module shortcut is ready for wiring.");
    }

    @FXML
    private void onOpenMeetings() {
        showInfo("Meetings", "Meetings module shortcut is ready for wiring.");
    }

    @FXML
    private void onOpenForum() {
        showInfo("Forum", "Forum module shortcut is ready for wiring.");
    }

    @FXML
    private void onOpenOpportunities() {
        showInfo("Opportunities", "Opportunities module shortcut is ready for wiring.");
    }

    @FXML
    private void onOpenProfile() {
        AppNavigator.showProfile();
    }

    @FXML
    private void onChangePassword() {
        AppNavigator.showChangePassword();
    }

    private void updateWidgetsByRole(String role) {
        if ("TEACHER".equals(role) || "TRAINER".equals(role)) {
            nextClassLabel.setText("Next class/event: prepare upcoming teaching session");
            notificationsLabel.setText("Notifications: class updates and learner messages");
            tasksLabel.setText("Tasks: publish resources and validate assessments");
            return;
        }

        if ("STUDENT".equals(role)) {
            nextClassLabel.setText("Next class/event: check your timetable for today");
            notificationsLabel.setText("Notifications: assignment reminders and announcements");
            tasksLabel.setText("Tasks: complete pending exercises and forum follow-ups");
            return;
        }

        if ("PARTNER".equals(role)) {
            nextClassLabel.setText("Next class/event: partnership coordination and planning");
            notificationsLabel.setText("Notifications: partner updates and opportunities");
            tasksLabel.setText("Tasks: review active collaborations");
            return;
        }

        nextClassLabel.setText("Next class/event: not scheduled");
        notificationsLabel.setText("Notifications: 0 unread");
        tasksLabel.setText("Tasks: no pending tasks");
    }

    private String toDisplayRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
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
}
