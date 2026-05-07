package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobOffer;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import service.job_offer.ApplicationDocumentStorageService;
import service.job_offer.GeminiApplicationFeedbackService;
import services.job_offer.ServiceJobApplication;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.io.File;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class JobOfferDetailController implements Initializable {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    @FXML
    private VBox applicationSection;

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
    private Label educationLabel;

    @FXML
    private Label experienceLabel;

    @FXML
    private Label mapStatusLabel;

    @FXML
    private Label mapUnavailableLabel;

    @FXML
    private Label createdAtLabel;

    @FXML
    private Label publishedAtLabel;

    @FXML
    private Label expiresAtLabel;

    @FXML
    private Label applicationStatusLabel;

    @FXML
    private FlowPane requiredSkillsPane;

    @FXML
    private FlowPane preferredSkillsPane;

    @FXML
    private FlowPane languagesPane;

    @FXML
    private TextArea motivationLetterArea;

    @FXML
    private TextField cvPathField;

    @FXML
    private Button browseCvButton;

    @FXML
    private Button generateMotivationButton;

    @FXML
    private Button applyButton;

    @FXML
    private Button editButton;

    @FXML
    private Button deleteButton;

    @FXML
    private WebView locationMapView;

    private JobOffer jobOffer;
    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private ServiceJobApplication serviceJobApplication;
    private ApplicationDocumentStorageService documentStorageService;
    private GeminiApplicationFeedbackService aiFeedbackService;
    private boolean mapLoaded;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobOffer = new ServiceJobOffer();
        serviceJobApplication = new ServiceJobApplication();
        documentStorageService = new ApplicationDocumentStorageService();
        aiFeedbackService = new GeminiApplicationFeedbackService();
        setupMap();
    }

    public void setJobOffer(JobOffer offer) {
        this.jobOffer = offer;
        if (offer != null) {
            displayOfferDetails();
            refreshMap();
        }
        setupUI();
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        setupUI();
    }

    private void displayOfferDetails() {
        titleLabel.setText(safe(jobOffer.getTitle(), "Untitled"));
        typeLabel.setText(safe(jobOffer.getType(), "N/A"));
        statusLabel.setText(safe(jobOffer.getStatus(), "N/A"));
        applyStatusStyle(statusLabel, jobOffer.getStatus());

        String rawLocation = trimToNull(jobOffer.getLocation());
        locationLabel.setText(rawLocation != null ? rawLocation : "Location not specified");

        String postedBy = jobOffer.getUser() != null && jobOffer.getUser().getEmail() != null
                ? jobOffer.getUser().getEmail()
                : "Unknown";
        postedByLabel.setText(postedBy);

        descriptionLabel.setText(safe(jobOffer.getDescription(), "No description provided"));
        requirementsLabel.setText(safe(jobOffer.getRequirements(), "No requirements provided"));
        educationLabel.setText(safe(jobOffer.getMinEducation(), "Not specified"));
        experienceLabel.setText(jobOffer.getMinExperienceYears() != null
                ? jobOffer.getMinExperienceYears() + " years"
                : "Not specified");

        populateChips(requiredSkillsPane, splitItems(jobOffer.getRequiredSkills()), "job-offer-detail-tag");
        populateChips(preferredSkillsPane, splitItems(jobOffer.getPreferredSkills()), "job-offer-detail-tag", "job-offer-detail-tag-alt");
        populateChips(languagesPane, splitItems(jobOffer.getRequiredLanguages()), "job-offer-detail-tag", "job-offer-detail-language-tag");

        createdAtLabel.setText("Created: " + formatTimestamp(jobOffer.getCreatedAt()));
        publishedAtLabel.setText("Published: " + formatTimestamp(jobOffer.getPublishedAt()));
        expiresAtLabel.setText("Expires: " + formatTimestamp(jobOffer.getExpiresAt()));
    }

    private void setupUI() {
        if (applyButton == null || editButton == null || deleteButton == null || applicationSection == null) {
            return;
        }

        boolean hasOffer = jobOffer != null;
        boolean hasUser = currentUser != null;
        boolean isOwner = hasOffer && hasUser && jobOffer.getUser() != null
                && jobOffer.getUser().getId() != null
                && jobOffer.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = hasUser && RoleGuard.isAdmin(currentUser);
        boolean isStudent = hasUser && RoleGuard.isStudent(currentUser);
        boolean isActive = hasOffer && "ACTIVE".equalsIgnoreCase(jobOffer.getStatus());
        boolean alreadyApplied = hasOffer && hasUser && hasApplied();

        boolean showApplication = isStudent;
        applicationSection.setVisible(showApplication);
        applicationSection.setManaged(showApplication);

        editButton.setDisable(!(isOwner || isAdmin));
        editButton.setVisible(isOwner || isAdmin);
        editButton.setManaged(isOwner || isAdmin);

        deleteButton.setDisable(!(isOwner || isAdmin));
        deleteButton.setVisible(isOwner || isAdmin);
        deleteButton.setManaged(isOwner || isAdmin);

        boolean canApply = showApplication && isActive && !alreadyApplied;
        applyButton.setDisable(!canApply);
        browseCvButton.setDisable(!canApply);
        if (generateMotivationButton != null) {
            generateMotivationButton.setDisable(!canApply);
        }
        motivationLetterArea.setDisable(!canApply);
        cvPathField.setDisable(!canApply);

        if (!showApplication) {
            return;
        }

        if (!isActive) {
            applicationStatusLabel.setText("This offer is not active, so applications are currently closed.");
        } else if (alreadyApplied) {
            applicationStatusLabel.setText("You already applied to this offer. Your application has been recorded.");
        } else {
            applicationStatusLabel.setText("Tell the employer why this opportunity matches your profile.");
        }
    }

    @FXML
    private void onGenerateMotivationLetter() {
        if (currentUser == null || jobOffer == null) {
            showError("AI generation unavailable", "Student or job offer data is missing.");
            return;
        }

        if (generateMotivationButton != null) {
            generateMotivationButton.setDisable(true);
            generateMotivationButton.setText("Generating...");
        }
        applicationStatusLabel.setText("Generating a motivation letter based on the job offer and your profile...");

        Thread thread = new Thread(() -> {
            try {
                String generatedLetter = aiFeedbackService.generateMotivationLetter(jobOffer, currentUser);
                Platform.runLater(() -> {
                    motivationLetterArea.setText(generatedLetter);
                    applicationStatusLabel.setText("AI generated a motivation letter. Review it and edit anything you want before applying.");
                    if (generateMotivationButton != null) {
                        generateMotivationButton.setDisable(false);
                        generateMotivationButton.setText("Generate with AI");
                    }
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    applicationStatusLabel.setText("AI generation failed. You can still write your own message.");
                    if (generateMotivationButton != null) {
                        generateMotivationButton.setDisable(false);
                        generateMotivationButton.setText("Generate with AI");
                    }
                    showError("AI generation failed", exception.getMessage());
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
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
                            && application.getUser().getId() != null
                            && application.getJobOffer().getId() == jobOffer.getId()
                            && application.getUser().getId().equals(currentUser.getId()));
        } catch (Exception exception) {
            return false;
        }
    }

    @FXML
    private void onBrowseCv() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select CV File");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("CV Files (*.pdf, *.doc, *.docx)", "*.pdf", "*.doc", "*.docx"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            Stage owner = cvPathField != null && cvPathField.getScene() != null
                    ? (Stage) cvPathField.getScene().getWindow()
                    : null;

            File selected = chooser.showOpenDialog(owner);
            if (selected != null) {
                cvPathField.setText(selected.getAbsolutePath());
            }
        } catch (Exception exception) {
            showError("File Picker Error", "Unable to open file browser: " + exception.getMessage());
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

        String sourcePath = trimToNull(cvPathField.getText());
        String motivationLetter = trimToNull(motivationLetterArea.getText());

        if (sourcePath == null) {
            showError("Validation Error", "Please choose a CV file before applying.");
            return;
        }
        if (motivationLetter == null) {
            showError("Validation Error", "Please write a cover letter or message before applying.");
            return;
        }

        try {
            String managedCvPath = documentStorageService.storeCv(
                    new File(sourcePath),
                    currentUser.getId(),
                    jobOffer.getId()
            );

            JobApplication application = new JobApplication();
            application.setUser(currentUser);
            application.setJobOffer(jobOffer);
            application.setMessage(motivationLetter);
            application.setCvFileName(managedCvPath);
            application.setStatus("SUBMITTED");
            application.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
            application.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
            application.setStatusNotified((byte) 0);

            serviceJobApplication.add(application);

            motivationLetterArea.clear();
            cvPathField.clear();
            showInfo("Success", "Application submitted successfully.");
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
            showError("Error", "You do not have permission to edit this offer.");
            return;
        }
        AppNavigator.showJobOfferForm(jobOffer);
    }

    @FXML
    private void onDelete() {
        if (!canEdit()) {
            showError("Error", "You do not have permission to delete this offer.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Delete Job Offer");
        confirmDialog.setContentText("Are you sure you want to delete this job offer?");

        if (confirmDialog.showAndWait().isPresent() && confirmDialog.getResult().getButtonData().isDefaultButton()) {
            try {
                serviceJobOffer.delete(jobOffer);
                showInfo("Success", "Job offer deleted successfully.");
                AppNavigator.showJobOffers();
            } catch (Exception exception) {
                showError("Error", "Failed to delete job offer: " + exception.getMessage());
            }
        }
    }

    @FXML
    private void onBack() {
        AppNavigator.showJobOffers();
    }

    private boolean canEdit() {
        if (jobOffer == null || currentUser == null || jobOffer.getUser() == null || jobOffer.getUser().getId() == null) {
            return false;
        }
        boolean isOwner = jobOffer.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        return isOwner || isAdmin;
    }

    private void setupMap() {
        if (locationMapView == null) {
            return;
        }

        WebEngine engine = locationMapView.getEngine();
        engine.setJavaScriptEnabled(true);

        URL mapUrl = getClass().getResource("/view/job_offer/job-offer-detail-map.html");
        if (mapUrl == null) {
            if (mapStatusLabel != null) {
                mapStatusLabel.setText("Map resource missing");
            }
            return;
        }

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapLoaded = true;
                refreshMap();
            }
        });

        engine.load(mapUrl.toExternalForm());
    }

    private void refreshMap() {
        if (!mapLoaded || jobOffer == null || locationMapView == null) {
            return;
        }

        String location = trimToNull(jobOffer.getLocation());
        boolean hasLocation = location != null;

        if (mapUnavailableLabel != null) {
            mapUnavailableLabel.setVisible(!hasLocation);
            mapUnavailableLabel.setManaged(!hasLocation);
        }
        if (locationMapView != null) {
            locationMapView.setVisible(hasLocation);
            locationMapView.setManaged(hasLocation);
        }
        if (mapStatusLabel != null) {
            mapStatusLabel.setText(hasLocation ? "Searching location" : "No location available");
        }

        if (!hasLocation) {
            return;
        }

        WebEngine engine = locationMapView.getEngine();
        Runnable geocode = () -> {
            try {
                engine.executeScript("showLocation('" + escapeForJavascript(location) + "')");
                if (mapStatusLabel != null) {
                    mapStatusLabel.setText("Location loaded");
                }
            } catch (Exception exception) {
                if (mapStatusLabel != null) {
                    mapStatusLabel.setText("Map could not load location");
                }
            }
        };

        if (engine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
            Platform.runLater(geocode);
        }
    }

    private void populateChips(FlowPane pane, List<String> values, String... styleClasses) {
        if (pane == null) {
            return;
        }

        pane.getChildren().clear();
        List<String> items = values.isEmpty() ? List.of("Not specified") : values;
        for (String value : items) {
            Label chip = new Label(value);
            chip.getStyleClass().add("job-offer-chip");
            for (String styleClass : styleClasses) {
                chip.getStyleClass().add(styleClass);
            }
            pane.getChildren().add(chip);
        }
    }

    private List<String> splitItems(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        String[] parts = rawValue.split("[,;\\n]+");
        List<String> items = new ArrayList<>();
        for (String part : parts) {
            String normalized = trimToNull(part);
            if (normalized != null) {
                items.add(normalized);
            }
        }
        return items;
    }

    private void applyStatusStyle(Label label, String status) {
        if (label == null) {
            return;
        }
        label.getStyleClass().removeAll(
                "job-offer-status-active",
                "job-offer-status-pending",
                "job-offer-status-rejected",
                "job-offer-status-closed",
                "job-offer-status-info"
        );

        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ENGLISH);
        switch (normalized) {
            case "ACTIVE" -> label.getStyleClass().add("job-offer-status-active");
            case "PENDING" -> label.getStyleClass().add("job-offer-status-pending");
            case "REJECTED" -> label.getStyleClass().add("job-offer-status-rejected");
            case "CLOSED" -> label.getStyleClass().add("job-offer-status-closed");
            default -> label.getStyleClass().add("job-offer-status-info");
        }
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "Not scheduled";
        }
        return timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DATE_TIME_FORMATTER);
    }

    private String safe(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeForJavascript(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
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
