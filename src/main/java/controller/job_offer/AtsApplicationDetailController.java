package controller.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import entities.User;
import entities.job_offer.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import service.job_offer.*;
import services.job_offer.ServiceJobApplication;
import util.AppNavigator;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * ATS Application Detail Controller.
 *
 * Shows full candidate detail for one application:
 * - Score hero card with colour-coded score circle
 * - Per-criterion score breakdown table
 * - Extracted candidate profile (skills, exp, education, languages)
 * - Stage mover with transition validation
 * - Cover letter / notes
 * - Audit history log
 * - Buttons: Recalculate Score, Extract CV via Gemini, Open CV file
 */
public class AtsApplicationDetailController implements Initializable {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label                  pageTitleLabel;
    @FXML private Label                  scoreBigLabel;
    @FXML private Label                  offerTitleLabel;
    @FXML private Label                  candidateNameLabel;
    @FXML private Label                  candidateEmailLabel;
    @FXML private Label                  stageChipLabel;
    @FXML private ComboBox<String>       moveStageCombo;

    @FXML private TableView<ScoreCriteria>          breakdownTable;
    @FXML private TableColumn<ScoreCriteria, String>  colCriterion;
    @FXML private TableColumn<ScoreCriteria, Integer> colWeight;
    @FXML private TableColumn<ScoreCriteria, Integer> colScore;
    @FXML private TableColumn<ScoreCriteria, String>  colExplanation;

    @FXML private TextArea  coverLetterArea;
    @FXML private TextField noteField;

    @FXML private Label     cvFilenameLabel;
    @FXML private Button    openCvButton;

    @FXML private Label     skillsLabel;
    @FXML private Label     expLabel;
    @FXML private Label     eduLabel;
    @FXML private Label     langsLabel;
    @FXML private Label     educationFieldLabel;
    @FXML private Label     portfolioUrlsLabel;
    @FXML private Label     extractionNoteLabel;

    @FXML private VBox      auditList;

    // ── State ─────────────────────────────────────────────────────────────────
    private JobApplication      application;
    private User                currentUser;
    private ServiceJobApplication serviceJobApplication;
    private AtsScoringEngine    scoringEngine;
    private GeminiCvExtractorService geminiService;
    private AtsApplicationScoringService applicationScoringService;
    private AtsAuditService     auditService;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        scoringEngine         = new AtsScoringEngine();
        geminiService         = new GeminiCvExtractorService();
        applicationScoringService = new AtsApplicationScoringService();
        auditService          = new AtsAuditService();
        setupBreakdownTable();
    }

    public void setApplication(JobApplication app) {
        this.application = app;
        if (app != null) {
            populateAll();
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    // ── Populate ──────────────────────────────────────────────────────────────

    private void populateAll() {
        populateHeader();
        populateScoreBreakdown();
        populateCoverLetter();
        populateCvInfo();
        populateCandidateProfile();
        populateMoveStage();
        populateAuditHistory();
    }

    private void populateHeader() {
        JobOffer offer  = application.getJobOffer();
        User    candidate = application.getUser();

        pageTitleLabel.setText("Application #" + application.getId());
        offerTitleLabel.setText(offer != null && offer.getTitle() != null ? offer.getTitle() : "Unknown Offer");

        String name = candidate != null && candidate.getName() != null
            ? candidate.getName().trim()
            : "Unknown";
        candidateNameLabel.setText(name.isBlank() ? "Unknown" : name);
        candidateEmailLabel.setText(candidate != null && candidate.getEmail() != null
            ? candidate.getEmail() : "—");

        // Score circle
        if (application.getScore() != null) {
            int s = application.getScore();
            scoreBigLabel.setText(String.valueOf(s));
            String color = s >= 75 ? "#00b894" : s >= 50 ? "#f39c12" : "#ff4f5e";
            scoreBigLabel.setStyle("-fx-text-fill: " + color +
                "; -fx-font-size: 30px; -fx-font-weight: 900; -fx-alignment: center;");
        } else {
            scoreBigLabel.setText("—");
        }

        // Stage chip
        JobApplicationStatus stage = JobApplicationStatus.fromString(application.getStatus());
        stageChipLabel.setText(stage.getLabel());
        applyStageChipStyle(stageChipLabel, stage.name());
    }

    private void populateScoreBreakdown() {
        ScoreBreakdown breakdown = scoringEngine.parseBreakdown(application.getScoreBreakdown());

        if (breakdown.getCriteria().isEmpty()) {
            breakdownTable.setItems(FXCollections.emptyObservableList());
            return;
        }

        breakdownTable.setItems(FXCollections.observableArrayList(breakdown.getCriteria()));
    }

    private void populateCoverLetter() {
        coverLetterArea.setText(
            application.getMessage() != null && !application.getMessage().isBlank()
                ? application.getMessage() : "No cover letter provided.");
    }

    private void populateCvInfo() {
        boolean hasCv = application.getCvFileName() != null && !application.getCvFileName().isBlank();
        cvFilenameLabel.setText(hasCv ? application.getCvFileName() : "No CV attached");
        openCvButton.setVisible(hasCv);
        openCvButton.setManaged(hasCv);
    }

    private void populateCandidateProfile() {
        String json = application.getExtractedData();
        if (json == null || json.isBlank()) {
            skillsLabel.setText("Not extracted yet");
            expLabel.setText("—");
            eduLabel.setText("—");
            langsLabel.setText("—");
            educationFieldLabel.setText("—");
            portfolioUrlsLabel.setText("—");
            extractionNoteLabel.setVisible(true);
            extractionNoteLabel.setManaged(true);
            return;
        }
        try {
            CandidateProfile profile = objectMapper.readValue(json, CandidateProfile.class);
            skillsLabel.setText(profile.getSkills().isEmpty() ? "—" : String.join(", ", profile.getSkills()));
            expLabel.setText(profile.getExperienceYears() + " years");
            eduLabel.setText(profile.getEducationLevel() != null ? profile.getEducationLevel() : "—");
            langsLabel.setText(profile.getLanguages().isEmpty() ? "—" : String.join(", ", profile.getLanguages()));
            educationFieldLabel.setText(profile.getEducationField() != null ? profile.getEducationField() : "—");
            portfolioUrlsLabel.setText(profile.getPortfolioUrls().isEmpty() ? "—" : String.join(", ", profile.getPortfolioUrls()));
            extractionNoteLabel.setVisible(false);
            extractionNoteLabel.setManaged(false);
        } catch (Exception e) {
            skillsLabel.setText("Failed to parse profile");
        }
    }

    private void populateMoveStage() {
        JobApplicationStatus current = JobApplicationStatus.fromString(application.getStatus());
        List<String> valid = Arrays.stream(JobApplicationStatus.values())
            .filter(s -> current.canTransitionTo(s))
            .map(JobApplicationStatus::name)
            .collect(Collectors.toList());
        moveStageCombo.setItems(FXCollections.observableArrayList(valid));
        moveStageCombo.setDisable(current.isTerminal());
    }

    private void populateAuditHistory() {
        auditList.getChildren().clear();
        List<AtsAuditLog> logs = auditService.getApplicationHistory(application.getId());
        if (logs.isEmpty()) {
            Label empty = new Label("No audit events yet.");
            empty.getStyleClass().add("job-offer-card-meta");
            auditList.getChildren().add(empty);
            return;
        }
        // Newest first
        for (int i = logs.size() - 1; i >= 0; i--) {
            auditList.getChildren().add(buildAuditRow(logs.get(i)));
        }
    }

    private HBox buildAuditRow(AtsAuditLog log) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.setStyle("-fx-background-color: derive(-ul-surface-1, 4%); "
            + "-fx-background-radius: 6;");

        String icon = switch (log.getAction()) {
            case AtsAuditLog.ACTION_STAGE_CHANGED    -> "🔀";
            case AtsAuditLog.ACTION_SCORE_CALCULATED -> "⚡";
            case AtsAuditLog.ACTION_NOTE_ADDED       -> "💬";
            case AtsAuditLog.ACTION_CV_EXTRACTED     -> "🤖";
            default                                  -> "📋";
        };

        Label iconLabel = new Label(icon);
        Label actionLabel = new Label(log.getAction().replace("_", " "));
        actionLabel.getStyleClass().add("job-offer-section-label");

        String detail = "";
        if (log.getOldValue() != null && log.getNewValue() != null) {
            detail = log.getOldValue() + " → " + log.getNewValue();
        } else if (log.getNewValue() != null) {
            detail = log.getNewValue();
        }
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("job-offer-card-meta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String dateStr = log.getCreatedAt() != null
            ? log.getCreatedAt().toLocalDateTime().format(DATE_FMT) : "";
        Label dateLabel = new Label(dateStr);
        dateLabel.getStyleClass().add("job-offer-card-meta");

        row.getChildren().addAll(iconLabel, actionLabel, detailLabel, spacer, dateLabel);
        return row;
    }

    // ── Breakdown Table Setup ─────────────────────────────────────────────────

    private void setupBreakdownTable() {
        colCriterion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colWeight.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getWeight()).asObject());
        colScore.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getPointsAwarded()).asObject());
        colScore.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer pts, boolean empty) {
                super.updateItem(pts, empty);
                if (empty || pts == null) { setText(null); return; }
                TableRow<?> row = getTableRow();
                if (row != null && row.getItem() instanceof ScoreCriteria sc) {
                    double ratio = sc.getWeight() > 0 ? (double) sc.getPointsAwarded() / sc.getWeight() : 0;
                    String color = ratio >= 0.75 ? "#00b894" : ratio >= 0.5 ? "#f39c12" : "#ff4f5e";
                    setText(pts + "/" + sc.getWeight());
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 800;");
                } else {
                    setText(String.valueOf(pts));
                }
            }
        });
        colExplanation.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getExplanation()));
        colExplanation.setCellFactory(col -> {
            TableCell<ScoreCriteria, String> cell = new TableCell<>() {
                @Override protected void updateItem(String text, boolean empty) {
                    super.updateItem(text, empty);
                    if (empty || text == null) { setText(null); return; }
                    setText(text);
                    setWrapText(true);
                }
            };
            return cell;
        });
    }

    // ── Action Handlers ───────────────────────────────────────────────────────

    @FXML
    private void onMoveStage() {
        String chosen = moveStageCombo.getValue();
        if (chosen == null || chosen.isBlank()) {
            showError("No stage selected", "Please select a pipeline stage.");
            return;
        }
        JobApplicationStatus current = JobApplicationStatus.fromString(application.getStatus());
        JobApplicationStatus next    = JobApplicationStatus.fromString(chosen);
        if (!current.canTransitionTo(next)) {
            showError("Invalid transition",
                "Cannot move from " + current.getLabel() + " to " + next.getLabel());
            return;
        }
        try {
            String old = application.getStatus();
            application.setStatus(chosen);
            application.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            serviceJobApplication.update(application);
            Integer actorId = currentUser != null ? currentUser.getId() : null;
            auditService.logStageChange(application.getId(), actorId, old, chosen);
            populateHeader();
            populateMoveStage();
            populateAuditHistory();
            showInfo("Stage moved", "Application moved to: " + next.getLabel());
        } catch (Exception e) {
            showError("Stage move failed", e.getMessage());
        }
    }

    @FXML
    private void onRecalculateScore() {
        if (application.getJobOffer() == null) {
            showError("Cannot score", "No job offer linked to this application."); return;
        }
        Thread t = new Thread(() -> {
            try {
                applicationScoringService.extractAndScore(application);
                serviceJobApplication.update(application);
                Integer actorId = currentUser != null ? currentUser.getId() : null;
                auditService.logScoreCalculated(application.getId(), actorId, application.getScore());
                Platform.runLater(() -> {
                    populateHeader();
                    populateScoreBreakdown();
                    populateAuditHistory();
                    showInfo("Score updated", "New score: " + application.getScore() + "/100");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Scoring failed", e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onExtractCv() {
        if (!geminiService.isEnabled()) {
            showInfo("Gemini not configured",
                "Set your API key in src/main/resources/config/ats-config.properties\n" +
                "  gemini.api.key=YOUR_KEY\n  gemini.enabled=true");
            return;
        }
        String cvPath = application.getCvFileName();
        if (cvPath == null || cvPath.isBlank()) {
            showError("No CV", "This application has no CV file attached."); return;
        }
        Thread t = new Thread(() -> {
            try {
                CandidateProfile profile = applicationScoringService.ensureExtractedData(application);
                String json = objectMapper.writeValueAsString(profile);
                application.setExtractedData(json);
                application.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
                serviceJobApplication.update(application);
                Integer actorId = currentUser != null ? currentUser.getId() : null;
                auditService.logCvExtracted(application.getId(), actorId);
                Platform.runLater(() -> {
                    populateCandidateProfile();
                    populateAuditHistory();
                    showInfo("Extraction complete", "CV profile extracted. Click 'Recalculate Score' to update score.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("CV extraction failed", e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onAddNote() {
        String note = noteField.getText();
        if (note == null || note.isBlank()) return;
        try {
            // Append note to statusMessage field (lightweight notes storage)
            String existing = application.getStatusMessage() != null ? application.getStatusMessage() : "";
            String combined = existing.isBlank() ? note.trim()
                : existing + "\n---\n" + note.trim();
            application.setStatusMessage(combined);
            application.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            serviceJobApplication.update(application);
            Integer actorId = currentUser != null ? currentUser.getId() : null;
            auditService.logNoteAdded(application.getId(), actorId, note.trim());
            noteField.clear();
            populateAuditHistory();
        } catch (Exception e) {
            showError("Note failed", e.getMessage());
        }
    }

    @FXML
    private void onOpenCv() {
        String cvPath = application.getCvFileName();
        if (cvPath == null || cvPath.isBlank()) return;
        try {
            File f = geminiService.resolveCvFile(cvPath);
            if (Desktop.isDesktopSupported()) { Desktop.getDesktop().open(f); }
        } catch (Exception e) {
            showError("Open failed", e.getMessage());
        }
    }

    @FXML
    private void onBack() {
        AppNavigator.showAtsPipelineBoard();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyStageChipStyle(Label chip, String stage) {
        chip.getStyleClass().removeIf(c -> c.startsWith("job-offer-status-"));
        chip.getStyleClass().add("job-offer-status-chip");
        if (stage == null) return;
        String cls = switch (stage.toUpperCase()) {
            case "HIRED", "ACCEPTED"     -> "job-offer-status-active";
            case "REJECTED", "WITHDRAWN" -> "job-offer-status-rejected";
            case "SCREENING"             -> "job-offer-status-info";
            case "SHORTLISTED", "INTERVIEW", "OFFER_SENT" -> "job-offer-status-pending";
            default                      -> "job-offer-status-closed";
        };
        chip.getStyleClass().add(cls);
    }

    private String safeJoin(String first, String last) {
        // kept for potential future use if User gets firstname/lastname fields
        String f = first != null ? first.trim() : "";
        String l = last  != null ? last.trim()  : "";
        return (f + " " + l).trim();
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setContentText(msg); a.showAndWait();
    }
    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setContentText(msg); a.showAndWait();
    }
}
