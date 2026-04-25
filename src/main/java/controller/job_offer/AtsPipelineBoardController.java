package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import service.job_offer.AtsScoringEngine;
import service.job_offer.AtsAuditService;
import services.job_offer.ServiceJobApplication;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * ATS Pipeline Board Controller.
 *
 * Shows all applications for a selected job offer grouped/filtered by pipeline stage.
 * Supports: stage filters, search by candidate name/email, score computation,
 * bulk score-all, stage moves, and CSV export.
 */
public class AtsPipelineBoardController implements Initializable {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<JobOffer>           offerComboBox;
    @FXML private ComboBox<String>             stageFilterCombo;
    @FXML private TextField                    searchField;
    @FXML private Label                        statsLabel;
    @FXML private Label                        syncStatusLabel;
    @FXML private TableView<JobApplication>    applicationsTable;
    @FXML private TableColumn<JobApplication, Integer> colScore;
    @FXML private TableColumn<JobApplication, String>  colCandidate;
    @FXML private TableColumn<JobApplication, String>  colEmail;
    @FXML private TableColumn<JobApplication, String>  colStage;
    @FXML private TableColumn<JobApplication, String>  colApplied;
    @FXML private TableColumn<JobApplication, Void>    colActions;

    @FXML private Button tabAll;
    @FXML private Button tabSubmitted;
    @FXML private Button tabScreening;
    @FXML private Button tabShortlisted;
    @FXML private Button tabInterview;
    @FXML private Button tabOfferSent;
    @FXML private Button tabHired;
    @FXML private Button tabRejected;

    // ── State ─────────────────────────────────────────────────────────────────
    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private ServiceJobApplication serviceJobApplication;
    private AtsScoringEngine scoringEngine;
    private AtsAuditService auditService;

    private ObservableList<JobApplication> allApplications = FXCollections.observableArrayList();
    private FilteredList<JobApplication>   filteredApplications;
    private String activeStageTab = "ALL";

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobOffer      = new ServiceJobOffer();
        serviceJobApplication = new ServiceJobApplication();
        scoringEngine        = new AtsScoringEngine();
        auditService         = new AtsAuditService();

        filteredApplications = new FilteredList<>(allApplications, p -> true);

        setupTable();
        setupFilters();
        loadOffers();

        syncStatusLabel.setText("ⓘ Sync: not configured");
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    // ── Offer Loading ─────────────────────────────────────────────────────────

    private void loadOffers() {
        Thread t = new Thread(() -> {
            try {
                List<JobOffer> offers = serviceJobOffer.getALL();
                Platform.runLater(() -> {
                    offerComboBox.setItems(FXCollections.observableArrayList(offers));
                    offerComboBox.setConverter(new javafx.util.StringConverter<>() {
                        @Override public String toString(JobOffer o) {
                            return o == null ? "" : o.getTitle() + " [" + o.getStatus() + "]";
                        }
                        @Override public JobOffer fromString(String s) { return null; }
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to load offers", e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML private void onOfferSelected() {
        JobOffer selected = offerComboBox.getValue();
        if (selected == null) return;
        loadApplicationsForOffer(selected.getId());
    }

    private void loadApplicationsForOffer(int offerId) {
        Thread t = new Thread(() -> {
            try {
                List<JobApplication> apps = serviceJobApplication.getALL().stream()
                        .filter(a -> a.getJobOffer() != null && a.getJobOffer().getId() == offerId)
                        .collect(Collectors.toList());
                Platform.runLater(() -> {
                    allApplications.setAll(apps);
                    applyFilters();
                    updateStats();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to load applications", e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Table Setup ───────────────────────────────────────────────────────────

    private void setupTable() {
        colScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        colScore.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer score, boolean empty) {
                super.updateItem(score, empty);
                if (empty || score == null) {
                    setText("—");
                    setStyle("");
                } else {
                    setText(score + "/100");
                    String color = score >= 70 ? "#00b894" : score >= 40 ? "#f39c12" : "#ff4f5e";
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 800;");
                }
            }
        });

        colCandidate.setCellValueFactory(cell -> {
            User u = cell.getValue().getUser();
            String name = u != null
                ? (u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                : "Unknown";
            return new SimpleStringProperty(name);
        });

        colEmail.setCellValueFactory(cell -> {
            User u = cell.getValue().getUser();
            return new SimpleStringProperty(u != null ? u.getEmail() : "—");
        });

        colStage.setCellValueFactory(cell -> {
            JobApplicationStatus s = JobApplicationStatus.fromString(cell.getValue().getStatus());
            return new SimpleStringProperty(s.getLabel());
        });
        colStage.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String stage, boolean empty) {
                super.updateItem(stage, empty);
                if (empty || stage == null) { setText(null); setGraphic(null); return; }
                Label chip = new Label(stage);
                chip.getStyleClass().add("job-offer-status-chip");
                applyStageChipStyle(chip, stage);
                setGraphic(chip);
                setText(null);
            }
        });

        colApplied.setCellValueFactory(cell -> {
            Timestamp ts = cell.getValue().getCreatedAt();
            return new SimpleStringProperty(ts != null ? ts.toLocalDateTime().format(DATE_FMT) : "—");
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn  = new Button("View");
            private final Button moveBtn  = new Button("Move Stage");
            private final Button scoreBtn = new Button("Score");
            {
                viewBtn.getStyleClass().addAll("ghost-button", "job-offer-card-button");
                moveBtn.getStyleClass().addAll("ghost-button", "job-offer-card-button");
                scoreBtn.getStyleClass().addAll("ghost-button", "job-offer-card-button");

                viewBtn.setOnAction(e -> {
                    JobApplication app = getTableView().getItems().get(getIndex());
                    AppNavigator.showAtsApplicationDetail(app);
                });
                scoreBtn.setOnAction(e -> {
                    JobApplication app = getTableView().getItems().get(getIndex());
                    scoreApplication(app);
                });
                moveBtn.setOnAction(e -> {
                    JobApplication app = getTableView().getItems().get(getIndex());
                    showMoveStageDialog(app);
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox box = new HBox(6, viewBtn, scoreBtn, moveBtn);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        applicationsTable.setItems(filteredApplications);
    }

    private void setupFilters() {
        stageFilterCombo.setItems(FXCollections.observableArrayList(
            "All", "SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW",
            "OFFER_SENT", "HIRED", "REJECTED", "WITHDRAWN"
        ));
        stageFilterCombo.setValue("All");
        stageFilterCombo.setOnAction(e -> applyFilters());
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        offerComboBox.setOnAction(e -> onOfferSelected());
    }

    private void applyFilters() {
        String search   = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String stageVal = stageFilterCombo.getValue();

        filteredApplications.setPredicate(app -> {
            // Stage tab filter
            if (!"ALL".equals(activeStageTab)) {
                JobApplicationStatus current = JobApplicationStatus.fromString(app.getStatus());
                if (!current.name().equals(activeStageTab)) return false;
            }
            // Combo filter
            if (stageVal != null && !"All".equals(stageVal)) {
                if (!stageVal.equalsIgnoreCase(app.getStatus())) return false;
            }
            // Search
            if (!search.isEmpty()) {
                User u = app.getUser();
                String candidateText = u == null ? "" :
                    ((u.getName()  != null ? u.getName()  : "") + " " +
                     (u.getEmail() != null ? u.getEmail() : "")).toLowerCase();
                if (!candidateText.contains(search)) return false;
            }
            return true;
        });

        updateStats();
    }

    private void updateStats() {
        int total    = allApplications.size();
        int filtered = filteredApplications.size();
        int scored   = (int) allApplications.stream()
            .filter(a -> a.getScore() != null && a.getScore() > 0).count();
        statsLabel.setText(String.format("Showing %d / %d applications · %d scored", filtered, total, scored));
    }

    // ── Stage Tab Handlers ────────────────────────────────────────────────────

    @FXML private void onTabAll()         { setActiveTab("ALL");         }
    @FXML private void onTabSubmitted()   { setActiveTab("SUBMITTED");   }
    @FXML private void onTabScreening()   { setActiveTab("SCREENING");   }
    @FXML private void onTabShortlisted() { setActiveTab("SHORTLISTED"); }
    @FXML private void onTabInterview()   { setActiveTab("INTERVIEW");   }
    @FXML private void onTabOfferSent()   { setActiveTab("OFFER_SENT");  }
    @FXML private void onTabHired()       { setActiveTab("HIRED");       }
    @FXML private void onTabRejected()    { setActiveTab("REJECTED");    }

    private void setActiveTab(String stage) {
        activeStageTab = stage;
        // Visual: reset all tab buttons, highlight active
        List<Button> tabs = List.of(tabAll, tabSubmitted, tabScreening, tabShortlisted,
                                     tabInterview, tabOfferSent, tabHired, tabRejected);
        for (Button b : tabs) {
            b.getStyleClass().remove("primary-button");
            if (!b.getStyleClass().contains("ghost-button")) b.getStyleClass().add("ghost-button");
        }
        Button active = switch (stage) {
            case "SUBMITTED"   -> tabSubmitted;
            case "SCREENING"   -> tabScreening;
            case "SHORTLISTED" -> tabShortlisted;
            case "INTERVIEW"   -> tabInterview;
            case "OFFER_SENT"  -> tabOfferSent;
            case "HIRED"       -> tabHired;
            case "REJECTED"    -> tabRejected;
            default            -> tabAll;
        };
        active.getStyleClass().remove("ghost-button");
        active.getStyleClass().add("primary-button");
        applyFilters();
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private void scoreApplication(JobApplication app) {
        if (app.getJobOffer() == null) { showError("Error", "No job offer linked."); return; }
        Thread t = new Thread(() -> {
            try {
                scoringEngine.score(app);
                serviceJobApplication.update(app);
                Integer actorId = currentUser != null ? currentUser.getId() : null;
                auditService.logScoreCalculated(app.getId(), actorId, app.getScore());
                Platform.runLater(() -> applicationsTable.refresh());
            } catch (Exception e) {
                Platform.runLater(() -> showError("Scoring failed", e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML private void onScoreAll() {
        List<JobApplication> unscored = allApplications.stream()
            .filter(a -> a.getScore() == null || a.getScore() == 0)
            .collect(Collectors.toList());
        if (unscored.isEmpty()) { showInfo("Nothing to score", "All applications already have scores."); return; }
        unscored.forEach(this::scoreApplication);
        showInfo("Scoring started", "Scoring " + unscored.size() + " applications in background…");
    }

    // ── Move Stage Dialog ─────────────────────────────────────────────────────

    private void showMoveStageDialog(JobApplication app) {
        JobApplicationStatus current = JobApplicationStatus.fromString(app.getStatus());
        if (current.isTerminal()) {
            showInfo("Terminal stage", "This application is in a terminal stage and cannot be moved.");
            return;
        }

        List<String> validNextStages = Arrays.stream(JobApplicationStatus.values())
            .filter(s -> current.canTransitionTo(s))
            .map(s -> s.name())
            .collect(Collectors.toList());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(validNextStages.get(0), validNextStages);
        dialog.setTitle("Move Pipeline Stage");
        dialog.setHeaderText("Move: " + (app.getUser() != null ? app.getUser().getEmail() : "candidate"));
        dialog.setContentText("Select next stage:");
        dialog.showAndWait().ifPresent(chosen -> {
            try {
                String oldStage = app.getStatus();
                app.setStatus(chosen);
                app.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
                serviceJobApplication.update(app);
                Integer actorId = currentUser != null ? currentUser.getId() : null;
                auditService.logStageChange(app.getId(), actorId, oldStage, chosen);
                applicationsTable.refresh();
                updateStats();
            } catch (Exception e) {
                showError("Stage move failed", e.getMessage());
            }
        });
    }

    // ── CSV Export ────────────────────────────────────────────────────────────

    @FXML private void onExportCsv() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Applications CSV");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("ats_applications.csv");
        File file = chooser.showSaveDialog(applicationsTable.getScene().getWindow());
        if (file == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("ID,Candidate,Email,Stage,Score,Applied");
            for (JobApplication a : filteredApplications) {
                User u = a.getUser();
                String name  = u != null
                    ? (u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                    : "Unknown";
                String email = u != null ? u.getEmail() : "";
                String ts    = a.getCreatedAt() != null ? a.getCreatedAt().toLocalDateTime().format(DATE_FMT) : "";
                pw.printf("%d,\"%s\",\"%s\",%s,%s,%s%n",
                    a.getId(), name, email,
                    a.getStatus(), a.getScore() != null ? a.getScore() : "",
                    ts);
            }
            showInfo("Export complete", "Saved to: " + file.getAbsolutePath());
        } catch (Exception e) {
            showError("Export failed", e.getMessage());
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML private void onBack()    { AppNavigator.showPartnerApplications(); }
    @FXML private void onRefresh() { onOfferSelected(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyStageChipStyle(Label chip, String stage) {
        if (stage == null) return;
        String style = switch (stage.toUpperCase()) {
            case "HIRED", "ACCEPTED"     -> "job-offer-status-active";
            case "REJECTED", "WITHDRAWN" -> "job-offer-status-rejected";
            case "SCREENING"             -> "job-offer-status-info";
            case "SHORTLISTED"           -> "job-offer-status-pending";
            case "INTERVIEW"             -> "job-offer-status-pending";
            case "OFFER_SENT"            -> "job-offer-status-active";
            default                      -> "job-offer-status-closed";
        };
        chip.getStyleClass().add(style);
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(title);
        a.setContentText(msg); a.showAndWait();
    }
    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION); a.setTitle(title);
        a.setContentText(msg); a.showAndWait();
    }
}
