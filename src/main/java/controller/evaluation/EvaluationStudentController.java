package controller.evaluation;

import entities.Assessment;
import entities.DocumentRequest;
import entities.Grade;
import entities.Reclamation;
import entities.Schedule;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import security.UserSession;
import service.evaluation.EvaluationService;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.function.UnaryOperator;

import javafx.scene.control.TextFormatter;

public class EvaluationStudentController {

    @FXML
    private TextField studentIdField;
    @FXML
    private TextField classIdField;

    @FXML
    private Label totalGradesLabel;
    @FXML
    private Label passedLabel;
    @FXML
    private Label failedLabel;
    @FXML
    private Label averageLabel;

    @FXML
    private VBox gradesPane;
    @FXML
    private VBox recommendationsPane;
    @FXML
    private VBox schedulePane;
    @FXML
    private VBox complaintsPane;
    @FXML
    private VBox documentsPane;

    @FXML
    private VBox gradesCardsBox;
    @FXML
    private VBox recommendationsCardsBox;
    @FXML
    private VBox scheduleCardsBox;

    @FXML
    private TextField complaintSubjectField;
    @FXML
    private TextField complaintCourseNameField;
    @FXML
    private TextArea complaintDescriptionArea;
    @FXML
    private VBox complaintsCardsBox;

    @FXML
    private ComboBox<String> docTypeBox;
    @FXML
    private TextArea docInfoArea;
    @FXML
    private VBox docRequestsCardsBox;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();
    private final DecimalFormat df = new DecimalFormat("0.00");

    @FXML
    public void initialize() {
        int currentStudentId = resolveStudentId();
        if (studentIdField != null) {
            studentIdField.setText(String.valueOf(currentStudentId));
            studentIdField.setEditable(false);
            studentIdField.setDisable(true);
        }
        if (classIdField != null) {
            classIdField.setText("1");
        }
        installInputValidation();

        docTypeBox.getItems().setAll(
                "attestation_stage",
                "attestation_inscription",
                "releve_notes",
                "attestation_reussite",
                "certificat_scolarite",
                "autre"
        );
        docTypeBox.getSelectionModel().selectFirst();

        showSection(gradesPane);
        refreshAll();

        // AI Correction for Student Message Body (spelling + profanity filter)
        complaintDescriptionArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String original = complaintDescriptionArea.getText();
                if (original != null && original.length() > 5) {
                    new Thread(() -> {
                        String corrected = aiService.correctSpellingAndGrammar(original);
                        if (corrected != null && !corrected.isEmpty() && !corrected.startsWith("###")) {
                            javafx.application.Platform.runLater(() -> {
                                if (complaintDescriptionArea.getText().equals(original)) {
                                    complaintDescriptionArea.setText(corrected);
                                    showFeedback("✅ AI auto-corrected your message and filtered inappropriate content.", false);
                                }
                            });
                        }
                    }).start();
                }
            }
        });

        // AI Correction for Student Message Subject (spelling + profanity filter)
        complaintSubjectField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String original = complaintSubjectField.getText();
                if (original != null && original.length() > 3) {
                    new Thread(() -> {
                        String corrected = aiService.correctSpellingAndGrammar(original);
                        if (corrected != null && !corrected.isEmpty() && !corrected.startsWith("###")) {
                            javafx.application.Platform.runLater(() -> {
                                if (complaintSubjectField.getText().equals(original)) {
                                    complaintSubjectField.setText(corrected);
                                    showFeedback("✅ AI auto-corrected subject and filtered inappropriate content.", false);
                                }
                            });
                        }
                    }).start();
                }
            }
        });
    }

    @FXML
    private void showGrades() {
        onOpenGrades();
    }

    @FXML
    private void showSchedule() {
        onOpenSchedule();
    }

    @FXML
    private void showComplaints() {
        onOpenComplaints();
    }

    @FXML
    private void showDocuments() {
        onOpenDocuments();
    }

    @FXML
    private void onOpenGrades() {
        showSection(gradesPane);
    }

    @FXML
    private void onOpenRecommendations() {
        showSection(recommendationsPane);
    }

    @FXML
    private void onOpenSchedule() {
        showSection(schedulePane);
    }

    @FXML
    private void onOpenComplaints() {
        showSection(complaintsPane);
    }

    @FXML
    private void onOpenDocuments() {
        showSection(documentsPane);
    }

    public void openSection(String sectionKey) {
        String normalized = sectionKey == null ? "GRADES" : sectionKey.trim().toUpperCase();
        switch (normalized) {
            case "RECOMMENDATIONS" -> showSection(recommendationsPane != null ? recommendationsPane : gradesPane);
            case "SCHEDULE" -> showSection(schedulePane);
            case "COMPLAINTS" -> showSection(complaintsPane);
            case "DOCUMENTS" -> showSection(documentsPane);
            default -> showSection(gradesPane);
        }
    }

    @FXML
    private void onRefreshAll() {
        refreshAll();
    }

    @FXML
    private void onCreateComplaint() {
        try {
            int studentId = resolveStudentId();
            String courseName = requireNotBlank(complaintCourseNameField.getText(), "Course name");
            String subject = requireNotBlank(complaintSubjectField.getText(), "Complaint subject");
            String description = requireNotBlank(complaintDescriptionArea.getText(), "Complaint description");

            if (subject.length() < 3) {
                throw new IllegalArgumentException("Complaint subject must have at least 3 characters.");
            }
            if (description.length() < 10) {
                throw new IllegalArgumentException("Complaint description must have at least 10 characters.");
            }
            if (service.findCourseIdByName(courseName) == null) {
                throw new IllegalArgumentException("Course name is invalid. Please enter an existing course title.");
            }

            service.createReclamation(studentId, courseName, subject, description);
            complaintSubjectField.clear();
            complaintDescriptionArea.clear();
            complaintCourseNameField.clear();
            refreshComplaints(studentId);
            showFeedback("Complaint created.");
        } catch (Exception e) {
            showFeedback("Create complaint failed: " + e.getMessage());
        }
    }

    @FXML
    private void onCreateDocumentRequest() {
        try {
            int studentId = resolveStudentId();
            String docType = docTypeBox.getValue();
            if (docType == null || docType.isBlank()) {
                throw new IllegalArgumentException("Please select a document type.");
            }
            String additionalInfo = requireNotBlank(docInfoArea.getText(), "Document request details");
            if (additionalInfo.length() < 5) {
                throw new IllegalArgumentException("Document request details must have at least 5 characters.");
            }

            service.createDocumentRequest(studentId, docType, additionalInfo);
            docInfoArea.clear();
            refreshDocRequests(studentId);
            showFeedback("Document request created.");
        } catch (Exception e) {
            showFeedback("Create document request failed: " + e.getMessage());
        }
    }

    private void refreshAll() {
        try {
            int studentId = resolveStudentId();
            int classId = resolveClassId();

            List<Grade> grades = service.getGradesByStudent(studentId);
            renderGrades(grades);

            EvaluationService.StudentSummary summary = service.computeStudentSummary(studentId);
            totalGradesLabel.setText(String.valueOf(summary.getTotalGrades()));
            passedLabel.setText(String.valueOf(summary.getPassed()));
            failedLabel.setText(String.valueOf(summary.getFailed()));
            averageLabel.setText(df.format(summary.getAverage()));

            renderRecommendations(service.buildRecommendations(studentId));
            renderSchedule(service.getScheduleByClasse(classId));
            refreshComplaints(studentId);
            refreshDocRequests(studentId);
            showFeedback("Evaluation data refreshed.");
        } catch (Exception e) {
            showFeedback("Refresh failed: " + e.getMessage());
        }
    }

    private void refreshComplaints(int studentId) {
        List<Reclamation> rows = service.getReclamationsByStudent(studentId);
        complaintsCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            complaintsCardsBox.getChildren().add(emptyCard("No complaints yet"));
            return;
        }
        for (Reclamation row : rows) {
            String courseTitle = row.getCourse() == null ? null : service.resolveCourseTitle(row.getCourse().getId());
            complaintsCardsBox.getChildren().add(dataCard(
                    "Complaint #" + row.getId(),
                "Course: " + safe(courseTitle),
                    "Subject: " + safe(row.getSubject()),
                    "Status: " + safe(row.getStatus()),
                    "Response: " + safe(row.getAdminResponse())
            ));
        }
    }

    private void refreshDocRequests(int studentId) {
        List<DocumentRequest> rows = service.getDocumentRequestsByStudent(studentId);
        docRequestsCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            docRequestsCardsBox.getChildren().add(emptyCard("No document requests yet"));
            return;
        }
        for (DocumentRequest row : rows) {
            String documentPath = row.getDocumentPath();
            VBox card = dataCard(
                    "Request #" + row.getId(),
                    "Type: " + safe(row.getDocumentType()),
                    "Status: " + safe(row.getStatus()),
                    "Path: " + safe(documentPath)
            );
            if (documentPath != null && !documentPath.isBlank()) {
                Button downloadButton = new Button("Download / Open");
                downloadButton.getStyleClass().add("eval-ghost-btn");
                downloadButton.setOnAction(event -> openDocument(documentPath));
                HBox actions = new HBox(downloadButton);
                actions.setAlignment(Pos.CENTER_LEFT);
                card.getChildren().add(actions);
            }
            docRequestsCardsBox.getChildren().add(card);
        }
    }

    private void renderGrades(List<Grade> grades) {
        gradesCardsBox.getChildren().clear();
        if (grades.isEmpty()) {
            gradesCardsBox.getChildren().add(emptyCard("No grades available"));
            return;
        }
        for (Grade grade : grades) {
            String status = grade.getScore() >= 10.0 ? "Passed" : "Failed";
            gradesCardsBox.getChildren().add(dataCard(
                    resolveAssessmentCourseTitle(grade.getAssessment()),
                    "Type: " + safe(grade.getAssessment() == null ? null : grade.getAssessment().getType()),
                    "Assessment: " + safe(grade.getAssessment() == null ? null : grade.getAssessment().getTitle()),
                    "Score: " + df.format(grade.getScore()) + " | " + status
            ));
        }
    }

    private void renderRecommendations(List<EvaluationService.RecommendationRow> rows) {
        if (recommendationsCardsBox == null) {
            return;
        }
        recommendationsCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            recommendationsCardsBox.getChildren().add(emptyCard("No recommendations available"));
            return;
        }
        for (EvaluationService.RecommendationRow row : rows) {
            recommendationsCardsBox.getChildren().add(dataCard(
                    safe(row.getCourseName()),
                    "Priority: " + safe(row.getPriority()),
                    "Action: " + safe(row.getAction())
            ));
        }
    }

    private void renderRecommendationResources(List<EvaluationService.LearningResourceRow> rows) {
        if (recommendationResourcesBox == null) {
            return;
        }
        recommendationResourcesBox.getChildren().clear();
        if (rows.isEmpty()) {
            recommendationResourcesBox.getChildren().add(emptyCard("No learning resources available"));
            return;
        }

        for (EvaluationService.LearningResourceRow row : rows) {
            recommendationResourcesBox.getChildren().add(resourceCard(row));
        }
    }

    private VBox resourceCard(EvaluationService.LearningResourceRow row) {
        VBox card = new VBox(8);
        card.getStyleClass().add("eval-resource-card");
        card.setPrefWidth(280);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(safe(row.getCourseName()));
        title.getStyleClass().add("eval-resource-title");

        Label badge = new Label(safe(row.getPriority()));
        badge.getStyleClass().add("eval-resource-badge");

        header.getChildren().addAll(title, badge);

        Label guidance = new Label(safe(row.getGuidance()));
        guidance.getStyleClass().add("eval-resource-text");
        guidance.setWrapText(true);
        guidance.setMinHeight(40);

        HBox links = new HBox(8);
        links.setAlignment(Pos.CENTER_LEFT);
        links.getStyleClass().add("eval-resource-links");
        links.getChildren().addAll(
                createResourceButton("YouTube", row.getYoutubeUrl(), "eval-link-youtube"),
                createResourceButton("Udemy", row.getUdemyUrl(), "eval-link-udemy"),
                createResourceButton("Coursera", row.getCourseraUrl(), "eval-link-coursera")
        );

        card.getChildren().addAll(header, guidance, links);
        return card;
    }

    private Button createResourceButton(String text, String url, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("eval-link-btn", styleClass);
        if ("eval-link-youtube".equals(styleClass)) {
            button.setOnAction(event -> {
                // Extract topic from the search query parameter
                String topic = "Learning Resource";
                try {
                    if (url != null && url.contains("search_query=")) {
                        String raw = url.split("search_query=")[1];
                        topic = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {}
                openYoutubePlayer(url, topic);
            });
        } else {
            button.setOnAction(event -> openExternalLink(url));
        }
        return button;
    }

    /**
     * Opens an embedded YouTube player in a dark-themed modal popup window.
     * The WebView loads YouTube search results so the student can pick and
     * play any video without leaving the application.
     */
    private void openYoutubePlayer(String searchUrl, String topic) {
        Stage playerStage = new Stage();
        playerStage.initModality(Modality.APPLICATION_MODAL);
        playerStage.setTitle("\u25B6 YouTube — " + topic);
        playerStage.setMinWidth(980);
        playerStage.setMinHeight(680);

        // ── WebView ──────────────────────────────────────────────────────────
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.load(searchUrl);
        VBox.setVgrow(webView, Priority.ALWAYS);

        // ── URL Bar ──────────────────────────────────────────────────────────
        TextField urlBar = new TextField(searchUrl);
        urlBar.setEditable(false);
        urlBar.setStyle(
            "-fx-background-color: #1e1e1e; -fx-text-fill: #aaaaaa;" +
            "-fx-border-color: #333; -fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-font-size: 11px; -fx-padding: 4 8;");
        HBox.setHgrow(urlBar, Priority.ALWAYS);

        // Update URL bar when page navigates
        engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (newUrl != null) urlBar.setText(newUrl);
        });

        // ── Navigation Buttons ───────────────────────────────────────────────
        Button backBtn = new Button("\u2190 Back");
        backBtn.setStyle(
            "-fx-background-color: #272727; -fx-text-fill: #cccccc;" +
            "-fx-border-color: #444; -fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-font-size: 12px; -fx-padding: 5 12; -fx-cursor: hand;");
        backBtn.setOnAction(e -> {
            try { engine.getHistory().go(-1); } catch (Exception ignored) {}
        });

        Button forwardBtn = new Button("\u2192 Fwd");
        forwardBtn.setStyle(backBtn.getStyle());
        forwardBtn.setOnAction(e -> {
            try { engine.getHistory().go(1); } catch (Exception ignored) {}
        });

        Button reloadBtn = new Button("\u21BB");
        reloadBtn.setStyle(backBtn.getStyle());
        reloadBtn.setOnAction(e -> engine.reload());

        Button homeBtn = new Button("\uD83C\uDFE0 Home");
        homeBtn.setStyle(backBtn.getStyle());
        homeBtn.setOnAction(e -> engine.load(searchUrl));

        // ── Close Button ─────────────────────────────────────────────────────
        Button closeBtn = new Button("\u2715 Close");
        closeBtn.setStyle(
            "-fx-background-color: #cc0000; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-font-size: 12px; -fx-padding: 5 14; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> playerStage.close());

        // ── Header Bar ───────────────────────────────────────────────────────
        Label ytIcon = new Label("\u25B6");
        ytIcon.setStyle("-fx-text-fill: #ff0000; -fx-font-size: 20px;");
        Label titleLabel = new Label(topic);
        titleLabel.setStyle(
            "-fx-text-fill: #ffffff; -fx-font-size: 14px;" +
            "-fx-font-weight: bold; -fx-max-width: 220; -fx-ellipsis-string: '...';");

        HBox navBar = new HBox(8,
            ytIcon, titleLabel, backBtn, forwardBtn, reloadBtn, homeBtn,
            urlBar, closeBtn);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setStyle(
            "-fx-background-color: #0f0f0f;" +
            "-fx-padding: 10 14;" +
            "-fx-border-color: transparent transparent #333 transparent;" +
            "-fx-border-width: 1;");

        // ── Status Bar ───────────────────────────────────────────────────────
        Label statusLabel = new Label("Loading...");
        statusLabel.setStyle(
            "-fx-text-fill: #777; -fx-font-size: 10px; -fx-padding: 3 10;");
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            switch (state) {
                case RUNNING  -> statusLabel.setText("Loading " + engine.getLocation() + "...");
                case SUCCEEDED -> statusLabel.setText("Ready");
                case FAILED   -> statusLabel.setText("Failed to load page.");
                default       -> statusLabel.setText("");
            }
        });
        HBox statusBar = new HBox(statusLabel);
        statusBar.setStyle("-fx-background-color: #111; -fx-padding: 2 8;");

        // ── Layout ───────────────────────────────────────────────────────────
        VBox root = new VBox(navBar, webView, statusBar);
        root.setStyle("-fx-background-color: #0f0f0f;");

        Scene scene = new Scene(root, 980, 680);
        playerStage.setScene(scene);
        playerStage.show();
    }

    private void renderSchedule(List<Schedule> rows) {
        scheduleCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            scheduleCardsBox.getChildren().add(emptyCard("No schedule entries available"));
            return;
        }

        Map<String, List<Schedule>> byDay = new LinkedHashMap<>();
        String[] daysOrder = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (String d : daysOrder) byDay.put(d, new ArrayList<>());

        for (Schedule s : rows) {
            String day = s.getDayOfWeek() == null ? "Monday" : s.getDayOfWeek().trim();
            String normalized = day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
            if (byDay.containsKey(normalized)) {
                byDay.get(normalized).add(s);
            }
        }

        for (Map.Entry<String, List<Schedule>> entry : byDay.entrySet()) {
            String dayName = entry.getKey();
            List<Schedule> dayClasses = entry.getValue();

            VBox dayCard = new VBox(10);
            dayCard.getStyleClass().add("eval-data-card");
            dayCard.setMinWidth(280);
            dayCard.setPadding(new Insets(14));

            Label dayLabel = new Label(dayName.toUpperCase());
            dayLabel.setStyle("-fx-font-weight: 900; -fx-text-fill: #7ec4ff; -fx-font-size: 15px; -fx-border-color: transparent transparent rgba(56,139,255,0.5) transparent; -fx-border-width: 0 0 2 0; -fx-padding: 0 0 4 0;");
            dayCard.getChildren().add(dayLabel);

            if (dayClasses.isEmpty()) {
                Label empty = new Label("No sessions scheduled");
                empty.setStyle("-fx-text-fill: rgba(150,190,255,0.4); -fx-font-style: italic; -fx-font-size: 12px;");
                dayCard.getChildren().add(empty);
            } else {
                dayClasses.sort(Comparator.comparing(Schedule::getStartTime));
                for (Schedule s : dayClasses) {
                    VBox classItem = new VBox(4);
                    classItem.setPadding(new Insets(10));
                    classItem.setStyle("-fx-background-color: rgba(30,60,120,0.35); -fx-background-radius: 10; -fx-border-color: rgba(56,139,255,0.2); -fx-border-radius: 10; -fx-border-width: 1;");

                    String courseTitle = s.getCourse() == null ? "Unknown Course" : service.resolveCourseTitle(s.getCourse().getId());
                    Label title = new Label(courseTitle);
                    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #d0e8ff; -fx-font-size: 13px;");
                    title.setWrapText(true);

                    HBox timeRow = new HBox(6);
                    timeRow.setAlignment(Pos.CENTER_LEFT);
                    Label clock = new Label("🕒");
                    clock.setStyle("-fx-font-size: 12px;");
                    Label time = new Label(s.getStartTime() + " - " + s.getEndTime());
                    time.setStyle("-fx-text-fill: #90c8ff; -fx-font-size: 12px; -fx-font-weight: 600;");
                    timeRow.getChildren().addAll(clock, time);

                    HBox roomRow = new HBox(6);
                    roomRow.setAlignment(Pos.CENTER_LEFT);
                    Label map = new Label("📍");
                    map.setStyle("-fx-font-size: 12px;");
                    Label room = new Label(safe(s.getRoom()));
                    room.setStyle("-fx-text-fill: rgba(160,200,255,0.65); -fx-font-size: 12px;");
                    roomRow.getChildren().addAll(map, room);

                    classItem.getChildren().addAll(title, timeRow, roomRow);
                    dayCard.getChildren().add(classItem);
                }
            }
            scheduleCardsBox.getChildren().add(dayCard);
        }
    }

    private VBox dataCard(String title, String... lines) {
        VBox card = new VBox();
        card.getStyleClass().add("eval-data-card");

        Label titleLabel = new Label(safe(title));
        titleLabel.getStyleClass().add("eval-data-card-title");
        card.getChildren().add(titleLabel);

        for (String line : lines) {
            Label lineLabel = new Label(safe(line));
            lineLabel.getStyleClass().add("eval-data-card-line");
            lineLabel.setWrapText(true);
            card.getChildren().add(lineLabel);
        }
        return card;
    }

    private VBox emptyCard(String text) {
        VBox card = new VBox();
        card.getStyleClass().add("eval-empty-card");
        Label label = new Label(text);
        label.getStyleClass().add("eval-data-card-line");
        card.getChildren().add(label);
        return card;
    }

    private void showSection(VBox target) {
        setVisibleManaged(gradesPane, target == gradesPane);
        setVisibleManaged(recommendationsPane, target == recommendationsPane);
        setVisibleManaged(schedulePane, target == schedulePane);
        setVisibleManaged(complaintsPane, target == complaintsPane);
        setVisibleManaged(documentsPane, target == documentsPane);
    }

    private void setVisibleManaged(VBox pane, boolean visible) {
        if (pane == null) {
            return;
        }
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    private int intValue(String value) {
        return Integer.parseInt(value.trim());
    }

    private void installInputValidation() {
        if (classIdField != null) {
            setIntegerField(classIdField);
        }
        if (complaintSubjectField != null) {
            setLengthField(complaintSubjectField, 150);
        }
        if (complaintCourseNameField != null) {
            setLengthField(complaintCourseNameField, 150);
        }
        if (docInfoArea != null) {
            setLengthField(docInfoArea, 500);
        }
        if (complaintDescriptionArea != null) {
            setLengthField(complaintDescriptionArea, 1000);
        }
    }

    private int resolveStudentId() {
        if (studentIdField != null && studentIdField.getText() != null && !studentIdField.getText().isBlank()) {
            return requirePositiveInt(studentIdField.getText(), "Student ID");
        }
        return UserSession.getCurrentUserId()
                .filter(id -> id > 0)
                .orElseThrow(() -> new IllegalStateException("No authenticated student found in session."));
    }

    private int resolveClassId() {
        if (classIdField != null && classIdField.getText() != null && !classIdField.getText().isBlank()) {
            return requirePositiveInt(classIdField.getText(), "Class ID");
        }
        return 1;
    }

    private void setIntegerField(TextField field) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.matches("\\d*") ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private void setLengthField(TextInputControl input, int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.length() <= maxLength ? change : null;
        };
        input.setTextFormatter(new TextFormatter<>(filter));
    }

    private int requirePositiveInt(String rawValue, String fieldName) {
        String normalized = requireNotBlank(rawValue, fieldName);
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than 0.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid integer.");
        }
    }

    private String requireNotBlank(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return rawValue.trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String resolveAssessmentCourseTitle(Assessment assessment) {
        if (assessment == null) {
            return "-";
        }
        if (assessment.getCourse() != null) {
            String byId = service.resolveCourseTitle(assessment.getCourse().getId());
            if (byId != null && !byId.isBlank()) {
                return byId;
            }
            if (assessment.getCourse().getTitle() != null && !assessment.getCourse().getTitle().isBlank()) {
                return assessment.getCourse().getTitle();
            }
        }
        return safe(assessment.getTitle());
    }

    private void showFeedback(String text) {
        feedbackLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + " - " + text);
    }

    private void openDocument(String documentPath) {
        try {
            if (documentPath == null || documentPath.isBlank()) {
                throw new IllegalArgumentException("No document uploaded yet.");
            }
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop actions are not supported on this system.");
            }

            String normalized = documentPath.trim();
            Desktop desktop = Desktop.getDesktop();

            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                desktop.browse(URI.create(normalized));
                showFeedback("Opened document URL.");
                return;
            }

            File file = new File(normalized);
            if (!file.exists()) {
                throw new IllegalArgumentException("Document file not found: " + normalized);
            }
            desktop.open(file);
            showFeedback("Opened document file.");
        } catch (Exception exception) {
            showFeedback("Unable to open document: " + exception.getMessage());
        }
    }
}