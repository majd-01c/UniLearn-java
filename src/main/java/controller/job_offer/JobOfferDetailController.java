package controller.job_offer;

import entities.User;
import entities.job_offer.JobOffer;
import entities.job_offer.JobApplication;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import services.job_offer.ServiceJobOffer;
import services.job_offer.ServiceJobApplication;
import util.AppNavigator;
import util.RoleGuard;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private Label descriptionLabel;

    @FXML
    private Label requirementsLabel;

    @FXML
    private Label requiredSkillsLabel;

    @FXML
    private Label preferredSkillsLabel;

    @FXML
    private Label educationLabel;

    @FXML
    private Label experienceLabel;

    @FXML
    private Label languagesLabel;

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

        descriptionLabel.setText(jobOffer.getDescription() != null ? jobOffer.getDescription() : "No description provided");
        requirementsLabel.setText(jobOffer.getRequirements() != null ? jobOffer.getRequirements() : "No requirements provided");

        requiredSkillsLabel.setText(jobOffer.getRequiredSkills() != null ? jobOffer.getRequiredSkills() : "Not specified");
        preferredSkillsLabel.setText(jobOffer.getPreferredSkills() != null ? jobOffer.getPreferredSkills() : "Not specified");
        educationLabel.setText(jobOffer.getMinEducation() != null ? jobOffer.getMinEducation() : "Not specified");
        experienceLabel.setText(jobOffer.getMinExperienceYears() != null 
            ? jobOffer.getMinExperienceYears() + " years" 
            : "Not specified");
        languagesLabel.setText(jobOffer.getRequiredLanguages() != null ? jobOffer.getRequiredLanguages() : "Not specified");
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

        // Hide edit/delete buttons in detail view
        editButton.setDisable(true);
        editButton.setVisible(false);
        editButton.setManaged(false);

        deleteButton.setDisable(true);
        deleteButton.setVisible(false);
        deleteButton.setManaged(false);
    }

    private boolean hasApplied() {
        if (currentUser == null || jobOffer == null) {
            return false;
        }

        try {
            List<JobApplication> applications = serviceJobApplication.getALL();
            return applications.stream().anyMatch(application ->
                    application.getUser() != null
                            && application.getJobOffer() != null
                            && application.getUser().getId() == currentUser.getId()
                            && application.getJobOffer().getId() == jobOffer.getId());
        } catch (Exception exception) {
            return false;
        }
    }

    @FXML
    private void onApply() {
        if (currentUser == null || jobOffer == null) {
            showError("Error", "Missing required data");
            return;
        }

        if (hasApplied()) {
            showError("Already applied", "You have already applied to this offer.");
            setupUI();
            return;
        }

        Optional<ApplicationFormData> formData = showApplicationDialog();
        if (formData.isEmpty()) {
            return;
        }

        try {
            ApplicationFormData data = formData.get();
            String managedCvPath = persistCvFile(data.cvFileName());

            JobApplication application = new JobApplication();
            application.setUser(currentUser);
            application.setJobOffer(jobOffer);
            application.setMessage(data.motivationLetter());
            application.setCvFileName(managedCvPath);
            application.setStatus("SUBMITTED");
            application.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
            application.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
            application.setStatusNotified((byte) 0);

            serviceJobApplication.add(application);

            showInfo("Success", "Application submitted successfully!");
            setupUI();
        } catch (Exception exception) {
            String errorMessage = exception.getCause() != null && exception.getCause().getMessage() != null
                    ? exception.getCause().getMessage()
                    : exception.getMessage();
            showError("Error", "Failed to submit application: " + errorMessage);
        }
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

    private Optional<ApplicationFormData> showApplicationDialog() {
        Dialog<ApplicationFormData> dialog = new Dialog<>();
        dialog.setTitle("Apply to Job Offer");
        dialog.setHeaderText("Upload your CV and add a motivation letter");

        ButtonType submitButtonType = new ButtonType("Submit", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(submitButtonType, ButtonType.CANCEL);

        TextField cvPathField = new TextField();
        cvPathField.setPromptText("Select your CV file (.pdf, .doc, .docx)");
        cvPathField.setEditable(false);

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(event -> {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Select CV File");
                chooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("CV Files (*.pdf, *.doc, *.docx)", "*.pdf", "*.doc", "*.docx"),
                        new FileChooser.ExtensionFilter("All Files", "*.*")
                );

                Stage owner = dialog.getDialogPane().getScene() != null
                        ? (Stage) dialog.getDialogPane().getScene().getWindow()
                        : null;

                File selected = chooser.showOpenDialog(owner);
                if (selected != null) {
                    cvPathField.setText(selected.getAbsolutePath());
                }
            } catch (Exception exception) {
                showError("File Picker Error", "Unable to open file browser: " + exception.getMessage());
            }
        });

        TextArea motivationArea = new TextArea();
        motivationArea.setPromptText("Write your motivation letter...");
        motivationArea.setWrapText(true);
        motivationArea.setPrefRowCount(8);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("CV File *"), 0, 0);
        grid.add(cvPathField, 0, 1);
        grid.add(browseButton, 1, 1);

        grid.add(new Label("Motivation Letter *"), 0, 2);
        grid.add(motivationArea, 0, 3, 2, 1);

        dialog.getDialogPane().setContent(grid);

        javafx.scene.Node submitButton = dialog.getDialogPane().lookupButton(submitButtonType);
        submitButton.setDisable(true);

        Runnable validator = () -> {
            boolean hasCv = cvPathField.getText() != null && !cvPathField.getText().trim().isEmpty();
            boolean hasMotivation = motivationArea.getText() != null && !motivationArea.getText().trim().isEmpty();
            submitButton.setDisable(!(hasCv && hasMotivation));
        };

        cvPathField.textProperty().addListener((obs, oldVal, newVal) -> validator.run());
        motivationArea.textProperty().addListener((obs, oldVal, newVal) -> validator.run());
        validator.run();

        dialog.setResultConverter(buttonType -> {
            if (buttonType == submitButtonType) {
                String path = cvPathField.getText().trim();
                String motivation = motivationArea.getText().trim();
                return new ApplicationFormData(path, motivation);
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private record ApplicationFormData(String cvFileName, String motivationLetter) {
    }

    private String persistCvFile(String sourcePath) throws IOException {
        File source = new File(sourcePath);
        if (!source.exists()) {
            throw new IOException("Selected CV file does not exist: " + sourcePath);
        }

        String sanitizedName = source.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String targetName = System.currentTimeMillis() + "_" + sanitizedName;

        Path targetDir = Path.of(System.getProperty("user.dir"), "uploads", "cvs");
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(targetName);
        Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toAbsolutePath().toString();
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
