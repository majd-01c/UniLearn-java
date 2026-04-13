package controller.job_offer;

import entities.User;
import entities.job_offer.JobOffer;
import entities.job_offer.JobApplication;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import services.job_offer.ServiceJobOffer;
import services.job_offer.ServiceJobApplication;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ResourceBundle;

public class JobOfferDetailController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private Label titleLabel;

    @FXML
    private Label typeLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label locationLabel;

    @FXML
    private Label postedByLabel;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private TextArea requirementsArea;

    @FXML
    private Label requiredSkillsLabel;

    @FXML
    private Label preferredSkillsLabel;

    @FXML
    private Label educationLabel;

    @FXML
    private Label experienceLabel;

    @FXML
    private Button applyButton;

    @FXML
    private Button editButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button backButton;

    private JobOffer jobOffer;
    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private ServiceJobApplication serviceJobApplication;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobOffer = new ServiceJobOffer();
        serviceJobApplication = new ServiceJobApplication();
    }

    public void setJobOffer(JobOffer offer) {
        this.jobOffer = offer;
        if (offer != null) {
            displayOfferDetails();
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        setupUI();
    }

    private void displayOfferDetails() {
        titleLabel.setText(jobOffer.getTitle() != null ? jobOffer.getTitle() : "Untitled");
        typeLabel.setText(jobOffer.getType() != null ? jobOffer.getType() : "N/A");
        statusLabel.setText(jobOffer.getStatus() != null ? jobOffer.getStatus() : "N/A");
        locationLabel.setText(jobOffer.getLocation() != null ? "📍 " + jobOffer.getLocation() : "Location not specified");
        
        String postedBy = jobOffer.getUser() != null && jobOffer.getUser().getEmail() != null 
            ? jobOffer.getUser().getEmail() 
            : "Unknown";
        postedByLabel.setText("Posted by: " + postedBy);

        descriptionArea.setText(jobOffer.getDescription() != null ? jobOffer.getDescription() : "");
        descriptionArea.setEditable(false);
        descriptionArea.setWrapText(true);

        requirementsArea.setText(jobOffer.getRequirements() != null ? jobOffer.getRequirements() : "");
        requirementsArea.setEditable(false);
        requirementsArea.setWrapText(true);

        requiredSkillsLabel.setText(jobOffer.getRequiredSkills() != null ? jobOffer.getRequiredSkills() : "Not specified");
        preferredSkillsLabel.setText(jobOffer.getPreferredSkills() != null ? jobOffer.getPreferredSkills() : "Not specified");
        educationLabel.setText(jobOffer.getMinEducation() != null ? jobOffer.getMinEducation() : "Not specified");
        experienceLabel.setText(jobOffer.getMinExperienceYears() != null 
            ? jobOffer.getMinExperienceYears() + " years" 
            : "Not specified");
    }

    private void setupUI() {
        if (currentUser == null || jobOffer == null) {
            return;
        }

        boolean isOwner = jobOffer.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        boolean isStudent = RoleGuard.isStudent(currentUser);
        boolean isActive = "ACTIVE".equals(jobOffer.getStatus());

        // Student can apply if offer is ACTIVE and hasn't already applied
        boolean canApply = isStudent && isActive && !hasApplied();
        applyButton.setDisable(!canApply);

        // Only owner or admin can edit
        editButton.setDisable(!(isOwner || isAdmin));

        // Only owner or admin can delete
        deleteButton.setDisable(!(isOwner || isAdmin));
    }

    private boolean hasApplied() {
        // TODO: Check if current user has already applied to this offer
        return false;
    }

    @FXML
    private void onApply() {
        if (currentUser == null || jobOffer == null) {
            showError("Error", "Missing required data");
            return;
        }

        // Create job application
        JobApplication application = new JobApplication();
        application.setUser(currentUser);
        application.setJobOffer(jobOffer);
        application.setStatus("SUBMITTED");
        application.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));

        // TODO: Open application form dialog to collect CV and motivation letter
        showInfo("Success", "Application submitted successfully!");
        setupUI();
    }

    @FXML
    private void onEdit() {
        if (!canEdit()) {
            showError("Error", "You don't have permission to edit this offer");
            return;
        }
        AppNavigator.showJobOfferForm(jobOffer);
    }

    @FXML
    private void onDelete() {
        if (!canEdit()) {
            showError("Error", "You don't have permission to delete this offer");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Delete Job Offer");
        confirmDialog.setContentText("Are you sure you want to delete this job offer?");
        
        if (confirmDialog.showAndWait().isPresent() && confirmDialog.getResult().getButtonData().isDefaultButton()) {
            try {
                serviceJobOffer.delete(jobOffer);
                showInfo("Success", "Job offer deleted successfully");
                AppNavigator.showJobOffers();
            } catch (Exception e) {
                showError("Error", "Failed to delete job offer: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onBack() {
        AppNavigator.showJobOffers();
    }

    private boolean canEdit() {
        boolean isOwner = jobOffer.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        return isOwner || isAdmin;
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
