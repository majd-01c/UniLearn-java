package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import service.job_offer.GeminiApplicationFeedbackService;
import services.job_offer.ServiceJobApplication;
import services.job_offer.ServiceJobOffer;
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
    private ServiceJobOffer serviceJobOffer;
    private GeminiApplicationFeedbackService aiFeedbackService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        serviceJobOffer = new ServiceJobOffer();
        aiFeedbackService = new GeminiApplicationFeedbackService();

        // Ensure reject button keeps themed style even if FXML class parsing is inconsistent.
        if (rejectButton != null) {
            if (!rejectButton.getStyleClass().contains("ghost-button")) {
                rejectButton.getStyleClass().add("ghost-button");
            }
            if (!rejectButton.getStyleClass().contains("job-offer-danger-button")) {
                rejectButton.getStyleClass().add("job-offer-danger-button");
            }
        }
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
            if (!scoreLabel.getStyleClass().contains("job-offer-score-highlight")) {
                scoreLabel.getStyleClass().add("job-offer-score-highlight");
            }
        } else {
            scoreLabel.setText("Score: Not yet evaluated");
            scoreLabel.getStyleClass().remove("job-offer-score-highlight");
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

        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        boolean canReview = isAdmin || isCurrentUserOfferOwner(offer);

        approveButton.setDisable(!canReview);
        rejectButton.setDisable(!canReview);
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
                application.setStatusNotified((byte) 0);
                application.setStatusNotifiedAt(null);
                String feedback = promptForDecisionFeedback(JobApplicationStatus.ACCEPTED, application.getStatusMessage());
                if (feedback == null) {
                    return;
                }
                application.setStatusMessage(feedback);

                serviceJobApplication.update(application);
                showInfo("Success", "Application approved successfully");
                AppNavigator.showPartnerApplications();
            } catch (NumberFormatException e) {
                showError("Validation", "Please enter a valid number");
            } catch (Exception e) {
                showError("Error", "Failed to approve application: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onReject() {
        if (!canReview()) {
            showError("Error", "You don't have permission to review this application");
            return;
        }

        try {
            application.setStatus("REJECTED");
            application.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
            application.setStatusNotified((byte) 0);
            application.setStatusNotifiedAt(null);
            String feedback = promptForDecisionFeedback(JobApplicationStatus.REJECTED, application.getStatusMessage());
            if (feedback == null) {
                return;
            }
            application.setStatusMessage(feedback);

            serviceJobApplication.update(application);
            showInfo("Success", "Application rejected");
            AppNavigator.showPartnerApplications();
        } catch (Exception e) {
            showError("Error", "Failed to reject application: " + e.getMessage());
        }
    }

    @FXML
    private void onBack() {
        AppNavigator.showPartnerApplications();
    }

    private boolean canReview() {
        JobOffer offer = application.getJobOffer();
        if (offer == null || currentUser == null) {
            return false;
        }

        return RoleGuard.isAdmin(currentUser) || isCurrentUserOfferOwner(offer);
    }

    private boolean isCurrentUserOfferOwner(JobOffer offerReference) {
        if (offerReference == null || currentUser == null) {
            return false;
        }

        try {
            return serviceJobOffer.getALL().stream()
                    .anyMatch(offer -> offer != null
                            && offer.getId() == offerReference.getId()
                            && offer.getUser() != null
                            && offer.getUser().getId().equals(currentUser.getId()));
        } catch (Exception exception) {
            return false;
        }
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

    private String promptForDecisionFeedback(JobApplicationStatus decision, String existingMessage) {
        String draftMessage = resolveDecisionFeedback(decision, existingMessage);

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(decision == JobApplicationStatus.ACCEPTED ? "Approval Feedback" : "Rejection Feedback");
        dialog.setHeaderText("Review the message that will be sent to the student.");

        ButtonType saveButtonType = new ButtonType("Save Message", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextArea feedbackArea = new TextArea(draftMessage);
        feedbackArea.setWrapText(true);
        feedbackArea.setPrefRowCount(10);
        feedbackArea.setPrefColumnCount(60);
        dialog.getDialogPane().setContent(feedbackArea);

        dialog.setResultConverter(buttonType -> buttonType == saveButtonType ? feedbackArea.getText() : null);

        return dialog.showAndWait()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .orElse(null);
    }

    private String resolveDecisionFeedback(JobApplicationStatus decision, String existingMessage) {
        String trimmedExisting = existingMessage == null ? "" : existingMessage.trim();
        if (!trimmedExisting.isEmpty()) {
            return trimmedExisting;
        }

        return aiFeedbackService.generateFeedback(application, decision);
    }
}
