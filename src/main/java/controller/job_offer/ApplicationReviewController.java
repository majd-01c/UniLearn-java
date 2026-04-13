package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobOffer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import services.job_offer.ServiceJobApplication;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ResourceBundle;

public class ApplicationReviewController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private Label jobTitleLabel;

    @FXML
    private Label candidateLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label appliedDateLabel;

    @FXML
    private TextArea messageArea;

    @FXML
    private Label scoreLabel;

    @FXML
    private TextArea scoreBreakdownArea;

    @FXML
    private Button approveButton;

    @FXML
    private Button rejectButton;

    @FXML
    private Button backButton;

    private JobApplication application;
    private User currentUser;
    private ServiceJobApplication serviceJobApplication;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
    }

    public void setApplication(JobApplication app) {
        this.application = app;
        if (app != null) {
            displayApplicationDetails();
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        setupUI();
    }

    private void displayApplicationDetails() {
        JobOffer offer = application.getJobOffer();
        jobTitleLabel.setText(offer != null && offer.getTitle() != null ? offer.getTitle() : "Unknown");

        User candidate = application.getUser();
        candidateLabel.setText(candidate != null && candidate.getEmail() != null ? candidate.getEmail() : "Unknown");

        statusLabel.setText(application.getStatus() != null ? application.getStatus() : "Unknown");
        appliedDateLabel.setText(application.getCreatedAt() != null ? application.getCreatedAt().toString() : "Unknown");

        messageArea.setText(application.getMessage() != null ? application.getMessage() : "No message provided");
        messageArea.setEditable(false);
        messageArea.setWrapText(true);

        if (application.getScore() != null) {
            scoreLabel.setText("Score: " + application.getScore() + "/100");
            scoreLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        } else {
            scoreLabel.setText("Score: Not yet evaluated");
            scoreLabel.setStyle("-fx-font-size: 14;");
        }

        scoreBreakdownArea.setText(application.getScoreBreakdown() != null ? application.getScoreBreakdown() : "No breakdown available");
        scoreBreakdownArea.setEditable(false);
        scoreBreakdownArea.setWrapText(true);
    }

    private void setupUI() {
        if (currentUser == null || application == null) {
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            return;
        }

        JobOffer offer = application.getJobOffer();
        if (offer == null) {
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            return;
        }

        boolean isOfferOwner = offer.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        boolean canReview = isOfferOwner || isAdmin;
        boolean isSubmitted = "SUBMITTED".equals(application.getStatus());

        approveButton.setDisable(!(canReview && isSubmitted));
        rejectButton.setDisable(!(canReview && isSubmitted));
    }

    @FXML
    private void onApprove() {
        if (!canReview()) {
            showError("Error", "You don't have permission to review this application");
            return;
        }

        TextInputDialog scoreDialog = new TextInputDialog("85");
        scoreDialog.setTitle("Evaluate Application");
        scoreDialog.setHeaderText("Enter applicant score (0-100)");
        scoreDialog.setContentText("Score:");

        scoreDialog.showAndWait().ifPresent(score -> {
            try {
                int scoreValue = Integer.parseInt(score);
                if (scoreValue < 0 || scoreValue > 100) {
                    showError("Validation", "Score must be between 0 and 100");
                    return;
                }

                application.setStatus("ACCEPTED");
                application.setScore(scoreValue);
                application.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
                
                serviceJobApplication.update(application);
                showInfo("Success", "Application approved successfully");
                AppNavigator.showMyJobApplications();
            } catch (NumberFormatException e) {
                showError("Validation", "Please enter a valid number");
            }
        });
    }

    @FXML
    private void onReject() {
        if (!canReview()) {
            showError("Error", "You don't have permission to review this application");
            return;
        }

        TextInputDialog feedbackDialog = new TextInputDialog();
        feedbackDialog.setTitle("Reject Application");
        feedbackDialog.setHeaderText("Provide feedback (optional)");
        feedbackDialog.setContentText("Feedback:");

        feedbackDialog.showAndWait().ifPresent(feedback -> {
            try {
                application.setStatus("REJECTED");
                application.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
                if (!feedback.isEmpty()) {
                    application.setStatusMessage(feedback);
                }
                
                serviceJobApplication.update(application);
                showInfo("Success", "Application rejected");
                AppNavigator.showMyJobApplications();
            } catch (Exception e) {
                showError("Error", "Failed to reject application: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onBack() {
        AppNavigator.showMyJobApplications();
    }

    private boolean canReview() {
        JobOffer offer = application.getJobOffer();
        if (offer == null) return false;
        
        boolean isOfferOwner = offer.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        return isOfferOwner || isAdmin;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
