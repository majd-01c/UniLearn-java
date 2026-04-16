package controller.evaluation;

import entities.Assessment;
import entities.Grade;
import entities.Schedule;
import evaluation.AssessmentType;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import security.UserSession;
import service.evaluation.EvaluationService;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

public class EvaluationTeacherController {

    @FXML
    private Label teacherNameLabel;
    @FXML
    private TextField classNameField;

    @FXML
    private TextField assessmentTitleField;
    @FXML
    private ComboBox<String> assessmentTypeBox;
    @FXML
    private TextField assessmentMaxScoreField;
    @FXML
    private DatePicker assessmentDatePicker;
    @FXML
    private TextArea assessmentDescriptionArea;
    @FXML
    private TextField assessmentCourseNameField;
    @FXML
    private TextField assessmentClasseNameField;
    @FXML
    private TextField assessmentContenuTitleField;

    @FXML
    private ComboBox<String> assessmentsSortBox;
    @FXML
    private VBox assessmentsCardsBox;

    @FXML
    private Label selectedAssessmentLabel;
    @FXML
    private ComboBox<String> gradesSortBox;
    @FXML
    private VBox gradesCardsBox;

    @FXML
    private TextField gradeStudentNameField;
    @FXML
    private TextField gradeScoreInputField;
    @FXML
    private TextField gradeCommentInputField;

    @FXML
    private ComboBox<String> scheduleSortBox;
    @FXML
    private VBox scheduleCardsBox;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();
    private final DecimalFormat df = new DecimalFormat("0.00");
    private int teacherId;
    private Integer selectedAssessmentId;

    @FXML
    public void initialize() {
        teacherId = UserSession.getCurrentUserId().orElse(1);
        String teacherName = service.resolveUserDisplayName(teacherId);
        teacherNameLabel.setText(teacherName == null ? "Teacher" : teacherName);

        assessmentTypeBox.getItems().setAll("CC", "EXAM", "OTHER");
        assessmentTypeBox.getSelectionModel().select("CC");
        assessmentMaxScoreField.setText("20");

        assessmentsSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        assessmentsSortBox.getSelectionModel().select("NEW");
        gradesSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        gradesSortBox.getSelectionModel().select("NEW");
        scheduleSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        scheduleSortBox.getSelectionModel().select("NEW");

        selectedAssessmentLabel.setText("Selected assessment: none");

        installInputValidation();
        refreshTeacherSpace();
    }

    @FXML
    private void onRefreshTeacherSpace() {
        refreshTeacherSpace();
    }

    @FXML
    private void onSortAssessmentsChanged() {
        refreshAssessments();
    }

    @FXML
    private void onSortGradesChanged() {
        if (selectedAssessmentId != null) {
            onLoadAssessmentGrades();
        }
    }

    @FXML
    private void onSortScheduleChanged() {
        refreshTeacherSchedule();
    }

    @FXML
    private void onCreateAssessment() {
        try {
            Integer courseId = service.findCourseIdByName(requireNotBlank(assessmentCourseNameField.getText(), "Course name"));
            if (courseId == null) {
                throw new IllegalArgumentException("Course name not found.");
            }

            Integer classeId = optionalNameToId(assessmentClasseNameField.getText(), "Class name", service::findClasseIdByName);
            Integer contenuId = optionalNameToId(assessmentContenuTitleField.getText(), "Content title", service::findContenuIdByTitle);

            String title = requireNotBlank(assessmentTitleField.getText(), "Assessment title");
            String description = requireNotBlank(assessmentDescriptionArea.getText(), "Assessment description");
            if (title.length() < 3) {
                throw new IllegalArgumentException("Assessment title must have at least 3 characters.");
            }
            if (description.length() < 10) {
                throw new IllegalArgumentException("Assessment description must have at least 10 characters.");
            }
            if (assessmentDatePicker.getValue() == null) {
                throw new IllegalArgumentException("Assessment date is required.");
            }
            if (assessmentTypeBox.getValue() == null || assessmentTypeBox.getValue().isBlank()) {
                throw new IllegalArgumentException("Assessment type is required.");
            }

            double maxScore = requireDoubleInRange(assessmentMaxScoreField.getText(), "Max score", 1.0, 100.0);

            service.createAssessment(
                    teacherId,
                    courseId,
                    classeId,
                    contenuId,
                    AssessmentType.fromValue(assessmentTypeBox.getValue()),
                    title,
                    description,
                    assessmentDatePicker.getValue(),
                    maxScore
            );
            assessmentTitleField.clear();
            assessmentDescriptionArea.clear();
            refreshAssessments();
            showFeedback("Assessment created.");
        } catch (Exception e) {
            showFeedback("Create assessment failed: " + e.getMessage());
        }
    }

    @FXML
    private void onDeleteSelectedAssessment() {
        try {
            if (selectedAssessmentId == null) {
                throw new IllegalArgumentException("Please select an assessment card first.");
            }
            if (!confirmDeletion("Delete this assessment? This action cannot be undone.")) {
                showFeedback("Deletion cancelled.");
                return;
            }
            service.deleteAssessment(selectedAssessmentId);
            selectedAssessmentId = null;
            selectedAssessmentLabel.setText("Selected assessment: none");
            gradesCardsBox.getChildren().clear();
            refreshAssessments();
            showFeedback("Assessment deleted.");
        } catch (Exception e) {
            showFeedback("Delete assessment failed: " + e.getMessage());
        }
    }

    @FXML
    private void onLoadAssessmentGrades() {
        try {
            if (selectedAssessmentId == null) {
                throw new IllegalArgumentException("Please select an assessment card first.");
            }
            List<Grade> grades = new ArrayList<>(service.getGradesByAssessment(selectedAssessmentId));
            sortGrades(grades, selectedSort(gradesSortBox));
            renderGradesCards(grades);
            showFeedback("Grades loaded.");
        } catch (Exception e) {
            showFeedback("Load grades failed: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveGrade() {
        try {
            if (selectedAssessmentId == null) {
                throw new IllegalArgumentException("Please select an assessment card first.");
            }
            Integer studentId = service.findUserIdByName(requireNotBlank(gradeStudentNameField.getText(), "Student name"));
            if (studentId == null) {
                throw new IllegalArgumentException("Student name not found.");
            }
            double score = requireDoubleInRange(gradeScoreInputField.getText(), "Grade score", 0.0, 100.0);
            String comment = requireNotBlank(gradeCommentInputField.getText(), "Grade comment");

            service.saveGrade(selectedAssessmentId, studentId, teacherId, score, comment);
            onLoadAssessmentGrades();
            showFeedback("Grade saved.");
        } catch (Exception e) {
            showFeedback("Save grade failed: " + e.getMessage());
        }
    }

    private void refreshTeacherSpace() {
        refreshAssessments();
        refreshTeacherSchedule();
    }

    private void refreshAssessments() {
        List<Assessment> assessments = new ArrayList<>(service.getAssessmentsByTeacher(teacherId));
        sortAssessments(assessments, selectedSort(assessmentsSortBox));

        assessmentsCardsBox.getChildren().clear();
        if (assessments.isEmpty()) {
            assessmentsCardsBox.getChildren().add(emptyCard("No assessments available"));
            return;
        }
        for (Assessment assessment : assessments) {
            assessmentsCardsBox.getChildren().add(createAssessmentCard(assessment));
        }
    }

    private void refreshTeacherSchedule() {
        List<Schedule> schedules = new ArrayList<>(service.getScheduleByTeacher(teacherId));
        String classFilter = classNameField.getText() == null ? "" : classNameField.getText().trim().toLowerCase(Locale.ROOT);
        if (!classFilter.isBlank()) {
            schedules.removeIf(s -> {
                String className = s.getClasse() == null ? null : service.resolveClasseName(s.getClasse().getId());
                return className == null || !className.trim().toLowerCase(Locale.ROOT).contains(classFilter);
            });
        }
        sortSchedules(schedules, selectedSort(scheduleSortBox));

        scheduleCardsBox.getChildren().clear();
        if (schedules.isEmpty()) {
            scheduleCardsBox.getChildren().add(emptyCard("No schedule entries available"));
            return;
        }
        for (Schedule schedule : schedules) {
            scheduleCardsBox.getChildren().add(createScheduleCard(schedule));
        }
    }

    private VBox createAssessmentCard(Assessment assessment) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");

        Label titleLabel = new Label(safe(assessment.getTitle()));
        titleLabel.getStyleClass().add("card-title");

        String courseName = assessment.getCourse() == null ? null : service.resolveCourseTitle(assessment.getCourse().getId());
        String classeName = assessment.getClasse() == null ? null : service.resolveClasseName(assessment.getClasse().getId());
        String contenuTitle = assessment.getContenu() == null ? null : service.resolveContenuTitle(assessment.getContenu().getId());

        Label typeLabel = new Label("Type: " + safe(assessment.getType()));
        Label courseLabel = new Label("Course: " + safe(courseName));
        Label classeLabel = new Label("Class: " + safe(classeName));
        Label contentLabel = new Label("Content: " + safe(contenuTitle));
        Label maxScoreLabel = new Label("Max Score: " + df.format(assessment.getMaxScore()));
        Label dateLabel = new Label("Date: " + formatDate(assessment.getDate()));
        Label descriptionLabel = new Label("Description: " + safe(assessment.getDescription()));
        descriptionLabel.setWrapText(true);

        Button viewButton = new Button("Select");
        viewButton.getStyleClass().add("ghost-button");
        viewButton.setOnAction(event -> {
            if (assessment.getId() == null) {
                showFeedback("Unable to select unsaved assessment.");
                return;
            }
            selectedAssessmentId = assessment.getId();
            selectedAssessmentLabel.setText("Selected assessment: " + safe(assessment.getTitle()));
            onLoadAssessmentGrades();
        });

        Button editButton = new Button("Load to Form");
        editButton.getStyleClass().add("primary-button");
        editButton.setOnAction(event -> loadForEdit(assessment));

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event -> {
            selectedAssessmentId = assessment.getId();
            selectedAssessmentLabel.setText("Selected assessment: " + safe(assessment.getTitle()));
            onDeleteSelectedAssessment();
        });

        HBox actions = new HBox(8, viewButton, editButton, deleteButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
                titleLabel,
                typeLabel,
                courseLabel,
                classeLabel,
                contentLabel,
                maxScoreLabel,
                dateLabel,
                descriptionLabel,
                actions
        );
        return card;
    }

    private void renderGradesCards(List<Grade> grades) {
        gradesCardsBox.getChildren().clear();
        if (grades.isEmpty()) {
            gradesCardsBox.getChildren().add(emptyCard("No grades available for this assessment"));
            return;
        }
        for (Grade grade : grades) {
            gradesCardsBox.getChildren().add(createGradeCard(grade));
        }
    }

    private VBox createGradeCard(Grade grade) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");

        String studentName = grade.getUserByStudentId() == null ? null : service.resolveUserDisplayName(grade.getUserByStudentId().getId());
        Label studentLabel = new Label("Student: " + safe(studentName));
        studentLabel.getStyleClass().add("card-title");

        Label scoreLabel = new Label("Score: " + df.format(grade.getScore()));
        Label commentLabel = new Label("Comment: " + safe(grade.getComment()));
        commentLabel.setWrapText(true);

        card.getChildren().addAll(studentLabel, scoreLabel, commentLabel);
        return card;
    }

    private VBox createScheduleCard(Schedule schedule) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");

        Label dayLabel = new Label(safe(schedule.getDayOfWeek()));
        dayLabel.getStyleClass().add("card-title");

        String className = schedule.getClasse() == null ? null : service.resolveClasseName(schedule.getClasse().getId());
        String courseName = schedule.getCourse() == null ? null : service.resolveCourseTitle(schedule.getCourse().getId());
        Label timeLabel = new Label("Time: " + schedule.getStartTime() + " - " + schedule.getEndTime());
        Label classLabel = new Label("Class: " + safe(className));
        Label courseLabel = new Label("Course: " + safe(courseName));
        Label roomLabel = new Label("Room: " + safe(schedule.getRoom()));

        card.getChildren().addAll(dayLabel, timeLabel, classLabel, courseLabel, roomLabel);
        return card;
    }

    private VBox emptyCard(String message) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");
        card.getChildren().add(new Label(message));
        return card;
    }

    private void loadForEdit(Assessment assessment) {
        assessmentTitleField.setText(safe(assessment.getTitle()));
        assessmentTypeBox.getSelectionModel().select(safe(assessment.getType()));
        assessmentMaxScoreField.setText(df.format(assessment.getMaxScore()));
        assessmentDescriptionArea.setText(safe(assessment.getDescription()));
        assessmentDatePicker.setValue(assessment.getDate() == null ? null : assessment.getDate().toLocalDateTime().toLocalDate());
        assessmentCourseNameField.setText(assessment.getCourse() == null ? "" : safe(service.resolveCourseTitle(assessment.getCourse().getId())));
        assessmentClasseNameField.setText(assessment.getClasse() == null ? "" : safe(service.resolveClasseName(assessment.getClasse().getId())));
        assessmentContenuTitleField.setText(assessment.getContenu() == null ? "" : safe(service.resolveContenuTitle(assessment.getContenu().getId())));
        selectedAssessmentId = assessment.getId();
        selectedAssessmentLabel.setText("Selected assessment: " + safe(assessment.getTitle()));
    }

    private void sortAssessments(List<Assessment> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(a -> safeForSort(a.getTitle())));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(Assessment::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Assessment::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(Assessment::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Assessment::getId, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void sortGrades(List<Grade> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(g -> safeForSort(g.getUserByStudentId() == null ? null : service.resolveUserDisplayName(g.getUserByStudentId().getId()))));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(Grade::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(Grade::getId, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void sortSchedules(List<Schedule> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing((Schedule s) -> safeForSort(s.getCourse() == null ? null : service.resolveCourseTitle(s.getCourse().getId())))
                    .thenComparing((Schedule s) -> safeForSort(s.getClasse() == null ? null : service.resolveClasseName(s.getClasse().getId()))));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(Schedule::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(Schedule::getId, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private String selectedSort(ComboBox<String> comboBox) {
        return comboBox.getValue() == null ? "NEW" : comboBox.getValue().trim().toUpperCase(Locale.ROOT);
    }

    private void installInputValidation() {
        setLengthField(classNameField, 120);
        setLengthField(assessmentTitleField, 150);
        setLengthField(assessmentCourseNameField, 150);
        setLengthField(assessmentClasseNameField, 150);
        setLengthField(assessmentContenuTitleField, 150);
        setLengthField(gradeStudentNameField, 150);
        setLengthField(gradeCommentInputField, 220);
        setDecimalField(assessmentMaxScoreField);
        setDecimalField(gradeScoreInputField);
    }

    private void setLengthField(TextField field, int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.length() <= maxLength ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private void setDecimalField(TextField field) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.matches("\\d{0,3}([.]\\d{0,2})?") ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private double requireDoubleInRange(String rawValue, String fieldName, double minInclusive, double maxInclusive) {
        String normalized = requireNotBlank(rawValue, fieldName);
        try {
            double parsed = Double.parseDouble(normalized);
            if (parsed < minInclusive || parsed > maxInclusive) {
                throw new IllegalArgumentException(fieldName + " must be between " + minInclusive + " and " + maxInclusive + ".");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    private Integer optionalNameToId(String rawValue, String fieldName, java.util.function.Function<String, Integer> resolver) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        Integer id = resolver.apply(rawValue.trim());
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " not found.");
        }
        return id;
    }

    private String requireNotBlank(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return rawValue.trim();
    }

    private String formatDate(Timestamp value) {
        return value == null ? "-" : value.toLocalDateTime().toLocalDate().toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeForSort(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void showFeedback(String text) {
        feedbackLabel.setText(text);
    }

    private boolean confirmDeletion(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Please confirm deletion");
        alert.setContentText(message);
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
}
