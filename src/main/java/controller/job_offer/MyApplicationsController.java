package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import entities.job_offer.JobOfferMeeting;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import service.job_offer.ApplicationDocumentStorageService;
import services.ServiceUser;
import service.job_offer.GeminiApplicationFeedbackService;
import service.job_offer.JobOfferMeetingService;
import services.job_offer.ServiceJobApplication;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

public class MyApplicationsController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter MEETING_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private VBox rootContainer;

    @FXML
    private ComboBox<String> filterStatus;

    @FXML
    private ListView<JobApplication> applicationListView;

    @FXML
    private Label noAppsLabel;

    private User currentUser;
    private ServiceJobApplication serviceJobApplication;
    private ServiceJobOffer serviceJobOffer;
    private ServiceUser serviceUser;
    private ApplicationDocumentStorageService documentStorageService;
    private GeminiApplicationFeedbackService aiFeedbackService;
    private JobOfferMeetingService jobOfferMeetingService;
    private ObservableList<JobApplication> allApplications;
    private Map<Integer, JobOfferMeeting> meetingsByApplicationId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        serviceJobOffer = new ServiceJobOffer();
        serviceUser = new ServiceUser();
        documentStorageService = new ApplicationDocumentStorageService();
        aiFeedbackService = new GeminiApplicationFeedbackService();
        jobOfferMeetingService = new JobOfferMeetingService();
        allApplications = FXCollections.observableArrayList();
        meetingsByApplicationId = new LinkedHashMap<>();

        setupFilters();
        setupListView();
        filterStatus.setOnAction(e -> applyFilters());
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loadApplications();
    }

    private void setupFilters() {
        filterStatus.setItems(FXCollections.observableArrayList(
                "All", "SUBMITTED", "REVIEWED", "ACCEPTED", "REJECTED"
        ));
        filterStatus.setValue("All");
    }

    private void setupListView() {
        applicationListView.setCellFactory(param -> new ApplicationListCell());
        Label placeholder = new Label("No applications to display.");
        placeholder.getStyleClass().add("job-offer-empty-label");
        applicationListView.setPlaceholder(placeholder);
    }

    private void loadApplications() {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                List<JobApplication> applications = serviceJobApplication.getALL();
                List<JobOffer> offers = serviceJobOffer.getALL();
                List<User> users = serviceUser.getALL();
                List<JobOfferMeeting> meetings = jobOfferMeetingService.getMeetingsForStudent(currentUser.getId());

                Map<Integer, JobOffer> offersById = offers.stream()
                        .filter(offer -> offer != null && offer.getId() > 0)
                        .collect(Collectors.toMap(JobOffer::getId, offer -> offer, (left, right) -> left, LinkedHashMap::new));

                Map<Integer, User> usersById = users.stream()
                        .filter(user -> user != null && user.getId() != null)
                        .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));

                List<JobApplication> userApps = applications.stream()
                        .filter(app -> app.getUser().getId().equals(currentUser.getId()))
                        .peek(app -> enrichApplicationReferences(app, offersById, usersById))
                        .sorted(Comparator.comparing(JobApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toList());

                Map<Integer, JobOfferMeeting> meetingMap = meetings.stream()
                        .filter(meeting -> meeting != null && meeting.getApplication() != null)
                        .collect(Collectors.toMap(
                                meeting -> meeting.getApplication().getId(),
                                meeting -> meeting,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));

                Platform.runLater(() -> {
                    meetingsByApplicationId = meetingMap;
                    allApplications.setAll(userApps);
                    applyFilters();
                    noAppsLabel.setVisible(userApps.isEmpty());
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error loading applications", e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void applyFilters() {
        String selectStatus = filterStatus.getValue();

        List<JobApplication> filtered = allApplications.stream()
                .filter(app -> "All".equals(selectStatus) || selectStatus.equals(app.getStatus()))
                .collect(Collectors.toList());

        applicationListView.setItems(FXCollections.observableArrayList(filtered));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void onBack() {
        AppNavigator.showJobOffers();
    }

    /**
     * Custom ListCell for JobApplications
     */
    private class ApplicationListCell extends ListCell<JobApplication> {
        @Override
        protected void updateItem(JobApplication app, boolean empty) {
            super.updateItem(app, empty);
            if (empty || app == null) {
                setGraphic(null);
                setText(null);
                getStyleClass().remove("job-offer-list-cell");
                return;
            }

            if (!getStyleClass().contains("job-offer-list-cell")) {
                getStyleClass().add("job-offer-list-cell");
            }

            VBox cellContent = new VBox(14);
            cellContent.getStyleClass().addAll("job-offer-card", "student-application-card");
            applyCardStatusStyle(cellContent, app.getStatus());
            cellContent.setFillWidth(true);
            cellContent.setMaxWidth(Double.MAX_VALUE);
            cellContent.prefWidthProperty().bind(applicationListView.widthProperty().subtract(36));

            HBox titleBar = new HBox(12);
            titleBar.setAlignment(Pos.CENTER_LEFT);
            titleBar.setMaxWidth(Double.MAX_VALUE);

            JobOffer offer = app.getJobOffer();
            String jobTitle = safeText(offer != null ? offer.getTitle() : null, "Untitled offer");

            VBox titleInfo = new VBox(6);
            titleInfo.setMaxWidth(Double.MAX_VALUE);
            Label titleLabel = new Label(jobTitle);
            titleLabel.getStyleClass().add("job-offer-card-title");
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(Double.MAX_VALUE);

            HBox metaLine = new HBox(10);
            metaLine.setAlignment(Pos.CENTER_LEFT);

            Label locationLabel = new Label("Location: " + safeText(offer != null ? offer.getLocation() : null, "Not specified"));
            locationLabel.getStyleClass().add("job-offer-card-meta");

            Label typeLabel = new Label(safeText(offer != null ? offer.getType() : null, "APPLICATION"));
            typeLabel.getStyleClass().add("job-offer-chip");

            metaLine.getChildren().addAll(locationLabel, typeLabel);
            titleInfo.getChildren().addAll(titleLabel, metaLine);
            HBox.setHgrow(titleInfo, Priority.ALWAYS);

            Label statusLabel = new Label(app.getStatus() != null ? app.getStatus() : "");
            applyApplicationStatusStyle(statusLabel, app.getStatus());

            titleBar.getChildren().addAll(titleInfo, statusLabel);

            HBox metaBar = new HBox(14);
            metaBar.setAlignment(Pos.CENTER_LEFT);
            String appliedAt = app.getCreatedAt() != null
                    ? app.getCreatedAt().toLocalDateTime().format(DATE_FORMATTER)
                    : "Unknown";
            Label appliedLabel = new Label("Applied: " + appliedAt);
            appliedLabel.getStyleClass().add("job-offer-card-meta");
            metaBar.getChildren().add(appliedLabel);

            if (app.getScore() != null) {
                Label scoreLabel = new Label("Score: " + app.getScore() + "/100");
                scoreLabel.getStyleClass().add("job-offer-score-badge");
                metaBar.getChildren().add(scoreLabel);
            }

            VBox partnerMessageCard = new VBox(8);
            partnerMessageCard.getStyleClass().add("student-application-message-card");
            partnerMessageCard.setMaxWidth(Double.MAX_VALUE);
            Label partnerMessageLabel = new Label(resolvePartnerMessageTitle(app));
            partnerMessageLabel.getStyleClass().add("job-offer-section-label");
            Label partnerMessageBody = new Label(buildPartnerMessagePreview(app));
            partnerMessageBody.getStyleClass().add("job-offer-card-description");
            partnerMessageBody.setWrapText(true);
            partnerMessageBody.setMaxWidth(Double.MAX_VALUE);
            partnerMessageBody.prefWidthProperty().bind(cellContent.widthProperty().subtract(28));
            partnerMessageCard.getChildren().addAll(partnerMessageLabel, partnerMessageBody);

            FlowPane actionsBar = new FlowPane();
            actionsBar.setHgap(10);
            actionsBar.setVgap(10);
            actionsBar.setAlignment(Pos.CENTER_LEFT);

            Button viewOfferButton = new Button("View Offer");
            viewOfferButton.getStyleClass().addAll("ghost-button", "job-offer-card-button");
            viewOfferButton.setOnAction(event -> {
                if (app.getJobOffer() != null && app.getJobOffer().getId() > 0) {
                    AppNavigator.showJobOfferDetail(app.getJobOffer());
                }
            });

            Button openCvButton = new Button("Open CV");
            openCvButton.getStyleClass().addAll("ghost-button", "job-offer-card-button");
            boolean hasCv = hasCv(app);
            openCvButton.setDisable(!hasCv);
            openCvButton.setOnAction(event -> openCvFile(app));

            Button adviceButton = new Button("AI Advice");
            adviceButton.getStyleClass().addAll("primary-button", "job-offer-card-button");
            adviceButton.setOnAction(event -> showAdviceDialog(app));

            actionsBar.getChildren().addAll(viewOfferButton, openCvButton, adviceButton);

            cellContent.getChildren().addAll(titleBar, metaBar, partnerMessageCard);
            JobOfferMeeting meeting = meetingsByApplicationId.get(app.getId());
            if (isAccepted(app) && meeting != null) {
                cellContent.getChildren().add(buildMeetingCard(meeting));
            }
            cellContent.getChildren().add(actionsBar);
            setGraphic(cellContent);
            setText(null);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
    }

    private void applyApplicationStatusStyle(Label statusLabel, String status) {
        statusLabel.getStyleClass().add("job-offer-status-chip");
        if (status == null) {
            return;
        }
        String normalized = status.trim().toUpperCase();
        switch (normalized) {
            case "ACCEPTED" -> statusLabel.getStyleClass().add("job-offer-status-active");
            case "SUBMITTED" -> statusLabel.getStyleClass().add("job-offer-status-info");
            case "REVIEWED" -> statusLabel.getStyleClass().add("job-offer-status-pending");
            case "REJECTED" -> statusLabel.getStyleClass().add("job-offer-status-rejected");
            default -> {
            }
        }
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

        if (application.getUser() != null && application.getUser().getId() != null) {
            User fullUser = usersById.get(application.getUser().getId());
            if (fullUser != null) {
                application.setUser(fullUser);
            }
        }
    }

    private void applyCardStatusStyle(VBox card, String status) {
        card.getStyleClass().removeAll(
                "student-application-card-submitted",
                "student-application-card-reviewed",
                "student-application-card-accepted",
                "student-application-card-rejected"
        );

        String normalized = status == null ? "" : status.trim().toUpperCase();
        switch (normalized) {
            case "ACCEPTED" -> card.getStyleClass().add("student-application-card-accepted");
            case "REJECTED" -> card.getStyleClass().add("student-application-card-rejected");
            case "REVIEWED" -> card.getStyleClass().add("student-application-card-reviewed");
            default -> card.getStyleClass().add("student-application-card-submitted");
        }
    }

    private VBox buildMeetingCard(JobOfferMeeting meeting) {
        VBox meetingCard = new VBox(8);
        meetingCard.getStyleClass().add("student-application-message-card");
        meetingCard.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("Interview meeting");
        title.getStyleClass().add("job-offer-section-label");

        Label details = new Label("Scheduled: " + formatMeetingDateTime(meeting)
                + " | Status: " + safeText(meeting.getStatus(), "scheduled").toUpperCase());
        details.getStyleClass().add("job-offer-card-description");
        details.setWrapText(true);
        details.setMaxWidth(Double.MAX_VALUE);

        Button joinButton = new Button("Join Meeting");
        joinButton.getStyleClass().addAll("primary-button", "job-offer-card-button");
        boolean canJoin = jobOfferMeetingService.canJoinNow(meeting);
        joinButton.setDisable(!canJoin);
        if (!canJoin) {
            joinButton.setTooltip(new Tooltip(resolveMeetingLockedText(meeting)));
        }
        joinButton.setOnAction(event -> joinMeeting(meeting));

        meetingCard.getChildren().addAll(title, details, joinButton);
        return meetingCard;
    }

    private void joinMeeting(JobOfferMeeting meeting) {
        try {
            JobOfferMeeting joinedMeeting = jobOfferMeetingService.joinStudentMeeting(meeting.getId());
            AppNavigator.showJobOfferMeetingRoom(joinedMeeting, false);
        } catch (Exception exception) {
            showInfo("Cannot join meeting", exception.getMessage());
            loadApplications();
        }
    }

    private String resolveMeetingLockedText(JobOfferMeeting meeting) {
        if (meeting != null && meeting.isEnded()) {
            return "This meeting has ended.";
        }
        return "Meeting opens on " + formatMeetingDateTime(meeting) + ".";
    }

    private boolean isAccepted(JobApplication application) {
        return JobApplicationStatus.fromString(application == null ? null : application.getStatus()) == JobApplicationStatus.ACCEPTED;
    }

    private String formatMeetingDateTime(JobOfferMeeting meeting) {
        return meeting != null && meeting.getScheduledAt() != null
                ? meeting.getScheduledAt().toLocalDateTime().format(MEETING_DATE_TIME_FORMATTER)
                : "Not scheduled";
    }

    private boolean hasCv(JobApplication application) {
        return application != null
                && application.getCvFileName() != null
                && !application.getCvFileName().trim().isEmpty();
    }

    private boolean isRejected(JobApplication application) {
        return JobApplicationStatus.fromString(application == null ? null : application.getStatus()) == JobApplicationStatus.REJECTED;
    }

    private void openCvFile(JobApplication application) {
        if (!hasCv(application)) {
            showInfo("CV not available", "No CV file is attached to this application.");
            return;
        }

        try {
            File cvFile = documentStorageService.resolveCvFile(application.getCvFileName().trim());
            if (cvFile == null || !cvFile.exists()) {
                showError("CV not found", "The stored CV file could not be found on this computer.");
                return;
            }

            if (!Desktop.isDesktopSupported()) {
                showError("Open not supported", "Desktop file opening is not supported on this system.");
                return;
            }

            Desktop.getDesktop().open(cvFile);
        } catch (Exception exception) {
            showError("Open CV failed", exception.getMessage());
        }
    }

    private void showAdviceDialog(JobApplication application) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(isRejected(application) ? "AI Rejection Advice" : "AI Application Advice");
        alert.setHeaderText(safeText(application.getJobOffer() != null ? application.getJobOffer().getTitle() : null, "Application guidance"));

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setPrefWidth(700);
        dialogPane.setPrefHeight(720);

        VBox content = new VBox(12);
        content.setPadding(new Insets(8, 0, 0, 0));
        content.setFillWidth(true);

        Label whyTitle = new Label(isRejected(application) ? "Why this application was rejected" : "Current application status");
        whyTitle.getStyleClass().add("job-offer-section-title");

        Label whyBody = new Label(buildRejectionExplanation(application));
        whyBody.getStyleClass().add("job-offer-card-description");
        whyBody.setMaxWidth(Double.MAX_VALUE);
        whyBody.setWrapText(true);

        Label partnerMessageTitle = new Label("Partner message");
        partnerMessageTitle.getStyleClass().add("job-offer-section-title");

        Label partnerMessageBody = new Label(resolvePartnerMessage(application));
        partnerMessageBody.getStyleClass().add("job-offer-card-description");
        partnerMessageBody.setMaxWidth(Double.MAX_VALUE);
        partnerMessageBody.setWrapText(true);

        Label cvTitle = new Label("CV on file");
        cvTitle.getStyleClass().add("job-offer-section-title");

        Label cvBody = new Label(hasCv(application)
                ? documentStorageService.extractDisplayName(application.getCvFileName())
                : "No CV attached to this application.");
        cvBody.getStyleClass().add("job-offer-card-description");
        cvBody.setMaxWidth(Double.MAX_VALUE);
        cvBody.setWrapText(true);

        Label adviceTitle = new Label("Professional advice");
        adviceTitle.getStyleClass().add("job-offer-section-title");

        TextArea adviceBody = new TextArea(buildProfessionalAdvice(application));
        adviceBody.getStyleClass().add("job-offer-readonly-area");
        adviceBody.setWrapText(true);
        adviceBody.setEditable(false);
        adviceBody.setPrefRowCount(8);
        adviceBody.setMinHeight(180);
        adviceBody.setPrefHeight(240);
        adviceBody.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(adviceBody, Priority.ALWAYS);

        content.getChildren().addAll(
                whyTitle,
                whyBody,
                partnerMessageTitle,
                partnerMessageBody,
                cvTitle,
                cvBody,
                adviceTitle,
                adviceBody
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportHeight(560);
        dialogPane.setContent(scrollPane);

        if (aiFeedbackService != null && aiFeedbackService.isEnabled()) {
            Thread thread = new Thread(() -> {
                String generatedAdvice = aiFeedbackService.generateStudentAdvice(application);
                Platform.runLater(() -> adviceBody.setText(generatedAdvice));
            });
            thread.setDaemon(true);
            thread.start();
        }
        alert.showAndWait();
    }

    private String resolvePartnerMessageTitle(JobApplication application) {
        return hasPartnerMessage(application) ? "Partner message" : "AI advice preview";
    }

    private String buildPartnerMessagePreview(JobApplication application) {
        if (hasPartnerMessage(application)) {
            return resolvePartnerMessage(application);
        }

        String message = buildProfessionalAdvice(application);
        int periodIndex = message.indexOf('.');
        if (periodIndex > 0) {
            return message.substring(0, periodIndex + 1);
        }
        return message;
    }

    private String buildRejectionExplanation(JobApplication application) {
        String partnerMessage = safeText(application.getStatusMessage(), "");
        if (!partnerMessage.isBlank()) {
            return partnerMessage;
        }

        JobOffer offer = application.getJobOffer();
        String requiredSkills = safeText(offer != null ? offer.getRequiredSkills() : null, "the requested skills");
        return "The available feedback suggests that the employer needed a stronger match with " + requiredSkills
                + " for this role. This does not mean your profile is weak; it means another candidate matched this opening more closely.";
    }

    private String buildProfessionalAdvice(JobApplication application) {
        JobOffer offer = application.getJobOffer();
        String requiredSkills = safeText(offer != null ? offer.getRequiredSkills() : null, "the main skills in the role");
        String preferredSkills = safeText(offer != null ? offer.getPreferredSkills() : null, "supporting skills related to the job");
        String scoreSummary = application.getScore() == null
                ? "The application has no ATS score yet, so focus on making your CV clearer and more targeted."
                : application.getScore() >= 75
                ? "Your score is already strong, so the main improvement is tailoring your CV and message more directly to this employer."
                : application.getScore() >= 50
                ? "Your score shows partial alignment. Strengthen the most important keywords and evidence in your CV."
                : "Your score suggests the employer did not see enough alignment yet. Rework the CV around the role requirements before applying again.";

        return scoreSummary + " Prioritize stronger evidence for " + requiredSkills
                + ", highlight concrete results in your CV, and improve your cover message with examples tied to "
                + preferredSkills + ".";
    }

    private boolean hasPartnerMessage(JobApplication application) {
        return application != null
                && application.getStatusMessage() != null
                && !application.getStatusMessage().trim().isEmpty();
    }

    private String resolvePartnerMessage(JobApplication application) {
        if (hasPartnerMessage(application)) {
            return application.getStatusMessage().trim();
        }
        return "No partner feedback has been sent for this application yet.";
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
