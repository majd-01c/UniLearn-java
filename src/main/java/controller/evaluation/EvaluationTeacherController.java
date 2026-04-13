package controller.evaluation;

import entities.Assessment;
import entities.Grade;
import entities.Schedule;
import evaluation.AssessmentType;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import security.UserSession;
import service.evaluation.EvaluationService;

import java.text.DecimalFormat;

public class EvaluationTeacherController {

    @FXML
    private TextField teacherIdField;
    @FXML
    private TextField classIdField;

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
    private TextField assessmentCourseIdField;
    @FXML
    private TextField assessmentClasseIdField;
    @FXML
    private TextField assessmentContenuIdField;

    @FXML
    private TableView<Assessment> assessmentsTable;
    @FXML
    private TableColumn<Assessment, String> assessmentIdCol;
    @FXML
    private TableColumn<Assessment, String> assessmentTitleCol;
    @FXML
    private TableColumn<Assessment, String> assessmentTypeCol;
    @FXML
    private TableColumn<Assessment, String> assessmentMaxCol;
    @FXML
    private TableColumn<Assessment, String> assessmentDateCol;

    @FXML
    private TextField selectedAssessmentIdField;
    @FXML
    private TableView<Grade> gradesTable;
    @FXML
    private TableColumn<Grade, String> gradeIdCol;
    @FXML
    private TableColumn<Grade, String> gradeStudentCol;
    @FXML
    private TableColumn<Grade, String> gradeScoreCol;
    @FXML
    private TableColumn<Grade, String> gradeCommentCol;

    @FXML
    private TextField gradeStudentIdField;
    @FXML
    private TextField gradeScoreInputField;
    @FXML
    private TextField gradeCommentInputField;

    @FXML
    private TableView<Schedule> scheduleTable;
    @FXML
    private TableColumn<Schedule, String> scheduleDayCol;
    @FXML
    private TableColumn<Schedule, String> scheduleTimeCol;
    @FXML
    private TableColumn<Schedule, String> scheduleClassCol;
    @FXML
    private TableColumn<Schedule, String> scheduleCourseCol;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();
    private final DecimalFormat df = new DecimalFormat("0.00");

    @FXML
    public void initialize() {
        teacherIdField.setText(String.valueOf(UserSession.getCurrentUserId().orElse(1)));
        teacherIdField.setEditable(false);
        teacherIdField.setDisable(true);
        classIdField.setText("1");

        assessmentTypeBox.getItems().setAll("CC", "EXAM", "OTHER");
        assessmentTypeBox.getSelectionModel().select("CC");
        assessmentMaxScoreField.setText("20");

        assessmentIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        assessmentTitleCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getTitle())));
        assessmentTypeCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getType())));
        assessmentMaxCol.setCellValueFactory(c -> new SimpleStringProperty(df.format(c.getValue().getMaxScore())));
        assessmentDateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDate() == null ? "-" : c.getValue().getDate().toString()));

        gradeIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        gradeStudentCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUserByStudentId() == null || c.getValue().getUserByStudentId().getId() == null
                        ? "-"
                        : String.valueOf(c.getValue().getUserByStudentId().getId())
        ));
        gradeScoreCol.setCellValueFactory(c -> new SimpleStringProperty(df.format(c.getValue().getScore())));
        gradeCommentCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getComment())));

        scheduleDayCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getDayOfWeek())));
        scheduleTimeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStartTime() + " - " + c.getValue().getEndTime()));
        scheduleClassCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClasse() == null ? "-" : safe(c.getValue().getClasse().getName())));
        scheduleCourseCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCourse() == null ? "-" : safe(c.getValue().getCourse().getTitle())));

        assessmentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue.getId() != null) {
                selectedAssessmentIdField.setText(String.valueOf(newValue.getId()));
            }
        });

        refreshTeacherSpace();
    }

    @FXML
    private void onRefreshTeacherSpace() {
        refreshTeacherSpace();
    }

    @FXML
    private void onCreateAssessment() {
        try {
            int teacherId = intValue(teacherIdField.getText());
            int courseId = intValue(assessmentCourseIdField.getText());
            Integer classeId = assessmentClasseIdField.getText().isBlank() ? null : intValue(assessmentClasseIdField.getText());
            Integer contenuId = assessmentContenuIdField.getText().isBlank() ? null : intValue(assessmentContenuIdField.getText());
            service.createAssessment(
                    teacherId,
                    courseId,
                    classeId,
                    contenuId,
                    AssessmentType.fromValue(assessmentTypeBox.getValue()),
                    assessmentTitleField.getText(),
                    assessmentDescriptionArea.getText(),
                    assessmentDatePicker.getValue(),
                    Double.parseDouble(assessmentMaxScoreField.getText())
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
            int assessmentId = intValue(selectedAssessmentIdField.getText());
            service.deleteAssessment(assessmentId);
            selectedAssessmentIdField.clear();
            gradesTable.getItems().clear();
            refreshAssessments();
            showFeedback("Assessment deleted.");
        } catch (Exception e) {
            showFeedback("Delete assessment failed: " + e.getMessage());
        }
    }

    @FXML
    private void onLoadAssessmentGrades() {
        try {
            int assessmentId = intValue(selectedAssessmentIdField.getText());
            gradesTable.setItems(FXCollections.observableArrayList(service.getGradesByAssessment(assessmentId)));
            showFeedback("Grades loaded for assessment " + assessmentId + ".");
        } catch (Exception e) {
            showFeedback("Load grades failed: " + e.getMessage());
        }
    }

    @FXML
    private void onSaveGrade() {
        try {
            int assessmentId = intValue(selectedAssessmentIdField.getText());
            int studentId = intValue(gradeStudentIdField.getText());
            int teacherId = intValue(teacherIdField.getText());
            double score = Double.parseDouble(gradeScoreInputField.getText());
            service.saveGrade(assessmentId, studentId, teacherId, score, gradeCommentInputField.getText());
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
        int teacherId = intValue(teacherIdField.getText());
        assessmentsTable.setItems(FXCollections.observableArrayList(service.getAssessmentsByTeacher(teacherId)));
    }

    private void refreshTeacherSchedule() {
        int teacherId = intValue(teacherIdField.getText());
        scheduleTable.setItems(FXCollections.observableArrayList(service.getScheduleByTeacher(teacherId)));
    }

    private int intValue(String value) {
        return Integer.parseInt(value.trim());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void showFeedback(String text) {
        feedbackLabel.setText(text);
    }
}
