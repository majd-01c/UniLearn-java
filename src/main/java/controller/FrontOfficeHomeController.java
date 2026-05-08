package controller;

import entities.User;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import util.AppNavigator;

import java.net.URL;
import java.util.LinkedHashSet;
import java.util.ResourceBundle;
import java.util.Set;
import javafx.util.Duration;

public class FrontOfficeHomeController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label roleLabel;

    @FXML
    private Label heroSubtitleLabel;

    @FXML
    private Label focusHeadingLabel;

    @FXML
    private Label nextClassLabel;

    @FXML
    private Label nextClassTileLabel;

    @FXML
    private Label notificationsLabel;

    @FXML
    private Label notificationsTileLabel;

    @FXML
    private Label tasksLabel;

    @FXML
    private Label learningTitleLabel;

    @FXML
    private Label learningDescriptionLabel;

    @FXML
    private Label evaluationDescriptionLabel;

    @FXML
    private Label meetingsTitleLabel;

    @FXML
    private Label meetingsDescriptionLabel;

    @FXML
    private Label communityDescriptionLabel;

    @FXML
    private Label opportunitiesTitleLabel;

    @FXML
    private Label opportunitiesDescriptionLabel;

    @FXML
    private Button primaryActionButton;

    @FXML
    private Button coursesButton;

    @FXML
    private Button meetingsButton;

    private String currentRole = "USER";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        welcomeLabel.setText("Welcome to FrontOffice");
        roleLabel.setText("USER");
        setStatusLabels(
                "No class or event is scheduled yet.",
                "No unread notifications.",
                "No pending tasks.");
        updateCopyByRole(currentRole);

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
        currentRole = role;
        welcomeLabel.setText("Welcome, " + displayName);
        roleLabel.setText(formatRoleLabel(role));

        updateCopyByRole(role);
        updateWidgetsByRole(role);
    }

    @FXML
    private void onOpenCourses() {
        switch (currentRole) {
            case "STUDENT" -> AppNavigator.showStudentLearning();
            case "TEACHER", "TRAINER" -> AppNavigator.showTeacherClasses();
            case "PARTNER", "BUSINESS_PARTNER" -> AppNavigator.showJobOffers();
            default -> AppNavigator.showJobOffers();
        }
    }

    @FXML
    private void onOpenEvaluation() {
        AppNavigator.navigateTo("EVALUATION");
    }

    @FXML
    private void onOpenSchedule() {
        AppNavigator.navigateTo("EVALUATION_SCHEDULE");
    }

    @FXML
    private void onOpenMeetings() {
        switch (currentRole) {
            case "STUDENT" -> AppNavigator.showStudentLearning();
            case "TEACHER", "TRAINER" -> AppNavigator.showTeacherClasses();
            default -> AppNavigator.showIARooms();
        }
    }

    @FXML
    private void onOpenForum() {
        AppNavigator.showForum();
    }

    @FXML
    private void onOpenOpportunities() {
        AppNavigator.showJobOffers();
    }

    @FXML
    private void onOpenRooms() {
        AppNavigator.showIARooms();
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
            setStatusLabels(
                    "Prepare the next teaching session and review assigned classes.",
                    "Class updates and learner messages need regular review.",
                    "Publish resources and validate pending assessments.");
            return;
        }

        if ("STUDENT".equals(role)) {
            setStatusLabels(
                    "Check today's timetable before starting your next class.",
                    "Assignment reminders and announcements appear here.",
                    "Complete pending exercises and forum follow-ups.");
            return;
        }

        if (isPartnerRole(role)) {
            setStatusLabels(
                    "Review active opportunities and candidate activity.",
                    "Partner updates and opportunity changes appear here.",
                    "Review active collaborations and applications.");
            return;
        }

        setStatusLabels(
                "No class or event is scheduled yet.",
                "No unread notifications.",
                "No pending tasks.");
    }

    private void updateCopyByRole(String role) {
        if ("TEACHER".equals(role) || "TRAINER".equals(role)) {
            setHeroCopy("Teaching, evaluation, community, room planning, and learner support in one workspace.");
            setLearningCopy("Teaching Workspace", "Open assigned classes, courses, content, and learner progress.");
            setEvaluationCopy("Build assessments, review results, and track schedule items.");
            setMeetingsCopy("Class Meetings", "Manage class sessions and room availability.");
            setCommunityCopy("Answer learner questions and follow discussions.");
            setOpportunityCopy("Opportunities", "Review job offers, partnerships, and applications.");
            setButtonText("Open My Classes", "Open My Classes", "Manage Meetings");
            return;
        }

        if ("STUDENT".equals(role)) {
            setHeroCopy("Classes, quizzes, timetable, discussions, and career opportunities in one workspace.");
            setLearningCopy("My Learning", "Continue enrolled classes, courses, and learning resources.");
            setEvaluationCopy("Review quizzes, grades, documents, and timetable items.");
            setMeetingsCopy("Meetings", "Find class sessions from your learning workspace and room tools.");
            setCommunityCopy("Ask questions, browse discussions, and share knowledge.");
            setOpportunityCopy("Opportunities", "Browse job offers and track career opportunities.");
            setButtonText("Continue Learning", "Open My Learning", "Find Meetings");
            return;
        }

        if (isPartnerRole(role)) {
            setHeroCopy("Opportunities, applications, community updates, and account tools in one workspace.");
            setLearningCopy("Opportunity Workspace", "Open job offers and partnership collaboration tools.");
            setEvaluationCopy("Open the evaluation workspace when academic coordination is needed.");
            setMeetingsCopy("Rooms", "Check room availability and coordination tools.");
            setCommunityCopy("Follow community discussions and partner updates.");
            setOpportunityCopy("Partner Opportunities", "Manage opportunities, applications, and candidate activity.");
            setButtonText("Open Opportunities", "Open Job Offers", "Open IArooms");
            return;
        }

        setHeroCopy("Learning, evaluation, community, opportunities, and account tools in one place.");
        setLearningCopy("Learning", "Continue classes, courses, and learning resources.");
        setEvaluationCopy("Review grades, quizzes, documents, and timetable items.");
        setMeetingsCopy("Meetings", "Access class sessions and room availability.");
        setCommunityCopy("Ask questions, browse discussions, and share knowledge.");
        setOpportunityCopy("Opportunities", "Explore job offers, applications, and partner opportunities.");
        setButtonText("Continue Work", "Open Learning", "Open Meetings");
    }

    private void setHeroCopy(String text) {
        if (heroSubtitleLabel != null) {
            heroSubtitleLabel.setText(text);
        }
    }

    private void setLearningCopy(String title, String description) {
        if (learningTitleLabel != null) {
            learningTitleLabel.setText(title);
        }
        if (learningDescriptionLabel != null) {
            learningDescriptionLabel.setText(description);
        }
    }

    private void setMeetingsCopy(String title, String description) {
        if (meetingsTitleLabel != null) {
            meetingsTitleLabel.setText(title);
        }
        if (meetingsDescriptionLabel != null) {
            meetingsDescriptionLabel.setText(description);
        }
    }

    private void setEvaluationCopy(String description) {
        if (evaluationDescriptionLabel != null) {
            evaluationDescriptionLabel.setText(description);
        }
    }

    private void setCommunityCopy(String description) {
        if (communityDescriptionLabel != null) {
            communityDescriptionLabel.setText(description);
        }
    }

    private void setOpportunityCopy(String title, String description) {
        if (opportunitiesTitleLabel != null) {
            opportunitiesTitleLabel.setText(title);
        }
        if (opportunitiesDescriptionLabel != null) {
            opportunitiesDescriptionLabel.setText(description);
        }
    }

    private void setButtonText(String primaryText, String coursesText, String meetingsText) {
        if (primaryActionButton != null) {
            primaryActionButton.setText(primaryText);
        }
        if (coursesButton != null) {
            coursesButton.setText(coursesText);
        }
        if (meetingsButton != null) {
            meetingsButton.setText(meetingsText);
        }
    }

    private void setStatusLabels(String nextClass, String notifications, String tasks) {
        if (nextClassLabel != null) {
            nextClassLabel.setText(nextClass);
        }
        if (nextClassTileLabel != null) {
            nextClassTileLabel.setText(nextClass);
        }
        if (notificationsLabel != null) {
            notificationsLabel.setText(notifications);
        }
        if (notificationsTileLabel != null) {
            notificationsTileLabel.setText(notifications);
        }
        if (tasksLabel != null) {
            tasksLabel.setText(tasks);
        }
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

    private boolean isPartnerRole(String role) {
        return "PARTNER".equals(role) || "BUSINESS_PARTNER".equals(role);
    }

    private String formatRoleLabel(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        return role.replace('_', ' ');
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        for (Node node : animatedNodes()) {
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

    private Set<Node> animatedNodes() {
        Set<Node> nodes = new LinkedHashSet<>();
        nodes.addAll(rootContainer.lookupAll(".frontoffice-hero"));
        nodes.addAll(rootContainer.lookupAll(".frontoffice-insight-card"));
        nodes.addAll(rootContainer.lookupAll(".frontoffice-action-card"));
        return nodes;
    }
}
