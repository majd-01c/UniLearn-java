package controller.job_offer;

import service.job_offer.GeminiApplicationFeedbackService;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private Button atsBoardButton;

    @FXML
    private Label offersCountLabel;

    @FXML
    private Label applicationsCountLabel;

    @FXML
    private Label pendingCountLabel;

    @FXML
    private SplitPane reviewSplitPane;

    @FXML
    private Label offersMetaLabel;

    @FXML
    private TextField offerSearchField;

    @FXML
    private VBox offersList;

    @FXML
    private Label selectedOfferLabel;

    @FXML
    private Label selectedOfferMetaLabel;

    @FXML
    private ComboBox<String> statusFilterCombo;

    @FXML
    private VBox applicationsList;

    @FXML
    private Label emptyStateLabel;

    @FXML
    private Label reviewTitleLabel;

    @FXML
    private Label reviewSubtitleLabel;

    @FXML
    private Label reviewStatusChip;

    @FXML
    private Label candidateNameLabel;

    @FXML
    private Label candidateEmailLabel;

    @FXML
    private Label scoreLabel;

    @FXML
    private Label scoreCaptionLabel;

    @FXML
    private Label appliedDateLabel;

    @FXML
    private Label statusTextLabel;

    @FXML
    private TextArea messageArea;

    @FXML
    private TextArea feedbackArea;

    @FXML
    private ComboBox<String> feedbackDecisionCombo;

    @FXML
    private Button generateFeedbackButton;

    @FXML
    private Label feedbackHelperLabel;

    @FXML
    private Button openCvButton;

    @FXML
    private Button openAtsButton;

    @FXML
    private Button submitDecisionButton;

    private User currentUser;
    private ServiceJobApplication serviceJobApplication;
    private ServiceJobOffer serviceJobOffer;
    private ServiceUser serviceUser;
    private GeminiApplicationFeedbackService aiFeedbackService;

    private final ObservableList<JobApplication> allApplications = FXCollections.observableArrayList();
    private final ObservableList<JobOffer> ownedOffers = FXCollections.observableArrayList();
    private final Map<Integer, List<JobApplication>> applicationsByOfferId = new LinkedHashMap<>();

    private JobOffer selectedOffer;
    private JobApplication selectedApplication;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        serviceJobOffer = new ServiceJobOffer();
        serviceUser = new ServiceUser();
        aiFeedbackService = new GeminiApplicationFeedbackService();

        setupFilters();
        setupInteraction();
        resetReviewPanel();

        Platform.runLater(this::loadApplications);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (pageTitleLabel != null) {
            pageTitleLabel.setText("Applications to Review");
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText("Start with a job offer, then review every candidate in a cleaner side-by-side workspace.");
        }
        loadApplications();
    }

    private void setupFilters() {
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All statuses",
                JobApplicationStatus.SUBMITTED.name(),
                JobApplicationStatus.REVIEWED.name(),
                JobApplicationStatus.ACCEPTED.name(),
                JobApplicationStatus.REJECTED.name(),
                JobApplicationStatus.SCREENING.name(),
                JobApplicationStatus.SHORTLISTED.name(),
                JobApplicationStatus.INTERVIEW.name(),
                JobApplicationStatus.OFFER_SENT.name(),
                JobApplicationStatus.HIRED.name(),
                JobApplicationStatus.WITHDRAWN.name()
        ));
        statusFilterCombo.setValue("All statuses");

        feedbackDecisionCombo.setItems(FXCollections.observableArrayList(
                JobApplicationStatus.ACCEPTED.name(),
                JobApplicationStatus.REJECTED.name(),
                JobApplicationStatus.REVIEWED.name()
        ));
        feedbackDecisionCombo.setValue(JobApplicationStatus.REVIEWED.name());
    }

    private void setupInteraction() {
        offerSearchField.textProperty().addListener((obs, oldValue, newValue) -> renderOffers());
        statusFilterCombo.setOnAction(event -> renderApplicationsForSelection());
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        feedbackArea.setWrapText(true);
    }

    private void loadApplications() {
        if (currentUser == null) {
            return;
        }

        Integer previousOfferId = selectedOffer != null ? selectedOffer.getId() : null;
        Integer previousApplicationId = selectedApplication != null ? selectedApplication.getId() : null;

        Thread thread = new Thread(() -> {
            try {
                List<JobApplication> applications = serviceJobApplication.getALL();
                List<JobOffer> offers = serviceJobOffer.getALL();
                List<User> users = serviceUser.getALL();

                Map<Integer, JobOffer> offersById = offers.stream()
                        .filter(offer -> offer != null && offer.getId() > 0)
                        .collect(Collectors.toMap(JobOffer::getId, offer -> offer, (left, right) -> left));

                Map<Integer, User> usersById = users.stream()
                        .filter(user -> user != null && user.getId() != null)
                        .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

                List<JobOffer> accessibleOffers = offers.stream()
                        .filter(this::isOwnedOffer)
                        .sorted(Comparator.comparing(this::offerSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(offer -> safeText(offer.getTitle()), String.CASE_INSENSITIVE_ORDER))
                        .collect(Collectors.toList());

                List<Integer> accessibleOfferIds = accessibleOffers.stream()
                        .map(JobOffer::getId)
                        .collect(Collectors.toList());

                List<JobApplication> partnerApplications = applications.stream()
                        .filter(application -> isApplicationForOwnedOffer(application, accessibleOfferIds))
                        .peek(application -> enrichApplicationReferences(application, offersById, usersById))
                        .sorted(Comparator.comparing(JobApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toList());

                Map<Integer, List<JobApplication>> grouped = partnerApplications.stream()
                        .filter(application -> application.getJobOffer() != null)
                        .collect(Collectors.groupingBy(application -> application.getJobOffer().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()));

                Platform.runLater(() -> {
                    ownedOffers.setAll(accessibleOffers);
                    allApplications.setAll(partnerApplications);
                    applicationsByOfferId.clear();
                    applicationsByOfferId.putAll(grouped);

                    updateStats();
                    renderOffers();
                    restoreSelection(previousOfferId, previousApplicationId);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error loading applications", e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void restoreSelection(Integer previousOfferId, Integer previousApplicationId) {
        JobOffer restoredOffer = previousOfferId == null ? null : findOfferById(previousOfferId);
        if (restoredOffer == null && !ownedOffers.isEmpty()) {
            restoredOffer = findBestDefaultOffer();
        }

        selectOffer(restoredOffer);

        if (selectedOffer == null) {
            return;
        }

        JobApplication restoredApplication = previousApplicationId == null ? null : findApplicationById(previousApplicationId);
        if (restoredApplication != null && restoredApplication.getJobOffer() != null
                && restoredApplication.getJobOffer().getId() == selectedOffer.getId()) {
            selectApplication(restoredApplication);
        } else {
            List<JobApplication> visibleApplications = getFilteredApplicationsForSelectedOffer();
            selectApplication(visibleApplications.isEmpty() ? null : visibleApplications.get(0));
        }
    }

    private void updateStats() {
        long pending = allApplications.stream()
                .map(application -> JobApplicationStatus.fromString(application.getStatus()))
                .filter(status -> status == JobApplicationStatus.SUBMITTED
                        || status == JobApplicationStatus.REVIEWED
                        || status == JobApplicationStatus.SCREENING
                        || status == JobApplicationStatus.SHORTLISTED
                        || status == JobApplicationStatus.INTERVIEW
                        || status == JobApplicationStatus.OFFER_SENT)
                .count();

        offersCountLabel.setText(String.valueOf(ownedOffers.size()));
        applicationsCountLabel.setText(String.valueOf(allApplications.size()));
        pendingCountLabel.setText(String.valueOf(pending));
        offersMetaLabel.setText(ownedOffers.size() + (ownedOffers.size() == 1 ? " offer" : " offers"));
    }

    private void renderOffers() {
        offersList.getChildren().clear();

        String search = offerSearchField.getText() == null ? "" : offerSearchField.getText().trim().toLowerCase(Locale.ROOT);

        List<JobOffer> filteredOffers = ownedOffers.stream()
                .filter(offer -> search.isBlank()
                        || safeText(offer.getTitle()).toLowerCase(Locale.ROOT).contains(search)
                        || safeText(offer.getLocation()).toLowerCase(Locale.ROOT).contains(search))
                .collect(Collectors.toList());

        for (JobOffer offer : filteredOffers) {
            offersList.getChildren().add(buildOfferCard(offer));
        }

        if (selectedOffer != null && filteredOffers.stream().noneMatch(offer -> offer.getId() == selectedOffer.getId())) {
            selectOffer(filteredOffers.isEmpty() ? null : filteredOffers.get(0));
        }
    }

    private VBox buildOfferCard(JobOffer offer) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("job-offer-card", "job-offer-review-offer-card");
        if (selectedOffer != null && selectedOffer.getId() == offer.getId()) {
            card.getStyleClass().add("job-offer-review-offer-card-selected");
        }
        card.setPadding(new Insets(14));

        int applicationCount = applicationsByOfferId.getOrDefault(offer.getId(), List.of()).size();

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label(safeText(offer.getType(), "OFFER"));
        typeLabel.getStyleClass().add("job-offer-chip");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label(applicationCount + (applicationCount == 1 ? " app" : " apps"));
        countLabel.getStyleClass().add("job-offer-score-badge");

        topRow.getChildren().addAll(typeLabel, spacer, countLabel);

        Label titleLabel = new Label(safeText(offer.getTitle(), "Untitled offer"));
        titleLabel.getStyleClass().add("job-offer-card-title");
        titleLabel.setWrapText(true);

        Label metaLabel = new Label("📍 " + safeText(offer.getLocation(), "Location not specified"));
        metaLabel.getStyleClass().add("job-offer-card-meta");

        Label statusLabel = new Label(safeText(offer.getStatus(), "PENDING"));
        statusLabel.getStyleClass().add("job-offer-status-chip");
        applyStatusStyle(statusLabel, offer.getStatus());

        Label footerLabel = new Label(applicationCount == 0
                ? "No applications yet."
                : "Click to review candidates for this offer.");
        footerLabel.getStyleClass().add("job-offer-card-meta");
        footerLabel.setWrapText(true);

        card.getChildren().addAll(topRow, titleLabel, metaLabel, statusLabel, footerLabel);
        card.setOnMouseClicked(event -> selectOffer(offer));
        return card;
    }

    private void selectOffer(JobOffer offer) {
        selectedOffer = offer;
        renderOffers();
        updateOfferHeader();
        renderApplicationsForSelection();
    }

    private void updateOfferHeader() {
        if (selectedOffer == null) {
            selectedOfferLabel.setText("Select a job offer");
            selectedOfferMetaLabel.setText("Applications will appear here.");
            return;
        }

        int count = applicationsByOfferId.getOrDefault(selectedOffer.getId(), List.of()).size();
        selectedOfferLabel.setText(safeText(selectedOffer.getTitle(), "Untitled offer"));
        selectedOfferMetaLabel.setText(count + (count == 1 ? " application" : " applications")
                + " • " + safeText(selectedOffer.getLocation(), "Location not specified"));
    }

    private void renderApplicationsForSelection() {
        applicationsList.getChildren().clear();

        List<JobApplication> filteredApplications = getFilteredApplicationsForSelectedOffer();

        boolean empty = filteredApplications.isEmpty();
        emptyStateLabel.setManaged(empty);
        emptyStateLabel.setVisible(empty);

        if (selectedOffer == null) {
            emptyStateLabel.setText("Select a job offer to load its applications.");
            resetReviewPanel();
            return;
        }

        emptyStateLabel.setText("No applications match the current filter for this offer.");

        for (JobApplication application : filteredApplications) {
            applicationsList.getChildren().add(buildApplicationCard(application));
        }

        if (selectedApplication != null && filteredApplications.stream().noneMatch(app -> app.getId() == selectedApplication.getId())) {
            selectApplication(filteredApplications.isEmpty() ? null : filteredApplications.get(0));
        } else if (selectedApplication == null && !filteredApplications.isEmpty()) {
            selectApplication(filteredApplications.get(0));
        } else if (filteredApplications.isEmpty()) {
            selectApplication(null);
        }
    }

    private List<JobApplication> getFilteredApplicationsForSelectedOffer() {
        if (selectedOffer == null) {
            return List.of();
        }

        String selectedStatus = statusFilterCombo.getValue();
        return applicationsByOfferId.getOrDefault(selectedOffer.getId(), List.of()).stream()
                .filter(application -> "All statuses".equals(selectedStatus)
                        || JobApplicationStatus.fromString(application.getStatus()).name().equalsIgnoreCase(selectedStatus))
                .sorted(Comparator.comparing(JobApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private VBox buildApplicationCard(JobApplication application) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("job-offer-card", "job-offer-review-application-card");
        if (selectedApplication != null && selectedApplication.getId() == application.getId()) {
            card.getStyleClass().add("job-offer-review-application-card-selected");
        }
        card.setPadding(new Insets(14));

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox identityBox = new VBox(4);
        Label candidateLabel = new Label(resolveCandidateName(application));
        candidateLabel.getStyleClass().add("job-offer-card-title");

        Label emailLabel = new Label(resolveCandidateEmail(application));
        emailLabel.getStyleClass().add("job-offer-card-meta");
        identityBox.getChildren().addAll(candidateLabel, emailLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusChip = new Label(JobApplicationStatus.fromString(application.getStatus()).getLabel());
        statusChip.getStyleClass().add("job-offer-status-chip");
        applyStatusStyle(statusChip, application.getStatus());

        topRow.getChildren().addAll(identityBox, spacer, statusChip);

        HBox metaRow = new HBox(10);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label("Applied " + formatDate(application.getCreatedAt()));
        dateLabel.getStyleClass().add("job-offer-card-meta");

        Label scoreBadge = new Label(application.getScore() == null ? "No ATS score" : application.getScore() + "/100");
        scoreBadge.getStyleClass().add("job-offer-score-badge");

        metaRow.getChildren().addAll(dateLabel, scoreBadge);

        Label messagePreview = new Label(buildMessagePreview(application));
        messagePreview.getStyleClass().add("job-offer-card-meta");
        messagePreview.setWrapText(true);

        HBox actions = new HBox(8);
        Button reviewButton = new Button("Review");
        reviewButton.getStyleClass().addAll("primary-button", "job-offer-card-button");
        reviewButton.setOnAction(event -> {
            event.consume();
            selectApplication(application);
        });

        Button atsButton = new Button("ATS");
        atsButton.getStyleClass().addAll("ghost-button", "job-offer-card-button");
        atsButton.setOnAction(event -> {
            event.consume();
            selectApplication(application);
            AppNavigator.showAtsApplicationDetail(application);
        });

        Button cvButton = new Button("CV");
        cvButton.getStyleClass().addAll("ghost-button", "job-offer-card-button");
        cvButton.setOnAction(event -> {
            event.consume();
            selectApplication(application);
            openCvFile(application);
        });

        boolean hasCv = application.getCvFileName() != null && !application.getCvFileName().trim().isEmpty();
        cvButton.setVisible(hasCv);
        cvButton.setManaged(hasCv);

        actions.getChildren().addAll(reviewButton, atsButton, cvButton);

        card.getChildren().addAll(topRow, metaRow, messagePreview, actions);
        card.setOnMouseClicked(event -> selectApplication(application));
        return card;
    }

    private void selectApplication(JobApplication application) {
        selectedApplication = application;
        renderApplicationsForSelectionSilently();

        if (application == null) {
            resetReviewPanel();
            return;
        }

        reviewTitleLabel.setText(resolveCandidateName(application));
        reviewSubtitleLabel.setText(safeText(application.getJobOffer() != null ? application.getJobOffer().getTitle() : null, "Unknown offer"));
        candidateNameLabel.setText(resolveCandidateName(application));
        candidateEmailLabel.setText(resolveCandidateEmail(application));
        appliedDateLabel.setText(formatDate(application.getCreatedAt()));

        JobApplicationStatus status = JobApplicationStatus.fromString(application.getStatus());
        if (status == JobApplicationStatus.ACCEPTED
                || status == JobApplicationStatus.REJECTED
                || status == JobApplicationStatus.REVIEWED) {
            feedbackDecisionCombo.setValue(status.name());
        } else {
            feedbackDecisionCombo.setValue(JobApplicationStatus.REVIEWED.name());
        }
        reviewStatusChip.setText(status.getLabel());
        reviewStatusChip.getStyleClass().clear();
        reviewStatusChip.getStyleClass().add("job-offer-status-chip");
        applyStatusStyle(reviewStatusChip, status.name());

        statusTextLabel.setText(status.getDescription());
        messageArea.setText(safeText(application.getMessage(), "No motivation letter provided."));
        feedbackArea.setText(safeText(application.getStatusMessage(), ""));

        if (application.getScore() == null) {
            scoreLabel.setText("—");
            scoreCaptionLabel.setText("Open ATS calculator");
        } else {
            scoreLabel.setText(application.getScore() + "/100");
            scoreCaptionLabel.setText(resolveScoreCaption(application.getScore()));
        }

        boolean hasCv = application.getCvFileName() != null && !application.getCvFileName().trim().isEmpty();
        openCvButton.setDisable(!hasCv);
        openAtsButton.setDisable(false);

        submitDecisionButton.setDisable(false);
        generateFeedbackButton.setDisable(false);
        feedbackHelperLabel.setText("Choose a decision, then generate a message. You can submit again later to update it.");
    }

    private void renderApplicationsForSelectionSilently() {
        for (int i = 0; i < applicationsList.getChildren().size(); i++) {
            if (applicationsList.getChildren().get(i) instanceof VBox) {
                // Rebuild the list only when selection styling changes.
                renderApplicationsForSelection();
                return;
            }
        }
    }

    private void resetReviewPanel() {
        reviewTitleLabel.setText("Candidate Review");
        reviewSubtitleLabel.setText("Choose an application to open the full review workspace.");
        reviewStatusChip.getStyleClass().clear();
        reviewStatusChip.getStyleClass().addAll("job-offer-status-chip", "job-offer-status-closed");
        reviewStatusChip.setText("No selection");
        candidateNameLabel.setText("Select an application");
        candidateEmailLabel.setText("Candidate details will appear here.");
        scoreLabel.setText("Not scored");
        scoreCaptionLabel.setText("Run ATS when you are ready.");
        appliedDateLabel.setText("Waiting for a selection");
        statusTextLabel.setText("Pick a candidate to inspect their review status.");
        messageArea.setText("Select an application to read the candidate's motivation message.");
        feedbackArea.setText("");
        feedbackDecisionCombo.setValue(JobApplicationStatus.REVIEWED.name());
        generateFeedbackButton.setDisable(true);
        feedbackHelperLabel.setText("Choose a decision, then generate a message when a candidate is selected.");
        openCvButton.setDisable(true);
        openAtsButton.setDisable(true);
        submitDecisionButton.setDisable(true);
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

    private boolean isOwnedOffer(JobOffer offer) {
        if (offer == null || offer.getUser() == null || currentUser == null) {
            return false;
        }

        return RoleGuard.isAdmin(currentUser)
                || (offer.getUser().getId() != null && offer.getUser().getId().equals(currentUser.getId()));
    }

    private boolean isApplicationForOwnedOffer(JobApplication application, List<Integer> ownedOfferIds) {
        if (application == null || application.getJobOffer() == null || application.getJobOffer().getId() <= 0) {
            return false;
        }

        return RoleGuard.isAdmin(currentUser) || ownedOfferIds.contains(application.getJobOffer().getId());
    }

    @FXML
    private void onBack() {
        AppNavigator.showJobOffers();
    }

    @FXML
    private void onRefresh() {
        loadApplications();
    }

    @FXML
    private void onOpenAtsPipelineBoard() {
        AppNavigator.showAtsPipelineBoard();
    }

    @FXML
    private void onOpenSelectedCv() {
        if (selectedApplication != null) {
            openCvFile(selectedApplication);
        }
    }

    @FXML
    private void onOpenSelectedAtsDetail() {
        if (selectedApplication != null) {
            AppNavigator.showAtsApplicationDetail(selectedApplication);
        }
    }

    @FXML
    private void onSubmitDecision() {
        if (selectedApplication == null) {
            return;
        }

        String selectedDecision = feedbackDecisionCombo.getValue();
        if (selectedDecision == null || selectedDecision.isBlank()) {
            showError("Decision required", "Choose the status you want to submit first.");
            return;
        }

        JobApplicationStatus targetStatus = JobApplicationStatus.fromString(selectedDecision);

        if (targetStatus == JobApplicationStatus.ACCEPTED) {
            TextInputDialog scoreDialog = new TextInputDialog(selectedApplication.getScore() == null
                    ? "85"
                    : String.valueOf(selectedApplication.getScore()));
            scoreDialog.setTitle("Submit Accepted Status");
            scoreDialog.setHeaderText("Set the final score before accepting the candidate.");
            scoreDialog.setContentText("Score (0-100):");

            scoreDialog.showAndWait().ifPresent(scoreText -> {
                try {
                    int scoreValue = Integer.parseInt(scoreText.trim());
                    if (scoreValue < 0 || scoreValue > 100) {
                        showError("Validation", "Score must be between 0 and 100.");
                        return;
                    }

                    selectedApplication.setScore(scoreValue);
                    scoreCaptionLabel.setText(resolveScoreCaption(scoreValue));
                    updateSelectedStatus(targetStatus, true);
                } catch (NumberFormatException exception) {
                    showError("Validation", "Please enter a valid numeric score.");
                }
            });
            return;
        }

        updateSelectedStatus(targetStatus, targetStatus.requiresNotification());
    }

    @FXML
    private void onGenerateFeedback() {
        if (selectedApplication == null) {
            showError("No application selected", "Select an application before generating AI feedback.");
            return;
        }

        String selectedDecision = feedbackDecisionCombo.getValue();
        if (selectedDecision == null || selectedDecision.isBlank()) {
            showError("Decision required", "Choose the decision type first.");
            return;
        }

        if (!aiFeedbackService.isEnabled()) {
            showError("AI unavailable", "AI feedback generation is not configured in this environment.");
            return;
        }

        JobApplicationStatus decision = JobApplicationStatus.fromString(selectedDecision);
        generateFeedbackButton.setDisable(true);
        feedbackHelperLabel.setText("Generating message with AI...");

        Thread thread = new Thread(() -> {
            try {
                String guidance = feedbackArea.getText() == null ? null : feedbackArea.getText().trim();
                String generatedMessage = aiFeedbackService.generateFeedback(selectedApplication, decision, guidance);
                Platform.runLater(() -> {
                    feedbackArea.setText(generatedMessage);
                    feedbackHelperLabel.setText("AI message generated. You can edit it before sending.");
                    generateFeedbackButton.setDisable(false);
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    feedbackHelperLabel.setText("AI generation failed.");
                    generateFeedbackButton.setDisable(false);
                    showError("AI generation failed", exception.getMessage());
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSelectedStatus(JobApplicationStatus targetStatus, boolean notifyCandidate) {
        if (selectedApplication == null) {
            return;
        }

        try {
            String feedback = resolveFeedbackForSubmission(targetStatus);
            if (feedback == null) {
                return;
            }

            selectedApplication.setStatus(targetStatus.name());
            selectedApplication.setUpdatedAt(Timestamp.from(Instant.now()));
            reviewStatusChip.setText(targetStatus.getLabel());
            reviewStatusChip.getStyleClass().clear();
            reviewStatusChip.getStyleClass().add("job-offer-status-chip");
            applyStatusStyle(reviewStatusChip, targetStatus.name());
            statusTextLabel.setText(targetStatus.getDescription());
            selectedApplication.setStatusMessage(feedback.isEmpty() ? null : feedback);

            if (notifyCandidate) {
                selectedApplication.setStatusNotified((byte) 0);
                selectedApplication.setStatusNotifiedAt(null);
            }

            serviceJobApplication.update(selectedApplication);
            feedbackHelperLabel.setText("Submitted status: " + targetStatus.getLabel());
            loadApplications();
        } catch (Exception exception) {
            showError("Update failed", exception.getMessage());
        }
    }

    private String resolveFeedbackForSubmission(JobApplicationStatus targetStatus) {
        String feedback = feedbackArea.getText() == null ? "" : feedbackArea.getText().trim();
        if (!feedback.isEmpty()) {
            return feedback;
        }

        if (targetStatus == null) {
            return "";
        }

        String generatedFeedback = aiFeedbackService.generateFeedback(selectedApplication, targetStatus);
        feedbackArea.setText(generatedFeedback);
        feedbackHelperLabel.setText("AI feedback was generated automatically before submission.");
        return generatedFeedback == null ? "" : generatedFeedback.trim();
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

    private JobOffer findOfferById(Integer offerId) {
        if (offerId == null) {
            return null;
        }
        return ownedOffers.stream()
                .filter(offer -> offer != null && offer.getId() == offerId)
                .findFirst()
                .orElse(null);
    }

    private JobApplication findApplicationById(Integer applicationId) {
        if (applicationId == null) {
            return null;
        }
        return allApplications.stream()
                .filter(application -> application != null && application.getId() == applicationId)
                .findFirst()
                .orElse(null);
    }

    private JobOffer findBestDefaultOffer() {
        return ownedOffers.stream()
                .sorted(Comparator.<JobOffer>comparingInt(offer -> applicationsByOfferId.getOrDefault(offer.getId(), List.of()).size())
                        .reversed()
                        .thenComparing(this::offerSortDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(null);
    }

    private Timestamp offerSortDate(JobOffer offer) {
        if (offer == null) {
            return null;
        }
        return offer.getUpdatedAt() != null ? offer.getUpdatedAt() : offer.getPublishedAt();
    }

    private String resolveCandidateName(JobApplication application) {
        if (application == null || application.getUser() == null) {
            return "Unknown candidate";
        }

        String fullName = safeText(application.getUser().getName());
        if (!fullName.isEmpty()) {
            return fullName;
        }

        String email = safeText(application.getUser().getEmail());
        return email.isEmpty() ? "Unknown candidate" : email;
    }

    private String resolveCandidateEmail(JobApplication application) {
        if (application == null || application.getUser() == null) {
            return "No email";
        }
        return safeText(application.getUser().getEmail(), "No email");
    }

    private String resolveScoreCaption(int score) {
        if (score >= 75) {
            return "Strong match";
        }
        if (score >= 50) {
            return "Medium match";
        }
        return "Weak match";
    }

    private String buildMessagePreview(JobApplication application) {
        String message = safeText(application.getMessage(), "No message provided.");
        if (message.length() <= 110) {
            return message;
        }
        return message.substring(0, 107) + "...";
    }

    private void applyStatusStyle(Label statusLabel, String status) {
        statusLabel.getStyleClass().removeIf(style -> style.startsWith("job-offer-status-"));
        statusLabel.getStyleClass().add("job-offer-status-chip");

        JobApplicationStatus normalized = JobApplicationStatus.fromString(status);
        switch (normalized) {
            case ACCEPTED, HIRED -> statusLabel.getStyleClass().add("job-offer-status-active");
            case REJECTED, WITHDRAWN -> statusLabel.getStyleClass().add("job-offer-status-rejected");
            case REVIEWED, SHORTLISTED, INTERVIEW, OFFER_SENT -> statusLabel.getStyleClass().add("job-offer-status-pending");
            case SCREENING, SUBMITTED -> statusLabel.getStyleClass().add("job-offer-status-info");
        }
    }

    private String formatDate(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime().format(DATE_FORMATTER) : "Unknown";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(String value, String fallback) {
        String safeValue = safeText(value);
        return safeValue.isEmpty() ? fallback : safeValue;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
