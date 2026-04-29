package controller.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import entities.User;
import entities.job_offer.CandidateProfile;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import entities.job_offer.ScoreBreakdown;
import entities.job_offer.ScoreCriteria;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import service.job_offer.AtsApplicationScoringService;
import service.job_offer.AtsScoringEngine;
import service.job_offer.GeminiApplicationFeedbackService;
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

    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Button backButton;
    @FXML private Button refreshButton;

    @FXML private Label offersCountLabel;
    @FXML private Label applicationsCountLabel;
    @FXML private Label pendingCountLabel;

    @FXML private Label stepOneBadge;
    @FXML private Label stepTwoBadge;
    @FXML private Label stepThreeBadge;
    @FXML private Label stepFourBadge;

    @FXML private HBox fixedStepOneActions;
    @FXML private HBox fixedStepTwoActions;
    @FXML private HBox fixedStepThreeActions;
    @FXML private HBox fixedStepFourActions;
    @FXML private BorderPane stepOnePane;
    @FXML private VBox stepTwoPane;
    @FXML private VBox stepThreePane;
    @FXML private VBox stepFourPane;
    @FXML private VBox donePane;

    @FXML private Label offersMetaLabel;
    @FXML private TextField offerSearchField;
    @FXML private VBox offersList;
    @FXML private Label selectedOfferHeroLabel;
    @FXML private Label selectedOfferHeroMetaLabel;
    @FXML private Button nextFromOfferButton;

    @FXML private Label selectedOfferLabel;
    @FXML private Label selectedOfferMetaLabel;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private VBox applicationsList;
    @FXML private Label emptyStateLabel;
    @FXML private Button scoreAllCandidatesButton;
    @FXML private Label candidatesHelperLabel;
    @FXML private Button nextFromCandidateButton;

    @FXML private Label reviewTitleLabel;
    @FXML private Label reviewSubtitleLabel;
    @FXML private Label reviewStatusChip;
    @FXML private Label candidateNameLabel;
    @FXML private Label candidateEmailLabel;
    @FXML private Label scoreLabel;
    @FXML private Label scoreCaptionLabel;
    @FXML private Label appliedDateLabel;
    @FXML private Label statusTextLabel;
    @FXML private TextArea messageArea;
    @FXML private VBox scoreBreakdownList;
    @FXML private Label profileSkillsLabel;
    @FXML private Label profileExperienceLabel;
    @FXML private Label profileEducationLabel;
    @FXML private Label profileLanguagesLabel;
    @FXML private Label profilePortfolioLabel;
    @FXML private Label profileExtractionNoteLabel;
    @FXML private Button openCvButton;
    @FXML private Button openAtsButton;

    @FXML private Label decisionCandidateLabel;
    @FXML private Label decisionOfferLabel;
    @FXML private ComboBox<String> feedbackDecisionCombo;
    @FXML private Button generateFeedbackButton;
    @FXML private Label feedbackHelperLabel;
    @FXML private TextArea feedbackArea;
    @FXML private Button submitDecisionButton;

    @FXML private Label doneTitleLabel;
    @FXML private Label doneSummaryLabel;

    private User currentUser;
    private ServiceJobApplication serviceJobApplication;
    private ServiceJobOffer serviceJobOffer;
    private ServiceUser serviceUser;
    private GeminiApplicationFeedbackService aiFeedbackService;
    private AtsScoringEngine atsScoringEngine;
    private AtsApplicationScoringService atsApplicationScoringService;
    private ObjectMapper objectMapper;

    private final ObservableList<JobApplication> allApplications = FXCollections.observableArrayList();
    private final ObservableList<JobOffer> ownedOffers = FXCollections.observableArrayList();
    private final Map<Integer, List<JobApplication>> applicationsByOfferId = new LinkedHashMap<>();

    private JobOffer selectedOffer;
    private JobApplication selectedApplication;
    private int currentStep = 1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        serviceJobOffer = new ServiceJobOffer();
        serviceUser = new ServiceUser();
        aiFeedbackService = new GeminiApplicationFeedbackService();
        atsScoringEngine = new AtsScoringEngine();
        atsApplicationScoringService = new AtsApplicationScoringService();
        objectMapper = new ObjectMapper();

        setupFilters();
        setupInteraction();
        resetStepOneSelection();
        resetStepTwoSelection();
        resetReviewPanel();
        resetDecisionPanel();
        showStep(1);

        Platform.runLater(this::loadApplications);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        pageTitleLabel.setText("Application Review");
        pageSubtitleLabel.setText("Pick an offer, select one candidate, review the dossier, then submit a final decision.");
        loadApplications();
    }

    private void setupFilters() {
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All statuses",
                JobApplicationStatus.SUBMITTED.name(),
                JobApplicationStatus.ACCEPTED.name(),
                JobApplicationStatus.REJECTED.name()
        ));
        statusFilterCombo.setValue("All statuses");

        feedbackDecisionCombo.setItems(FXCollections.observableArrayList(
                JobApplicationStatus.REVIEWED.name(),
                JobApplicationStatus.ACCEPTED.name(),
                JobApplicationStatus.REJECTED.name()
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
        selectedOffer = restoredOffer;

        if (selectedOffer == null) {
            resetStepOneSelection();
            resetStepTwoSelection();
            resetReviewPanel();
            resetDecisionPanel();
            showStep(1);
            return;
        }

        updateStepOneSelection();
        renderOffers();
        renderApplicationsForSelection();

        JobApplication restoredApplication = previousApplicationId == null ? null : findApplicationById(previousApplicationId);
        if (restoredApplication != null
                && restoredApplication.getJobOffer() != null
                && restoredApplication.getJobOffer().getId() == selectedOffer.getId()) {
            selectedApplication = restoredApplication;
            renderApplicationsForSelection();
            nextFromCandidateButton.setDisable(false);
            if (currentStep > 2) {
                populateReviewPanel(restoredApplication);
                populateDecisionPanel(restoredApplication);
            }
        } else {
            selectedApplication = null;
            resetStepTwoSelection();
            resetReviewPanel();
            resetDecisionPanel();
            if (currentStep > 2) {
                showStep(2);
            }
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
            selectedOffer = null;
            resetStepOneSelection();
            renderApplicationsForSelection();
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

        Label countLabel = new Label(applicationCount + (applicationCount == 1 ? " applicant" : " applicants"));
        countLabel.getStyleClass().addAll("job-offer-score-badge", "job-offer-review-offer-count");

        topRow.getChildren().addAll(typeLabel, spacer, countLabel);

        Label titleLabel = new Label(safeText(offer.getTitle(), "Untitled offer"));
        titleLabel.getStyleClass().add("job-offer-card-title");
        titleLabel.setWrapText(true);

        Label metaLabel = new Label("Location: " + safeText(offer.getLocation(), "Not specified"));
        metaLabel.getStyleClass().add("job-offer-card-meta");
        metaLabel.setWrapText(true);

        Label statusLabel = new Label(safeText(offer.getStatus(), "PENDING"));
        statusLabel.getStyleClass().add("job-offer-status-chip");
        applyStatusStyle(statusLabel, offer.getStatus());

        Label footerLabel = new Label(applicationCount == 0
                ? "No applications yet."
                : "Click to select this offer for review.");
        footerLabel.getStyleClass().add("job-offer-card-meta");
        footerLabel.setWrapText(true);

        HBox actionRow = new HBox(8);
        actionRow.getStyleClass().add("job-offer-review-offer-actions");

        Button viewButton = createOfferActionButton("View Details", "ghost-button", event -> {
            event.consume();
            AppNavigator.showJobOfferDetail(offer);
        });

        Button editButton = createOfferActionButton("Edit", "ghost-button", event -> {
            event.consume();
            AppNavigator.showJobOfferForm(offer);
        });

        Button deleteButton = createOfferActionButton("Delete", "job-offer-delete-button", event -> {
            event.consume();
            deleteOffer(offer);
        });

        actionRow.getChildren().addAll(viewButton, editButton, deleteButton);

        card.getChildren().addAll(topRow, titleLabel, metaLabel, statusLabel, footerLabel, actionRow);
        card.setOnMouseClicked(event -> selectOffer(offer));
        return card;
    }

    private Button createOfferActionButton(String text, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("job-offer-card-button", "job-offer-review-offer-action-button", styleClass);
        button.setFocusTraversable(false);
        button.setOnAction(action);
        return button;
    }

    private void deleteOffer(JobOffer offer) {
        if (!canManageOffer(offer)) {
            showError("Access denied", "You can only delete your own offers.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Delete job offer");
        confirmDialog.setHeaderText(null);
        confirmDialog.setContentText("Delete \"" + safeText(offer != null ? offer.getTitle() : null, "this offer") + "\"?");

        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            serviceJobOffer.delete(offer);
            if (selectedOffer != null && offer != null && selectedOffer.getId() == offer.getId()) {
                selectedOffer = null;
                selectedApplication = null;
                resetStepOneSelection();
                resetStepTwoSelection();
                resetReviewPanel();
                resetDecisionPanel();
                showStep(1);
            }
            loadApplications();
        } catch (Exception exception) {
            showError("Delete failed", exception.getMessage());
        }
    }

    private boolean canManageOffer(JobOffer offer) {
        if (offer == null || currentUser == null || offer.getUser() == null || offer.getUser().getId() == null) {
            return false;
        }
        return RoleGuard.isAdmin(currentUser) || offer.getUser().getId().equals(currentUser.getId());
    }

    private void selectOffer(JobOffer offer) {
        selectedOffer = offer;
        selectedApplication = null;
        renderOffers();
        updateStepOneSelection();
        renderApplicationsForSelection();
        resetReviewPanel();
        resetDecisionPanel();
        if (currentStep > 2) {
            showStep(2);
        }
    }

    private void renderApplicationsForSelection() {
        applicationsList.getChildren().clear();

        List<JobApplication> filteredApplications = getFilteredApplicationsForSelectedOffer();

        boolean empty = filteredApplications.isEmpty();
        emptyStateLabel.setManaged(empty);
        emptyStateLabel.setVisible(empty);

        if (selectedOffer == null) {
            emptyStateLabel.setText("Select a job offer first.");
            resetStepTwoSelection();
            return;
        }

        emptyStateLabel.setText("No candidates match the current filter for this offer.");
        candidatesHelperLabel.setText("Candidates are sorted by ATS score, highest first.");

        for (JobApplication application : filteredApplications) {
            applicationsList.getChildren().add(buildApplicationCard(application));
        }

        if (selectedApplication != null && filteredApplications.stream().noneMatch(app -> app.getId() == selectedApplication.getId())) {
            selectedApplication = null;
            resetStepTwoSelection();
            if (currentStep > 2) {
                showStep(2);
            }
        } else {
            updateStepTwoSelection();
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
                .sorted(Comparator
                        .comparing(JobApplication::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(JobApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
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

        HBox layoutRow = new HBox(14);

        VBox contentBox = new VBox(10);
        HBox.setHgrow(contentBox, Priority.ALWAYS);

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

        metaRow.getChildren().add(dateLabel);

        Label messagePreview = new Label(buildMessagePreview(application));
        messagePreview.getStyleClass().add("job-offer-card-meta");
        messagePreview.setWrapText(true);

        VBox scorePanel = new VBox(4);
        scorePanel.getStyleClass().addAll("job-offer-review-candidate-score-panel", resolveScorePanelStyle(application.getScore()));
        scorePanel.setAlignment(Pos.CENTER);

        Label scoreValueLabel = new Label(application.getScore() == null ? "—" : application.getScore().toString());
        scoreValueLabel.getStyleClass().add("job-offer-review-candidate-score-value");

        Label scoreUnitLabel = new Label(application.getScore() == null ? "No ATS score" : "ATS / 100");
        scoreUnitLabel.getStyleClass().add("job-offer-review-candidate-score-unit");
        scoreUnitLabel.setWrapText(true);

        scorePanel.getChildren().addAll(scoreValueLabel, scoreUnitLabel);

        contentBox.getChildren().addAll(topRow, metaRow, messagePreview);
        layoutRow.getChildren().addAll(contentBox, scorePanel);

        card.getChildren().add(layoutRow);
        card.setOnMouseClicked(event -> selectApplication(application));
        return card;
    }

    private String resolveScorePanelStyle(Integer score) {
        if (score == null) {
            return "job-offer-review-candidate-score-none";
        }
        if (score >= 85) {
            return "job-offer-review-candidate-score-high";
        }
        if (score >= 70) {
            return "job-offer-review-candidate-score-good";
        }
        if (score >= 50) {
            return "job-offer-review-candidate-score-mid";
        }
        return "job-offer-review-candidate-score-low";
    }

    private void selectApplication(JobApplication application) {
        selectedApplication = application;
        renderApplicationsForSelection();
        nextFromCandidateButton.setDisable(selectedApplication == null);
        if (currentStep > 2 && application != null) {
            populateReviewPanel(application);
            populateDecisionPanel(application);
        }
    }

    private void populateReviewPanel(JobApplication application) {
        if (application == null) {
            resetReviewPanel();
            return;
        }

        reviewTitleLabel.setText(resolveCandidateName(application));
        reviewSubtitleLabel.setText(safeText(application.getJobOffer() != null ? application.getJobOffer().getTitle() : null, "Unknown offer"));

        JobApplicationStatus status = JobApplicationStatus.fromString(application.getStatus());
        reviewStatusChip.setText(status.getLabel());
        reviewStatusChip.getStyleClass().clear();
        reviewStatusChip.getStyleClass().add("job-offer-status-chip");
        applyStatusStyle(reviewStatusChip, status.name());

        candidateNameLabel.setText(resolveCandidateName(application));
        candidateEmailLabel.setText(resolveCandidateEmail(application));
        appliedDateLabel.setText(formatDate(application.getCreatedAt()));
        statusTextLabel.setText(status.getDescription());
        messageArea.setText(safeText(application.getMessage(), "No motivation letter provided."));

        if (application.getScore() == null) {
            scoreLabel.setText("Not scored");
            scoreCaptionLabel.setText("No ATS score available yet.");
        } else {
            scoreLabel.setText(application.getScore() + "/100");
            scoreCaptionLabel.setText(resolveScoreCaption(application.getScore()));
        }

        renderScoreBreakdown(application);
        renderCandidateProfile(application);

        boolean hasCv = application.getCvFileName() != null && !application.getCvFileName().trim().isEmpty();
        openCvButton.setDisable(!hasCv);
        openAtsButton.setDisable(false);
    }

    private void renderScoreBreakdown(JobApplication application) {
        scoreBreakdownList.getChildren().clear();

        ScoreBreakdown breakdown = atsScoringEngine.parseBreakdown(application.getScoreBreakdown());
        if (breakdown.getCriteria() == null || breakdown.getCriteria().isEmpty()) {
            Label emptyLabel = new Label("No ATS breakdown available for this application.");
            emptyLabel.getStyleClass().add("job-offer-card-meta");
            scoreBreakdownList.getChildren().add(emptyLabel);
            return;
        }

        for (ScoreCriteria criteria : breakdown.getCriteria()) {
            VBox row = new VBox(4);
            row.getStyleClass().add("job-offer-review-breakdown-row");

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(safeText(criteria.getName(), "Criterion"));
            nameLabel.getStyleClass().add("job-offer-section-label");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label valueLabel = new Label(criteria.getPointsAwarded() + "/" + criteria.getWeight());
            valueLabel.getStyleClass().add("job-offer-score-badge");

            header.getChildren().addAll(nameLabel, spacer, valueLabel);

            Label explanationLabel = new Label(safeText(criteria.getExplanation(), "No explanation available."));
            explanationLabel.getStyleClass().add("job-offer-card-meta");
            explanationLabel.setWrapText(true);

            row.getChildren().addAll(header, explanationLabel);
            scoreBreakdownList.getChildren().add(row);
        }
    }

    private void renderCandidateProfile(JobApplication application) {
        String json = application.getExtractedData();
        if (json == null || json.isBlank()) {
            profileSkillsLabel.setText("Not extracted yet");
            profileExperienceLabel.setText("—");
            profileEducationLabel.setText("—");
            profileLanguagesLabel.setText("—");
            profilePortfolioLabel.setText("—");
            profileExtractionNoteLabel.setText("No extracted profile is available for this candidate yet.");
            profileExtractionNoteLabel.setVisible(true);
            profileExtractionNoteLabel.setManaged(true);
            return;
        }

        try {
            CandidateProfile profile = objectMapper.readValue(json, CandidateProfile.class);
            profileSkillsLabel.setText(profile.getSkills().isEmpty() ? "—" : String.join(", ", profile.getSkills()));
            profileExperienceLabel.setText(profile.getExperienceYears() <= 0 ? "—" : profile.getExperienceYears() + " years");

            String educationLevel = safeText(profile.getEducationLevel());
            String educationField = safeText(profile.getEducationField());
            if (!educationLevel.isEmpty() && !educationField.isEmpty()) {
                profileEducationLabel.setText(educationLevel + " • " + educationField);
            } else if (!educationLevel.isEmpty()) {
                profileEducationLabel.setText(educationLevel);
            } else if (!educationField.isEmpty()) {
                profileEducationLabel.setText(educationField);
            } else {
                profileEducationLabel.setText("—");
            }

            profileLanguagesLabel.setText(profile.getLanguages().isEmpty() ? "—" : String.join(", ", profile.getLanguages()));
            profilePortfolioLabel.setText(profile.getPortfolioUrls().isEmpty() ? "—" : String.join(", ", profile.getPortfolioUrls()));
            profileExtractionNoteLabel.setVisible(false);
            profileExtractionNoteLabel.setManaged(false);
        } catch (Exception exception) {
            profileSkillsLabel.setText("Profile unavailable");
            profileExperienceLabel.setText("—");
            profileEducationLabel.setText("—");
            profileLanguagesLabel.setText("—");
            profilePortfolioLabel.setText("—");
            profileExtractionNoteLabel.setText("Stored extracted profile could not be parsed.");
            profileExtractionNoteLabel.setVisible(true);
            profileExtractionNoteLabel.setManaged(true);
        }
    }

    private void populateDecisionPanel(JobApplication application) {
        if (application == null) {
            resetDecisionPanel();
            return;
        }

        decisionCandidateLabel.setText(resolveCandidateName(application));
        decisionOfferLabel.setText(safeText(application.getJobOffer() != null ? application.getJobOffer().getTitle() : null, "Unknown offer"));

        JobApplicationStatus status = JobApplicationStatus.fromString(application.getStatus());
        if (status == JobApplicationStatus.ACCEPTED
                || status == JobApplicationStatus.REJECTED
                || status == JobApplicationStatus.REVIEWED) {
            feedbackDecisionCombo.setValue(status.name());
        } else {
            feedbackDecisionCombo.setValue(JobApplicationStatus.REVIEWED.name());
        }

        feedbackArea.setText(safeText(application.getStatusMessage(), ""));
        generateFeedbackButton.setDisable(false);
        submitDecisionButton.setDisable(false);
        feedbackHelperLabel.setText("Pick a status, generate AI feedback if needed, then submit.");
    }

    private void showStep(int step) {
        currentStep = step;

        setStepState(stepOnePane, step == 1);
        setStepState(stepTwoPane, step == 2);
        setStepState(stepThreePane, step == 3);
        setStepState(stepFourPane, step == 4);
        setStepState(donePane, step == 5);
        setStepState(fixedStepOneActions, step == 1);
        setStepState(fixedStepTwoActions, step == 2);
        setStepState(fixedStepThreeActions, step == 3);
        setStepState(fixedStepFourActions, step == 4);

        updateStepBadge(stepOneBadge, 1, step);
        updateStepBadge(stepTwoBadge, 2, step);
        updateStepBadge(stepThreeBadge, 3, step);
        updateStepBadge(stepFourBadge, 4, step);
    }

    private void setStepState(Region pane, boolean active) {
        pane.setManaged(active);
        pane.setVisible(active);
    }

    private void updateStepBadge(Label badge, int badgeStep, int activeStep) {
        badge.getStyleClass().removeAll("job-offer-step-badge-active", "job-offer-step-badge-complete");
        if (activeStep > 4 && badgeStep == 4) {
            badge.getStyleClass().add("job-offer-step-badge-complete");
            return;
        }
        if (badgeStep < activeStep) {
            badge.getStyleClass().add("job-offer-step-badge-complete");
        } else if (badgeStep == activeStep) {
            badge.getStyleClass().add("job-offer-step-badge-active");
        }
    }

    private void updateStepOneSelection() {
        if (selectedOffer == null) {
            resetStepOneSelection();
            return;
        }

        int count = applicationsByOfferId.getOrDefault(selectedOffer.getId(), List.of()).size();
        selectedOfferHeroLabel.setText(safeText(selectedOffer.getTitle(), "Untitled offer"));
        selectedOfferHeroMetaLabel.setText(count + (count == 1 ? " candidate" : " candidates")
                + " • " + safeText(selectedOffer.getLocation(), "Location not specified"));
        nextFromOfferButton.setDisable(false);
    }

    private void resetStepOneSelection() {
        selectedOfferHeroLabel.setText("No offer selected yet");
        selectedOfferHeroMetaLabel.setText("Click any offer card to select it, then continue.");
        nextFromOfferButton.setDisable(true);
    }

    private void updateStepTwoSelection() {
        if (selectedOffer == null) {
            selectedOfferLabel.setText("Select a job offer first");
            selectedOfferMetaLabel.setText("Candidates will appear here.");
            scoreAllCandidatesButton.setDisable(true);
            nextFromCandidateButton.setDisable(true);
            return;
        }

        int count = getFilteredApplicationsForSelectedOffer().size();
        selectedOfferLabel.setText(safeText(selectedOffer.getTitle(), "Untitled offer"));
        selectedOfferMetaLabel.setText(count + (count == 1 ? " visible candidate" : " visible candidates"));
        scoreAllCandidatesButton.setDisable(count == 0);
        nextFromCandidateButton.setDisable(selectedApplication == null);
    }

    private void resetStepTwoSelection() {
        if (selectedOffer == null) {
            selectedOfferLabel.setText("Select a job offer first");
            selectedOfferMetaLabel.setText("Candidates will appear here.");
            candidatesHelperLabel.setText("Pick an offer first, then you can calculate ATS scores for all of its candidates.");
        } else {
            updateStepTwoSelection();
            candidatesHelperLabel.setText("You can calculate ATS scores for every visible candidate, then review them from highest to lowest.");
        }
        scoreAllCandidatesButton.setDisable(selectedOffer == null);
        nextFromCandidateButton.setDisable(true);
    }

    private void resetReviewPanel() {
        reviewTitleLabel.setText("Review & score");
        reviewSubtitleLabel.setText("Select a candidate in step 2 to load the full ATS breakdown, profile, and cover letter.");
        reviewStatusChip.getStyleClass().clear();
        reviewStatusChip.getStyleClass().addAll("job-offer-status-chip", "job-offer-status-closed");
        reviewStatusChip.setText("Waiting");
        candidateNameLabel.setText("No candidate selected");
        candidateEmailLabel.setText("Candidate details will appear here.");
        scoreLabel.setText("Not scored");
        scoreCaptionLabel.setText("ATS details will appear here.");
        appliedDateLabel.setText("—");
        statusTextLabel.setText("—");
        messageArea.setText("No cover letter loaded.");
        scoreBreakdownList.getChildren().setAll(new Label("No ATS breakdown available."));
        profileSkillsLabel.setText("—");
        profileExperienceLabel.setText("—");
        profileEducationLabel.setText("—");
        profileLanguagesLabel.setText("—");
        profilePortfolioLabel.setText("—");
        profileExtractionNoteLabel.setText("No extracted profile is available for this candidate yet.");
        profileExtractionNoteLabel.setVisible(true);
        profileExtractionNoteLabel.setManaged(true);
        openCvButton.setDisable(true);
        openAtsButton.setDisable(true);
    }

    private void resetDecisionPanel() {
        decisionCandidateLabel.setText("No candidate selected");
        decisionOfferLabel.setText("Pick a candidate in step 2 first.");
        feedbackDecisionCombo.setValue(JobApplicationStatus.REVIEWED.name());
        feedbackArea.setText("");
        generateFeedbackButton.setDisable(true);
        submitDecisionButton.setDisable(true);
        feedbackHelperLabel.setText("Step 4 unlocks after you review a selected candidate.");
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
    private void onContinueToCandidates() {
        if (selectedOffer == null) {
            showError("Offer required", "Pick an offer before continuing.");
            return;
        }
        showStep(2);
    }

    @FXML
    private void onBackToOffers() {
        showStep(1);
    }

    @FXML
    private void onScoreAllCandidates() {
        if (selectedOffer == null) {
            showError("Offer required", "Pick an offer before calculating ATS scores.");
            return;
        }

        List<JobApplication> candidatesToScore = getFilteredApplicationsForSelectedOffer();
        if (candidatesToScore.isEmpty()) {
            showError("No candidates", "There are no visible candidates to score for this offer.");
            return;
        }

        scoreAllCandidatesButton.setDisable(true);
        candidatesHelperLabel.setText("Calculating ATS scores for visible candidates...");

        Thread thread = new Thread(() -> {
            int successCount = 0;
            int failedCount = 0;

            for (JobApplication application : candidatesToScore) {
                try {
                    atsApplicationScoringService.extractAndScore(application);
                    application.setUpdatedAt(Timestamp.from(Instant.now()));
                    serviceJobApplication.update(application);
                    successCount++;
                } catch (Exception exception) {
                    failedCount++;
                }
            }

            final int scored = successCount;
            final int failed = failedCount;
            Platform.runLater(() -> {
                scoreAllCandidatesButton.setDisable(false);
                loadApplications();
                if (failed == 0) {
                    candidatesHelperLabel.setText("Scored " + scored + " candidate" + (scored == 1 ? "" : "s") + ". List sorted highest to lowest.");
                } else {
                    candidatesHelperLabel.setText("Scored " + scored + " candidate" + (scored == 1 ? "" : "s")
                            + ", " + failed + " failed. List sorted by available ATS scores.");
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onContinueToReview() {
        if (selectedApplication == null) {
            showError("Candidate required", "Pick a candidate before continuing.");
            return;
        }
        populateReviewPanel(selectedApplication);
        populateDecisionPanel(selectedApplication);
        showStep(3);
    }

    @FXML
    private void onBackToCandidates() {
        showStep(2);
    }

    @FXML
    private void onContinueToDecision() {
        if (selectedApplication == null) {
            showError("Candidate required", "Pick a candidate before continuing.");
            return;
        }
        populateDecisionPanel(selectedApplication);
        showStep(4);
    }

    @FXML
    private void onBackToReview() {
        showStep(3);
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
                    feedbackHelperLabel.setText("AI message generated. You can edit it before submission.");
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
    private void onReviewAnother() {
        selectedApplication = null;
        renderApplicationsForSelection();
        resetReviewPanel();
        resetDecisionPanel();
        showStep(selectedOffer == null ? 1 : 2);
    }

    @FXML
    private void onDoneBackToOffers() {
        showStep(1);
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
            selectedApplication.setStatusMessage(feedback.isEmpty() ? null : feedback);

            if (notifyCandidate) {
                selectedApplication.setStatusNotified((byte) 0);
                selectedApplication.setStatusNotifiedAt(null);
            }

            serviceJobApplication.update(selectedApplication);

            doneTitleLabel.setText("Decision submitted");
            doneSummaryLabel.setText("Candidate: " + resolveCandidateName(selectedApplication)
                    + "\nOffer: " + safeText(selectedApplication.getJobOffer() != null ? selectedApplication.getJobOffer().getTitle() : null, "Unknown offer")
                    + "\nStatus: " + targetStatus.getLabel());

            feedbackHelperLabel.setText("Submitted status: " + targetStatus.getLabel());
            loadApplications();
            showStep(5);
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
