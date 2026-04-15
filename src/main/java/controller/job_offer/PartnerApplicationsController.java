package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import services.ServiceUser;
import services.job_offer.ServiceJobApplication;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.io.File;
import java.io.IOException;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class PartnerApplicationsController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML
    private VBox rootContainer;

    @FXML
    private Label pageTitleLabel;

    @FXML
    private Label pageSubtitleLabel;

    @FXML
    private Button backButton;

    @FXML
    private Button refreshButton;

    @FXML
    private ScrollPane applicationsScrollPane;

    @FXML
    private VBox applicationsList;

    @FXML
    private Label emptyStateLabel;

    private User currentUser;
    private ServiceJobApplication serviceJobApplication;
    private ServiceJobOffer serviceJobOffer;
    private ServiceUser serviceUser;
    private ObservableList<JobApplication> allApplications;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        serviceJobOffer = new ServiceJobOffer();
        serviceUser = new ServiceUser();
        allApplications = FXCollections.observableArrayList();

        Platform.runLater(this::loadApplications);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (pageTitleLabel != null) {
            pageTitleLabel.setText("Applications to Review");
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText("Review people who applied to your offers and accept or reject them.");
        }
        loadApplications();
    }

    private void loadApplications() {
        if (currentUser == null) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                List<JobApplication> applications = serviceJobApplication.getALL();
                List<JobOffer> offers = serviceJobOffer.getALL();
                List<User> users = serviceUser.getALL();

                Map<Integer, JobOffer> offersById = offers.stream()
                    .collect(Collectors.toMap(JobOffer::getId, offer -> offer, (left, right) -> left));
                Map<Integer, User> usersById = users.stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

                List<Integer> ownedOfferIds = offers.stream()
                        .filter(this::isOwnedOffer)
                        .map(JobOffer::getId)
                        .collect(Collectors.toList());

                List<JobApplication> partnerApplications = applications.stream()
                        .filter(application -> isApplicationForOwnedOffer(application, ownedOfferIds))
                    .peek(application -> enrichApplicationReferences(application, offersById, usersById))
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    allApplications.setAll(partnerApplications);
                    renderApplications(partnerApplications);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error loading applications", e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isOwnedOffer(JobOffer offer) {
        if (offer == null || offer.getUser() == null) {
            return false;
        }

        return offer.getUser().getId().equals(currentUser.getId()) || RoleGuard.isAdmin(currentUser);
    }

    private boolean isApplicationForOwnedOffer(JobApplication application, List<Integer> ownedOfferIds) {
        if (application == null || application.getJobOffer() == null || application.getJobOffer().getId() <= 0) {
            return false;
        }

        if (RoleGuard.isAdmin(currentUser)) {
            return true;
        }

        return ownedOfferIds.contains(application.getJobOffer().getId());
    }

    private void renderApplications(List<JobApplication> applications) {
        applicationsList.getChildren().clear();

        boolean empty = applications == null || applications.isEmpty();
        emptyStateLabel.setManaged(empty);
        emptyStateLabel.setVisible(empty);

        if (empty) {
            return;
        }

        applications.forEach(application -> applicationsList.getChildren().add(buildApplicationRow(application)));
    }

    private VBox buildApplicationRow(JobApplication application) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("job-offer-card");
        card.setPadding(new Insets(14));
        card.setMaxWidth(Double.MAX_VALUE);

        JobOffer offer = application.getJobOffer();
        String offerTitle = offer != null && offer.getTitle() != null ? offer.getTitle() : "Unknown offer";
        String candidateEmail = application.getUser() != null && application.getUser().getEmail() != null
                ? application.getUser().getEmail()
                : "Unknown candidate";

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label offerLabel = new Label(offerTitle);
        offerLabel.getStyleClass().add("job-offer-card-title");
        offerLabel.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusLabel = new Label(valueOrDefault(application.getStatus(), "SUBMITTED"));
        statusLabel.getStyleClass().add("job-offer-status-chip");
        applyStatusStyle(statusLabel, application.getStatus());

        topRow.getChildren().addAll(offerLabel, spacer, statusLabel);

        Label candidateLabel = new Label("Candidate: " + candidateEmail);
        candidateLabel.getStyleClass().add("job-offer-card-meta");

        Label dateLabel = new Label("Applied: " + formatDate(application.getCreatedAt()));
        dateLabel.getStyleClass().add("job-offer-card-meta");

        String cvPath = valueOrDefault(application.getCvFileName(), "No CV attached");
        Label cvLabel = new Label("CV: " + cvPath);
        cvLabel.getStyleClass().add("job-offer-card-meta");
        cvLabel.setWrapText(true);

        TextArea messageArea = new TextArea(valueOrDefault(application.getMessage(), "No motivation letter provided."));
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(4);
        messageArea.getStyleClass().add("job-offer-readonly-area");

        HBox actions = new HBox(8);
        Button viewButton = new Button("View Review");
        viewButton.getStyleClass().addAll("ghost-button", "job-offer-card-button");
        viewButton.setOnAction(event -> AppNavigator.showJobApplicationReview(application));

        Button openCvButton = new Button("Open CV");
        openCvButton.getStyleClass().addAll("ghost-button", "job-offer-card-button");
        openCvButton.setOnAction(event -> openCvFile(application));

        Button approveButton = new Button("Accept");
        approveButton.getStyleClass().addAll("primary-button", "job-offer-card-button");
        approveButton.setOnAction(event -> updateStatus(application, JobApplicationStatus.ACCEPTED));

        Button rejectButton = new Button("Reject");
        rejectButton.getStyleClass().addAll("ghost-button", "job-offer-danger-button", "job-offer-card-button");
        rejectButton.setOnAction(event -> updateStatus(application, JobApplicationStatus.REJECTED));

        actions.getChildren().addAll(viewButton, openCvButton, approveButton, rejectButton);

        boolean hasCv = application.getCvFileName() != null && !application.getCvFileName().trim().isEmpty();
        openCvButton.setVisible(hasCv);
        openCvButton.setManaged(hasCv);

        card.getChildren().addAll(topRow, candidateLabel, dateLabel, cvLabel, messageArea, actions);
        return card;
    }

    private void enrichApplicationReferences(JobApplication application,
                                             Map<Integer, JobOffer> offersById,
                                             Map<Integer, User> usersById) {
        if (application == null) {
            return;
        }

        if (application.getJobOffer() != null) {
            JobOffer fullOffer = offersById.get(application.getJobOffer().getId());
            if (fullOffer != null) {
                application.setJobOffer(fullOffer);
            }
        }

        if (application.getUser() != null) {
            User fullUser = usersById.get(application.getUser().getId());
            if (fullUser != null) {
                application.setUser(fullUser);
            }
        }
    }

    private boolean canReview(JobApplication application) {
        return application != null;
    }

    private void updateStatus(JobApplication application, JobApplicationStatus targetStatus) {
        if (!canReview(application)) {
            showError("Access denied", "You cannot update this application.");
            return;
        }

        try {
            application.setStatus(targetStatus.name());
            application.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            if (targetStatus == JobApplicationStatus.ACCEPTED || targetStatus == JobApplicationStatus.REJECTED) {
                application.setStatusNotified((byte) 0);
                application.setStatusNotifiedAt(null);
            }
            serviceJobApplication.update(application);
            loadApplications();
        } catch (Exception exception) {
            showError("Update failed", exception.getMessage());
        }
    }

    private void openCvFile(JobApplication application) {
        if (application == null || application.getCvFileName() == null || application.getCvFileName().trim().isEmpty()) {
            showError("CV not available", "This application does not have a CV file.");
            return;
        }

        try {
            String storedValue = application.getCvFileName().trim();
            File cvFile = resolveCvFile(storedValue);
            if (cvFile == null || !cvFile.exists()) {
                relinkCvFile(application, storedValue);
                return;
            }

            if (!Desktop.isDesktopSupported()) {
                showError("Open not supported", "Desktop open is not supported on this system.");
                return;
            }

            Desktop.getDesktop().open(cvFile);
        } catch (Exception exception) {
            showError("Open CV failed", exception.getMessage());
        }
    }

    private void relinkCvFile(JobApplication application, String previousValue) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("CV not found");
        confirm.setHeaderText("Stored CV path is invalid");
        confirm.setContentText("File not found: " + previousValue + "\n\nDo you want to locate the CV file now?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Candidate CV");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CV Files (*.pdf, *.doc, *.docx)", "*.pdf", "*.doc", "*.docx"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selected = chooser.showOpenDialog(backButton.getScene() != null ? backButton.getScene().getWindow() : null);
        if (selected == null) {
            return;
        }

        try {
            String managedCvPath = persistCvFile(selected);
            application.setCvFileName(managedCvPath);
            application.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            serviceJobApplication.update(application);
            Desktop.getDesktop().open(new File(managedCvPath));
            loadApplications();
        } catch (Exception exception) {
            showError("CV relink failed", exception.getMessage());
        }
    }

    private String persistCvFile(File source) throws IOException {
        if (source == null || !source.exists()) {
            throw new IOException("Selected CV file does not exist.");
        }

        String sanitizedName = source.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String targetName = System.currentTimeMillis() + "_" + sanitizedName;

        Path targetDir = Path.of(System.getProperty("user.dir"), "uploads", "cvs");
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(targetName);
        Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toAbsolutePath().toString();
    }

    private File resolveCvFile(String storedValue) {
        File direct = new File(storedValue);
        if (direct.exists()) {
            return direct;
        }

        String normalized = storedValue.replace("\\", File.separator).replace("/", File.separator);
        File normalizedFile = new File(normalized);
        if (normalizedFile.exists()) {
            return normalizedFile;
        }

        String filenameOnly = new File(normalized).getName();
        if (filenameOnly.isEmpty()) {
            return null;
        }

        List<File> candidateDirs = new ArrayList<>();
        candidateDirs.add(new File(System.getProperty("user.dir")));
        candidateDirs.add(new File(System.getProperty("user.home"), "Downloads"));
        candidateDirs.add(new File(System.getProperty("user.home"), "Desktop"));
        candidateDirs.add(new File(System.getProperty("user.home"), "Documents"));

        for (File dir : candidateDirs) {
            File candidate = new File(dir, filenameOnly);
            if (candidate.exists()) {
                return candidate;
            }
        }

        return null;
    }

    @FXML
    private void onBack() {
        AppNavigator.showJobOffers();
    }

    @FXML
    private void onRefresh() {
        loadApplications();
    }

    private void applyStatusStyle(Label statusLabel, String status) {
        if (status == null) {
            return;
        }
        switch (status.trim().toUpperCase()) {
            case "ACCEPTED" -> statusLabel.getStyleClass().add("job-offer-status-active");
            case "REJECTED" -> statusLabel.getStyleClass().add("job-offer-status-rejected");
            case "REVIEWED" -> statusLabel.getStyleClass().add("job-offer-status-pending");
            case "SUBMITTED" -> statusLabel.getStyleClass().add("job-offer-status-info");
            default -> {
            }
        }
    }

    private String formatDate(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime().format(DATE_FORMATTER) : "Unknown";
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}