package controller.evaluation;

import entities.DocumentRequest;
import entities.Reclamation;
import entities.Schedule;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import service.evaluation.EvaluationService;

import java.sql.Time;
import java.time.LocalTime;

public class EvaluationAdminController {

    @FXML
    private TableView<Reclamation> reclamationsTable;
    @FXML
    private TableColumn<Reclamation, String> reclamationIdCol;
    @FXML
    private TableColumn<Reclamation, String> reclamationStudentCol;
    @FXML
    private TableColumn<Reclamation, String> reclamationSubjectCol;
    @FXML
    private TableColumn<Reclamation, String> reclamationStatusCol;

    @FXML
    private TextField reclamationIdField;
    @FXML
    private TextField reclamationStatusField;
    @FXML
    private TextArea reclamationResponseArea;

    @FXML
    private TableView<DocumentRequest> documentRequestsTable;
    @FXML
    private TableColumn<DocumentRequest, String> documentIdCol;
    @FXML
    private TableColumn<DocumentRequest, String> documentStudentCol;
    @FXML
    private TableColumn<DocumentRequest, String> documentTypeCol;
    @FXML
    private TableColumn<DocumentRequest, String> documentStatusCol;

    @FXML
    private TextField documentIdField;
    @FXML
    private TextField documentStatusField;
    @FXML
    private TextField documentPathField;

    @FXML
    private TableView<Schedule> schedulesTable;
    @FXML
    private TableColumn<Schedule, String> scheduleIdCol;
    @FXML
    private TableColumn<Schedule, String> scheduleDayCol;
    @FXML
    private TableColumn<Schedule, String> scheduleTimeCol;
    @FXML
    private TableColumn<Schedule, String> scheduleClassCol;
    @FXML
    private TableColumn<Schedule, String> scheduleTeacherCol;

    @FXML
    private TextField scheduleDeleteIdField;
    @FXML
    private TextField scheduleTeacherIdField;
    @FXML
    private TextField scheduleCourseIdField;
    @FXML
    private TextField scheduleClassIdField;
    @FXML
    private TextField scheduleDayField;
    @FXML
    private TextField scheduleStartTimeField;
    @FXML
    private TextField scheduleEndTimeField;
    @FXML
    private TextField scheduleRoomField;
    @FXML
    private DatePicker scheduleStartDatePicker;
    @FXML
    private DatePicker scheduleEndDatePicker;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();

    @FXML
    public void initialize() {
        reclamationIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        reclamationStudentCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUser() == null ? "-" : String.valueOf(c.getValue().getUser().getId())));
        reclamationSubjectCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getSubject())));
        reclamationStatusCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getStatus())));

        documentIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        documentStudentCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUser() == null ? "-" : String.valueOf(c.getValue().getUser().getId())));
        documentTypeCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getDocumentType())));
        documentStatusCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getStatus())));

        scheduleIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        scheduleDayCol.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getDayOfWeek())));
        scheduleTimeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStartTime() + " - " + c.getValue().getEndTime()));
        scheduleClassCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClasse() == null ? "-" : safe(c.getValue().getClasse().getName())));
        scheduleTeacherCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUser() == null ? "-" : String.valueOf(c.getValue().getUser().getId())));

        scheduleDayField.setText("monday");
        scheduleStartTimeField.setText("08:00");
        scheduleEndTimeField.setText("10:00");

        refreshAll();
    }

    @FXML
    private void onRefreshAll() {
        refreshAll();
    }

    @FXML
    private void onUpdateReclamation() {
        try {
            int id = intValue(reclamationIdField.getText());
            service.updateReclamationStatus(id, reclamationStatusField.getText(), reclamationResponseArea.getText());
            refreshReclamations();
            showFeedback("Reclamation updated.");
        } catch (Exception e) {
            showFeedback("Reclamation update failed: " + e.getMessage());
        }
    }

    @FXML
    private void onUpdateDocumentRequest() {
        try {
            int id = intValue(documentIdField.getText());
            service.updateDocumentRequest(id, documentStatusField.getText(), documentPathField.getText());
            refreshDocumentRequests();
            showFeedback("Document request updated.");
        } catch (Exception e) {
            showFeedback("Document request update failed: " + e.getMessage());
        }
    }

    @FXML
    private void onCreateSchedule() {
        try {
            Integer teacherId = scheduleTeacherIdField.getText().isBlank() ? null : intValue(scheduleTeacherIdField.getText());
            int courseId = intValue(scheduleCourseIdField.getText());
            int classeId = intValue(scheduleClassIdField.getText());
            service.createSchedule(
                    teacherId,
                    courseId,
                    classeId,
                    scheduleDayField.getText(),
                    scheduleStartDatePicker.getValue(),
                    scheduleEndDatePicker.getValue(),
                    Time.valueOf(LocalTime.parse(scheduleStartTimeField.getText())),
                    Time.valueOf(LocalTime.parse(scheduleEndTimeField.getText())),
                    scheduleRoomField.getText()
            );
            refreshSchedules();
            showFeedback("Schedule created.");
        } catch (Exception e) {
            showFeedback("Create schedule failed: " + e.getMessage());
        }
    }

    @FXML
    private void onDeleteSchedule() {
        try {
            int id = intValue(scheduleDeleteIdField.getText());
            service.deleteSchedule(id);
            refreshSchedules();
            showFeedback("Schedule deleted.");
        } catch (Exception e) {
            showFeedback("Delete schedule failed: " + e.getMessage());
        }
    }

    private void refreshAll() {
        refreshReclamations();
        refreshDocumentRequests();
        refreshSchedules();
    }

    private void refreshReclamations() {
        reclamationsTable.setItems(FXCollections.observableArrayList(service.getAllReclamations()));
    }

    private void refreshDocumentRequests() {
        documentRequestsTable.setItems(FXCollections.observableArrayList(service.getAllDocumentRequests()));
    }

    private void refreshSchedules() {
        schedulesTable.setItems(FXCollections.observableArrayList(service.getAllSchedules()));
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
