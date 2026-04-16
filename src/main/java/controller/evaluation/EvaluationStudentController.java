package controller.evaluation;

import entities.Assessment;
import entities.DocumentRequest;
import entities.Grade;
import entities.Reclamation;
import entities.Schedule;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import security.UserSession;
import service.evaluation.EvaluationService;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;

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
        studentIdField.setText(String.valueOf(UserSession.getCurrentUserId().orElse(1)));
        studentIdField.setEditable(false);
        studentIdField.setDisable(true);
        classIdField.setText("1");

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
            case "RECOMMENDATIONS" -> showSection(recommendationsPane);
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
            int studentId = intValue(studentIdField.getText());
            String courseName = complaintCourseNameField.getText();
            service.createReclamation(studentId, courseName, complaintSubjectField.getText(), complaintDescriptionArea.getText());
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
            int studentId = intValue(studentIdField.getText());
            service.createDocumentRequest(studentId, docTypeBox.getValue(), docInfoArea.getText());
            docInfoArea.clear();
            refreshDocRequests(studentId);
            showFeedback("Document request created.");
        } catch (Exception e) {
            showFeedback("Create document request failed: " + e.getMessage());
        }
    }

    private void refreshAll() {
        try {
            int studentId = intValue(studentIdField.getText());
            int classId = intValue(classIdField.getText());

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
            docRequestsCardsBox.getChildren().add(dataCard(
                    "Request #" + row.getId(),
                    "Type: " + safe(row.getDocumentType()),
                    "Status: " + safe(row.getStatus()),
                    "Path: " + safe(row.getDocumentPath())
            ));
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
    }

    private void setVisibleManaged(VBox pane, boolean visible) {
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    private int intValue(String value) {
        return Integer.parseInt(value.trim());
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
}
