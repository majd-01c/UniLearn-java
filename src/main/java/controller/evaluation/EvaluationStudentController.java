package controller.evaluation;

import entities.Assessment;
import entities.DocumentRequest;
import entities.Grade;
import entities.Reclamation;
import entities.Schedule;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import security.UserSession;
import service.evaluation.EvaluationService;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
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
        if (aiTeacherMessageArea != null) {
            aiTeacherMessageArea.setEditable(false);
            aiTeacherMessageArea.setText("Click Generate Teacher Message to draft a clear support request.");
        }
        if (pdfTranslationArea != null) {
            pdfTranslationArea.setEditable(false);
            pdfTranslationArea.setText("Select a delivered PDF request and click Translate PDF.");
        }

        showSection(gradesPane);
        refreshAll();
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

    @FXML
    private void onGenerateAiRecommendations() {
        try {
            int studentId = resolveStudentId();
            String recommendation = service.generateAiStudentRecommendations(studentId);
            if (aiRecommendationArea != null) {
                aiRecommendationArea.setText(recommendation);
            }
            renderRecommendationResources(service.buildLearningResources(studentId));
            showFeedback("AI recommendations generated.");
        } catch (Exception e) {
            showFeedback("AI recommendation failed: " + e.getMessage());
        }
    }

    @FXML
    private void onGenerateTeacherMessage() {
        try {
            int studentId = resolveStudentId();
            String message = service.generateAiTeacherMessageFromStudent(studentId);
            if (aiTeacherMessageArea != null) {
                aiTeacherMessageArea.setText(message);
            }
            showFeedback("Teacher message generated.");
        } catch (Exception e) {
            showFeedback("Teacher message generation failed: " + e.getMessage());
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
                showFeedback("Translated PDF saved: " + file.getName());
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
            } else {
                showFeedback("Translation complete, but save was cancelled.");
            }
        } catch (Exception e) {
            showFeedback("PDF translation/save failed: " + e.getMessage());
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
                    showFeedback("PDF selected for AI translation.");
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
        button.setOnAction(event -> openExternalLink(url));
        return button;
    }

    private void renderSchedule(List<Schedule> rows) {
        scheduleCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            scheduleCardsBox.getChildren().add(emptyCard("No schedule entries available"));
            return;
        }
        for (Schedule row : rows) {
            String courseTitle = row.getCourse() == null ? null : service.resolveCourseTitle(row.getCourse().getId());
            scheduleCardsBox.getChildren().add(dataCard(
                    safe(row.getDayOfWeek()),
                    "Time: " + row.getStartTime() + " - " + row.getEndTime(),
                "Course: " + safe(courseTitle),
                    "Room: " + safe(row.getRoom())
            ));
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
        if (aiTeacherMessageArea != null) {
            setLengthField(aiTeacherMessageArea, 8000);
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

    private void openExternalLink(String url) {
        try {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("No resource link available.");
            }
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop actions are not supported on this system.");
            }
            Desktop.getDesktop().browse(URI.create(url.trim()));
            showFeedback("Opened learning resource.");
        } catch (Exception exception) {
            showFeedback("Unable to open learning resource: " + exception.getMessage());
        }
    }
}
