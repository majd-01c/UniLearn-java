package controller.evaluation;

import entities.Assessment;
import entities.DocumentRequest;
import entities.Grade;
import entities.Reclamation;
import entities.Schedule;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import security.UserSession;
import service.evaluation.EvaluationService;
import service.evaluation.ai.GroqAiService;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator; // Added import for Comparator
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private Button gradesNavBtn;
    @FXML
    private Button recommendationsNavBtn;
    @FXML
    private Button scheduleNavBtn;
    @FXML
    private Button complaintsNavBtn;
    @FXML
    private Button documentsNavBtn;

    @FXML
    private VBox gradesCardsBox;
    @FXML
    private VBox recommendationsCardsBox;
    @FXML
    private HBox recommendationResourcesBox;
    @FXML
    private VBox scheduleCardsBox;

    @FXML
    private TextField complaintSubjectField;
    @FXML
    private ComboBox<String> complaintCourseBox;
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
    private TextArea aiRecommendationArea;
    @FXML
    private TextArea aiTeacherMessageArea;
    @FXML
    private Label selectedPdfLabel;
    @FXML
    private ComboBox<String> pdfTargetLanguageBox;
    @FXML
    private TextArea pdfTranslationArea;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();
    private final GroqAiService aiService = new GroqAiService();
    private final DecimalFormat df = new DecimalFormat("0.00");
    private String selectedPdfPath;

    @FXML
    public void initialize() {
        int currentStudentId = resolveStudentId();
        if (studentIdField != null) {
            studentIdField.setText(String.valueOf(currentStudentId));
            studentIdField.setEditable(false);
            studentIdField.setDisable(true);
        }
        Integer classId = service.resolvePrimaryClassIdForStudent(currentStudentId);
        if (classIdField != null) {
            classIdField.setText(classId == null ? "" : String.valueOf(classId));
        }
        installInputValidation();

        if (complaintCourseBox != null) {
            complaintCourseBox.getItems().setAll(service.getCourseNamesForStudent(currentStudentId));
            if (!complaintCourseBox.getItems().isEmpty()) {
                complaintCourseBox.getSelectionModel().selectFirst();
            }
        }

        docTypeBox.getItems().setAll(
                "attestation_stage",
                "attestation_inscription",
                "releve_notes",
                "attestation_reussite",
                "certificat_scolarite",
                "autre"
        );
        docTypeBox.getSelectionModel().selectFirst();

        if (pdfTargetLanguageBox != null) {
            pdfTargetLanguageBox.getItems().setAll("French", "English", "Arabic", "Spanish", "German");
            pdfTargetLanguageBox.getSelectionModel().select("French");
        }
        if (selectedPdfLabel != null) {
            selectedPdfLabel.setText("Selected PDF: none");
        }
        if (aiRecommendationArea != null) {
            aiRecommendationArea.setEditable(false);
            aiRecommendationArea.setText("Click Generate AI Recommendations to create a personalized study plan.");
        }
        if (pdfTranslationArea != null) {
            pdfTranslationArea.setEditable(false);
            pdfTranslationArea.setText("Select a delivered PDF request and click Translate PDF.");
        }

        showSection(gradesPane);
        refreshAll();

        // AI Correction for Student Message to Teacher (including profanity filter)
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
                                    showFeedback("AI auto-corrected your message spelling and filtered content.", false);
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
    private void showRecommendations() {
        onOpenRecommendations();
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
            String courseName = complaintCourseBox == null ? null : complaintCourseBox.getValue();
            courseName = requireNotBlank(courseName, "Course");
            String subject = requireNotBlank(complaintSubjectField.getText(), "Subject");
            String description = requireNotBlank(complaintDescriptionArea.getText(), "Message content");

            if (subject.length() < 3) {
                throw new IllegalArgumentException("Subject must have at least 3 characters.");
            }
            if (description.length() < 5) {
                throw new IllegalArgumentException("Message must have at least 5 characters.");
            }

            service.createReclamation(studentId, courseName, subject, description);
            complaintSubjectField.clear();
            complaintDescriptionArea.clear();
            refreshComplaints(studentId);
            showFeedback("Message sent to teacher.", false);
        } catch (Exception e) {
            showFeedback("Failed to send message: " + e.getMessage(), true);
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
            showFeedback("Document request created.", false);
        } catch (Exception e) {
            showFeedback("Create document request failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onGenerateAiRecommendations() {
        try {
            int studentId = resolveStudentId();
            String recommendation = service.generateAiStudentRecommendations(studentId);
            if (aiRecommendationArea != null) {
                aiRecommendationArea.setText(recommendation);
            }
            renderRecommendationResources(service.buildLearningResources(studentId));
            showFeedback("AI recommendations generated.", false);
        } catch (Exception e) {
            showFeedback("AI recommendation failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onTranslateSelectedPdf() {
        try {
            if (selectedPdfPath == null || selectedPdfPath.isBlank()) {
                throw new IllegalArgumentException("Please select a delivered PDF from the list first.");
            }
            String targetLanguage = (pdfTargetLanguageBox == null || pdfTargetLanguageBox.getValue() == null)
                    ? "English"
                    : pdfTargetLanguageBox.getValue();
            
            String translatedText = service.translatePdfDocument(selectedPdfPath, targetLanguage);
            if (pdfTranslationArea != null) {
                pdfTranslationArea.setText(translatedText);
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Translated PDF");
            fileChooser.setInitialFileName("translated_document_" + targetLanguage + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fileChooser.showSaveDialog(pdfTranslationArea.getScene().getWindow());

            if (file != null) {
                service.saveTextAsPdf(translatedText, file);
                showFeedback("Translated PDF saved: " + file.getName(), false);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
            } else {
                showFeedback("Translation complete, but save was cancelled.", false);
            }
        } catch (Exception e) {
            showFeedback("PDF translation/save failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onDownloadTranscript() {
        try {
            int studentId = resolveStudentId();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Academic Transcript");
            fileChooser.setInitialFileName("Transcript_" + studentId + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fileChooser.showSaveDialog(feedbackLabel.getScene().getWindow());

            if (file != null) {
                service.downloadStudentTranscript(studentId, file);
                showFeedback("Transcript downloaded: " + file.getName(), false);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
            }
        } catch (Exception e) {
            showFeedback("Download transcript failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onDownloadStudentSchedule() {
        try {
            int studentId = resolveStudentId();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save My Academic Schedule");
            fileChooser.setInitialFileName("Student_Schedule_" + studentId + ".pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fileChooser.showSaveDialog(feedbackLabel.getScene().getWindow());

            if (file != null) {
                service.downloadStudentSchedule(studentId, file);
                showFeedback("Schedule downloaded: " + file.getName(), false);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
            }
        } catch (Exception e) {
            showFeedback("Download schedule failed: " + e.getMessage(), true);
        }
    }

    private void refreshAll() {
        try {
            int studentId = resolveStudentId();
            int classId = resolveClassId();

            if (complaintCourseBox != null) {
                String previousSelection = complaintCourseBox.getValue();
                complaintCourseBox.getItems().setAll(service.getCourseNamesForStudent(studentId));
                if (previousSelection != null && complaintCourseBox.getItems().contains(previousSelection)) {
                    complaintCourseBox.getSelectionModel().select(previousSelection);
                } else if (!complaintCourseBox.getItems().isEmpty()) {
                    complaintCourseBox.getSelectionModel().selectFirst();
                }
            }

            List<Grade> grades = service.getGradesByStudent(studentId);
            renderGrades(grades);

            EvaluationService.StudentSummary summary = service.computeStudentSummary(studentId);
            totalGradesLabel.setText(String.valueOf(summary.getTotalGrades()));
            passedLabel.setText(String.valueOf(summary.getPassed()));
            failedLabel.setText(String.valueOf(summary.getFailed()));
            averageLabel.setText(df.format(summary.getAverage()));

            renderRecommendations(service.buildRecommendations(studentId));
            renderRecommendationResources(service.buildLearningResources(studentId));
            renderSchedule(service.getScheduleByClasse(classId));
            refreshComplaints(studentId);
            refreshDocRequests(studentId);
            showFeedback("Evaluation data refreshed.", false);
        } catch (Exception e) {
            showFeedback("Refresh failed: " + e.getMessage(), true);
        }
    }

    private void refreshComplaints(int studentId) {
        List<Reclamation> rows = service.getReclamationsByStudent(studentId);
        complaintsCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            complaintsCardsBox.getChildren().add(emptyCard("No messages yet"));
            return;
        }
        for (Reclamation row : rows) {
            String courseTitle = row.getCourse() == null ? null : service.resolveCourseTitle(row.getCourse().getId());
            complaintsCardsBox.getChildren().add(dataCard(
                    "Topic: " + safe(row.getSubject()),
                "Regarding Course: " + safe(courseTitle),
                    "Status: " + safe(row.getStatus()).toUpperCase(),
                    "Teacher Reply: " + safe(row.getAdminResponse())
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
                Button downloadButton = new Button("Download / Open Original");
                downloadButton.getStyleClass().add("eval-ghost-btn");
                downloadButton.setOnAction(event -> openDocument(documentPath));

                Button selectForTranslateButton = new Button("Use for AI Translation");
                selectForTranslateButton.getStyleClass().add("eval-primary-btn");
                selectForTranslateButton.setOnAction(event -> {
                    selectedPdfPath = documentPath;
                    if (selectedPdfLabel != null) {
                        selectedPdfLabel.setText("Selected PDF: " + documentPath);
                    }
                    showFeedback("PDF selected for AI translation.", false);
                });

                HBox actions = new HBox(8, downloadButton, selectForTranslateButton);
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

        Map<String, List<Grade>> grouped = new LinkedHashMap<>();
        for (Grade grade : grades) {
            if (grade.getAssessment() == null) continue;
            String courseName = resolveAssessmentCourseTitle(grade.getAssessment());
            String title = safe(grade.getAssessment().getTitle());
            String key = courseName + " - " + title;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(grade);
        }

        for (Map.Entry<String, List<Grade>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<Grade> courseGrades = entry.getValue();

            VBox card = new VBox(6);
            card.getStyleClass().add("eval-data-card");

            HBox header = new HBox();
            header.setAlignment(Pos.CENTER_LEFT);
            Label titleLabel = new Label(key);
            titleLabel.getStyleClass().add("eval-data-card-title");
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            double sum = 0;
            int count = 0;
            for (Grade g : courseGrades) {
                sum += g.getScore();
                count++;
            }
            double moyenne = count > 0 ? sum / count : 0;
            Label moyenneLabel = new Label("MOYENNE: " + df.format(moyenne));
            moyenneLabel.getStyleClass().add("eval-resource-badge");
            moyenneLabel.setStyle("-fx-background-color: #0ea5e9; -fx-font-size: 13px;");

            header.getChildren().addAll(titleLabel, spacer, moyenneLabel);
            card.getChildren().add(header);

            for (Grade grade : courseGrades) {
                String type = safe(grade.getAssessment().getType());
                String scoreStr = df.format(grade.getScore());
                String status = grade.getScore() >= 10.0 ? "PASSED" : "FAILED";
                
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                Label typeLabel = new Label(type.toUpperCase());
                typeLabel.setMinWidth(60);
                typeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");
                
                Label scoreLabel = new Label("Score: " + scoreStr);
                scoreLabel.setMinWidth(100);
                
                Label statusLabel = new Label(status);
                statusLabel.setStyle(status.equals("PASSED") ? "-fx-text-fill: #10b981;" : "-fx-text-fill: #ef4444;");
                statusLabel.setStyle(statusLabel.getStyle() + " -fx-font-weight: bold;");

                row.getChildren().addAll(typeLabel, scoreLabel, statusLabel);
                card.getChildren().add(row);
            }
            
            gradesCardsBox.getChildren().add(card);
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
        button.setOnAction(event -> openExternalLink(url));
        return button;
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
            dayCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d1d5db; -fx-background-radius: 12; -fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 2);");

            Label dayLabel = new Label(dayName.toUpperCase());
            dayLabel.setStyle("-fx-font-weight: 900; -fx-text-fill: #111827; -fx-font-size: 15px; -fx-border-color: transparent transparent #3b82f6 transparent; -fx-border-width: 0 0 2 0; -fx-padding: 0 0 4 0;");
            dayCard.getChildren().add(dayLabel);

            if (dayClasses.isEmpty()) {
                Label empty = new Label("No sessions scheduled");
                empty.setStyle("-fx-text-fill: #9ca3af; -fx-font-style: italic; -fx-font-size: 12px;");
                dayCard.getChildren().add(empty);
            } else {
                dayClasses.sort(Comparator.comparing(Schedule::getStartTime));
                for (Schedule s : dayClasses) {
                    VBox classItem = new VBox(4);
                    classItem.setPadding(new Insets(10));
                    classItem.setStyle("-fx-background-color: #f9fafb; -fx-background-radius: 8; -fx-border-color: #e5e7eb; -fx-border-radius: 8;");
                    
                    String courseTitle = s.getCourse() == null ? "Unknown Course" : service.resolveCourseTitle(s.getCourse().getId());
                    Label title = new Label(courseTitle);
                    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f2937; -fx-font-size: 13px;");
                    title.setWrapText(true);

                    HBox timeRow = new HBox(6);
                    timeRow.setAlignment(Pos.CENTER_LEFT);
                    Label clock = new Label("🕒");
                    clock.setStyle("-fx-font-size: 12px;");
                    Label time = new Label(s.getStartTime() + " - " + s.getEndTime());
                    time.setStyle("-fx-text-fill: #4b5563; -fx-font-size: 12px; -fx-font-weight: 600;");
                    timeRow.getChildren().addAll(clock, time);

                    HBox roomRow = new HBox(6);
                    roomRow.setAlignment(Pos.CENTER_LEFT);
                    Label map = new Label("📍");
                    map.setStyle("-fx-font-size: 12px;");
                    Label room = new Label(safe(s.getRoom()));
                    room.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");
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
        updateNavSelection(target);
    }

    private void updateNavSelection(VBox target) {
        setNavActive(gradesNavBtn, target == gradesPane);
        setNavActive(recommendationsNavBtn, target == recommendationsPane);
        setNavActive(scheduleNavBtn, target == schedulePane);
        setNavActive(complaintsNavBtn, target == complaintsPane);
        setNavActive(documentsNavBtn, target == documentsPane);
    }

    private void setNavActive(Button button, boolean active) {
        if (button == null) {
            return;
        }
        if (active) {
            if (!button.getStyleClass().contains("eval-nav-btn-active")) {
                button.getStyleClass().add("eval-nav-btn-active");
            }
        } else {
            button.getStyleClass().remove("eval-nav-btn-active");
        }
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
        if (docInfoArea != null) {
            setLengthField(docInfoArea, 500);
        }
        if (complaintDescriptionArea != null) {
            setLengthField(complaintDescriptionArea, 1000);
        }
        if (aiRecommendationArea != null) {
            setLengthField(aiRecommendationArea, 12000);
        }
        if (pdfTranslationArea != null) {
            setLengthField(pdfTranslationArea, 20000);
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
        int studentId = resolveStudentId();
        Integer classId = service.resolvePrimaryClassIdForStudent(studentId);
        if (classId == null) {
            throw new IllegalStateException("No active class found for this student.");
        }
        return classId;
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

    private void showFeedback(String text, boolean isError) {
        feedbackLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + " - " + text);
        feedbackLabel.getStyleClass().removeAll("eval-feedback", "eval-feedback-error");
        if (isError) {
            feedbackLabel.getStyleClass().add("eval-feedback-error");
        } else {
            feedbackLabel.getStyleClass().add("eval-feedback");
        }
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
                showFeedback("Opened document URL.", false);
                return;
            }

            File file = new File(normalized);
            if (!file.exists()) {
                throw new IllegalArgumentException("Document file not found: " + normalized);
            }
            desktop.open(file);
            showFeedback("Opened document file.", false);
        } catch (Exception exception) {
            showFeedback("Unable to open document: " + exception.getMessage(), true);
        }
    }

    private void openExternalLink(String url) {
        try {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("No resource link available.");
            }
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop actions are not supported on this system.");
            }
            Desktop.getDesktop().browse(URI.create(url.trim()));
            showFeedback("Opened learning resource.", false);
        } catch (Exception exception) {
            showFeedback("Unable to open learning resource: " + exception.getMessage(), true);
        }
    }
}
