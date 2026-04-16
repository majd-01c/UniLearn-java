package controller.evaluation;

import entities.DocumentRequest;
import entities.Reclamation;
import entities.Schedule;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import service.evaluation.EvaluationService;

import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

public class EvaluationAdminController {

    private static final Set<String> ALLOWED_RECLAMATION_STATUS = Set.of("pending", "resolved", "rejected", "approved");
    private static final Set<String> ALLOWED_DOCUMENT_STATUS = Set.of("pending", "processing", "approved", "rejected", "delivered");
    private static final Set<String> ALLOWED_WEEK_DAYS = Set.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    );

    @FXML
    private VBox reclamationsCardsBox;
    @FXML
    private ComboBox<String> complaintsSortBox;
    @FXML
    private TextField reclamationStatusField;
    @FXML
    private TextArea reclamationResponseArea;

    @FXML
    private VBox documentRequestsCardsBox;
    @FXML
    private ComboBox<String> documentsSortBox;
    @FXML
    private TextField documentStatusField;
    @FXML
    private TextField documentPathField;

    @FXML
    private VBox schedulesCardsBox;
    @FXML
    private ComboBox<String> schedulesSortBox;
    @FXML
    private TextField scheduleTeacherNameField;
    @FXML
    private TextField scheduleCourseNameField;
    @FXML
    private TextField scheduleClassNameField;
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
    private Label selectedReclamationLabel;
    @FXML
    private Label selectedDocumentLabel;
    @FXML
    private Label selectedScheduleLabel;
    @FXML
    private Button deleteSelectedScheduleButton;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();
    private Integer selectedReclamationId;
    private Integer selectedDocumentId;
    private Integer selectedScheduleId;

    @FXML
    public void initialize() {
        scheduleDayField.setText("monday");
        scheduleStartTimeField.setText("08:00");
        scheduleEndTimeField.setText("10:00");

        complaintsSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        complaintsSortBox.getSelectionModel().select("NEW");
        documentsSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        documentsSortBox.getSelectionModel().select("NEW");
        schedulesSortBox.getItems().setAll("NEW", "OLD", "A-Z");
        schedulesSortBox.getSelectionModel().select("NEW");

        installInputValidation();
        refreshAll();
    }

    @FXML
    private void onRefreshAll() {
        refreshAll();
    }

    @FXML
    private void onSortComplaintsChanged() {
        refreshReclamations();
    }

    @FXML
    private void onSortDocumentsChanged() {
        refreshDocumentRequests();
    }

    @FXML
    private void onSortSchedulesChanged() {
        refreshSchedules();
    }

    @FXML
    private void onUpdateReclamation() {
        try {
            if (selectedReclamationId == null) {
                throw new IllegalArgumentException("Please select a complaint card first.");
            }
            String status = normalizeStatus(reclamationStatusField.getText(), "Reclamation status", ALLOWED_RECLAMATION_STATUS);
            String response = requireNotBlank(reclamationResponseArea.getText(), "Admin response");
            service.updateReclamationStatus(selectedReclamationId, status, response);
            refreshReclamations();
            showFeedback("Complaint updated.");
        } catch (Exception e) {
            showFeedback("Complaint update failed: " + e.getMessage());
        }
    }

    @FXML
    private void onUpdateDocumentRequest() {
        try {
            if (selectedDocumentId == null) {
                throw new IllegalArgumentException("Please select a document request card first.");
            }
            String status = normalizeStatus(documentStatusField.getText(), "Document status", ALLOWED_DOCUMENT_STATUS);
            String path = requireNotBlank(documentPathField.getText(), "Document path");
            service.updateDocumentRequest(selectedDocumentId, status, path);
            refreshDocumentRequests();
            showFeedback("Document request updated.");
        } catch (Exception e) {
            showFeedback("Document request update failed: " + e.getMessage());
        }
    }

    @FXML
    private void onCreateSchedule() {
        try {
            Integer teacherId = null;
            if (scheduleTeacherNameField.getText() != null && !scheduleTeacherNameField.getText().isBlank()) {
                teacherId = service.findUserIdByName(scheduleTeacherNameField.getText());
                if (teacherId == null) {
                    throw new IllegalArgumentException("Teacher name not found.");
                }
            }

            Integer courseId = service.findCourseIdByName(requireNotBlank(scheduleCourseNameField.getText(), "Course name"));
            if (courseId == null) {
                throw new IllegalArgumentException("Course name not found.");
            }

            Integer classeId = service.findClasseIdByName(requireNotBlank(scheduleClassNameField.getText(), "Class name"));
            if (classeId == null) {
                throw new IllegalArgumentException("Class name not found.");
            }

            String dayOfWeek = requireNotBlank(scheduleDayField.getText(), "Day").toLowerCase(Locale.ROOT);
            if (!ALLOWED_WEEK_DAYS.contains(dayOfWeek)) {
                throw new IllegalArgumentException("Day must be between monday and sunday.");
            }
            if (scheduleStartDatePicker.getValue() == null) {
                throw new IllegalArgumentException("Start date is required.");
            }
            if (scheduleEndDatePicker.getValue() != null && scheduleEndDatePicker.getValue().isBefore(scheduleStartDatePicker.getValue())) {
                throw new IllegalArgumentException("End date cannot be before start date.");
            }

            Time startTime = parseTime(scheduleStartTimeField.getText(), "Start time");
            Time endTime = parseTime(scheduleEndTimeField.getText(), "End time");
            if (!endTime.toLocalTime().isAfter(startTime.toLocalTime())) {
                throw new IllegalArgumentException("End time must be after start time.");
            }

            String room = requireNotBlank(scheduleRoomField.getText(), "Room");

            service.createSchedule(
                    teacherId,
                    courseId,
                    classeId,
                    dayOfWeek,
                    scheduleStartDatePicker.getValue(),
                    scheduleEndDatePicker.getValue(),
                    startTime,
                    endTime,
                    room
            );
            refreshSchedules();
            showFeedback("Schedule created.");
        } catch (Exception e) {
            showFeedback("Create schedule failed: " + e.getMessage());
        }
    }

    @FXML
    private void onDeleteSelectedSchedule() {
        try {
            if (selectedScheduleId == null) {
                throw new IllegalArgumentException("Please select a schedule card first.");
            }
            service.deleteSchedule(selectedScheduleId);
            clearSelectedSchedule();
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
        List<Reclamation> rows = new ArrayList<>(service.getAllReclamations());
        sortReclamations(rows, selectedSort(complaintsSortBox));

        reclamationsCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            reclamationsCardsBox.getChildren().add(emptyCard("No complaints available"));
            return;
        }
        for (Reclamation row : rows) {
            reclamationsCardsBox.getChildren().add(createReclamationCard(row));
        }
    }

    private void refreshDocumentRequests() {
        List<DocumentRequest> rows = new ArrayList<>(service.getAllDocumentRequests());
        sortDocumentRequests(rows, selectedSort(documentsSortBox));

        documentRequestsCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            documentRequestsCardsBox.getChildren().add(emptyCard("No document requests available"));
            return;
        }
        for (DocumentRequest row : rows) {
            documentRequestsCardsBox.getChildren().add(createDocumentRequestCard(row));
        }
    }

    private void refreshSchedules() {
        List<Schedule> rows = new ArrayList<>(service.getAllSchedules());
        sortSchedules(rows, selectedSort(schedulesSortBox));

        schedulesCardsBox.getChildren().clear();
        if (rows.isEmpty()) {
            schedulesCardsBox.getChildren().add(emptyCard("No schedules available"));
            return;
        }
        for (Schedule row : rows) {
            schedulesCardsBox.getChildren().add(createScheduleCard(row));
        }
    }

    private VBox createReclamationCard(Reclamation row) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");

        HBox header = new HBox(8);
        Label title = new Label(safe(row.getSubject()));
        title.getStyleClass().add("card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(safe(row.getStatus()));
        status.getStyleClass().addAll("eval-status-badge", statusStyleClass(row.getStatus()));
        header.getChildren().addAll(title, spacer, status);

        String studentName = row.getUser() == null ? null : service.resolveUserDisplayName(row.getUser().getId());
        Label studentLabel = new Label("Student: " + safe(studentName));
        Label descriptionLabel = new Label("Description: " + safe(row.getDescription()));
        descriptionLabel.setWrapText(true);
        Label responseLabel = new Label("Response: " + safe(row.getAdminResponse()));
        responseLabel.setWrapText(true);

        Button selectButton = new Button("Select");
        selectButton.getStyleClass().add("ghost-button");
        selectButton.setOnAction(event -> {
            selectedReclamationId = row.getId();
            selectedReclamationLabel.setText("Selected complaint: " + safe(row.getSubject()));
            reclamationStatusField.setText(safe(row.getStatus()));
            reclamationResponseArea.setText(row.getAdminResponse() == null ? "" : row.getAdminResponse());
        });

        card.getChildren().addAll(header, studentLabel, descriptionLabel, responseLabel, selectButton);
        return card;
    }

    private VBox createDocumentRequestCard(DocumentRequest row) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");

        HBox header = new HBox(8);
        Label title = new Label(safe(row.getDocumentType()));
        title.getStyleClass().add("card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(safe(row.getStatus()));
        status.getStyleClass().addAll("eval-status-badge", statusStyleClass(row.getStatus()));
        header.getChildren().addAll(title, spacer, status);

        String studentName = row.getUser() == null ? null : service.resolveUserDisplayName(row.getUser().getId());
        Label studentLabel = new Label("Student: " + safe(studentName));
        Label pathLabel = new Label("Path: " + safe(row.getDocumentPath()));
        pathLabel.setWrapText(true);
        Label infoLabel = new Label("Additional info: " + safe(row.getAdditionalInfo()));
        infoLabel.setWrapText(true);

        Button selectButton = new Button("Select");
        selectButton.getStyleClass().add("ghost-button");
        selectButton.setOnAction(event -> {
            selectedDocumentId = row.getId();
            selectedDocumentLabel.setText("Selected request: " + safe(row.getDocumentType()));
            documentStatusField.setText(safe(row.getStatus()));
            documentPathField.setText(row.getDocumentPath() == null ? "" : row.getDocumentPath());
        });

        card.getChildren().addAll(header, studentLabel, infoLabel, pathLabel, selectButton);
        return card;
    }

    private VBox createScheduleCard(Schedule row) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");

        String className = row.getClasse() == null ? null : service.resolveClasseName(row.getClasse().getId());
        String courseTitle = row.getCourse() == null ? null : service.resolveCourseTitle(row.getCourse().getId());
        String teacherName = row.getUser() == null ? null : service.resolveUserDisplayName(row.getUser().getId());

        Label title = new Label(safe(row.getDayOfWeek()));
        title.getStyleClass().add("card-title");
        Label timeLabel = new Label("Time: " + row.getStartTime() + " - " + row.getEndTime());
        Label classLabel = new Label("Class: " + safe(className));
        Label courseLabel = new Label("Course: " + safe(courseTitle));
        Label teacherLabel = new Label("Teacher: " + safe(teacherName));
        Label roomLabel = new Label("Room: " + safe(row.getRoom()));

        Button selectButton = new Button("Select");
        selectButton.getStyleClass().add("ghost-button");
        selectButton.setOnAction(event -> {
            selectedScheduleId = row.getId();
            selectedScheduleLabel.setText("Selected schedule: " + safe(courseTitle) + " / " + safe(className));
            deleteSelectedScheduleButton.setDisable(false);
        });

        card.getChildren().addAll(title, timeLabel, classLabel, courseLabel, teacherLabel, roomLabel, selectButton);
        return card;
    }

    private VBox emptyCard(String message) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("home-card", "eval-card");
        card.getChildren().add(new Label(message));
        return card;
    }

    private void sortReclamations(List<Reclamation> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(r -> safeForSort(r.getSubject())));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(Reclamation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Reclamation::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(Reclamation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Reclamation::getId, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void sortDocumentRequests(List<DocumentRequest> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(r -> safeForSort(r.getDocumentType())));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(DocumentRequest::getRequestedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(DocumentRequest::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(DocumentRequest::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(DocumentRequest::getId, Comparator.nullsLast(Comparator.reverseOrder())));
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

    private String statusStyleClass(String statusValue) {
        String normalized = statusValue == null ? "" : statusValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("approve") || normalized.contains("valide") || normalized.contains("accepted") || normalized.contains("resolved") || normalized.contains("delivered")) {
            return "status-approved";
        }
        if (normalized.contains("reject") || normalized.contains("refus") || normalized.contains("deny")) {
            return "status-rejected";
        }
        return "status-pending";
    }

    private void installInputValidation() {
        setLengthField(scheduleTeacherNameField, 150);
        setLengthField(scheduleCourseNameField, 150);
        setLengthField(scheduleClassNameField, 150);
        setLengthField(reclamationStatusField, 25);
        setLengthField(documentStatusField, 25);
        setLengthField(documentPathField, 255);
        setLengthField(scheduleRoomField, 100);
        setTimeField(scheduleStartTimeField);
        setTimeField(scheduleEndTimeField);
        deleteSelectedScheduleButton.setDisable(true);
        clearSelectedSchedule();
    }

    private void clearSelectedSchedule() {
        selectedScheduleId = null;
        selectedScheduleLabel.setText("Selected schedule: none");
        deleteSelectedScheduleButton.setDisable(true);
    }

    private void setLengthField(TextField field, int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.length() <= maxLength ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private void setTimeField(TextField field) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String next = change.getControlNewText();
            return next.matches("^([0-1]?\\d|2[0-3])?(:[0-5]?\\d?)?$") ? change : null;
        };
        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private String normalizeStatus(String rawValue, String fieldName, Set<String> allowedStatuses) {
        String normalized = requireNotBlank(rawValue, fieldName).toLowerCase(Locale.ROOT);
        if (!allowedStatuses.contains(normalized)) {
            throw new IllegalArgumentException(fieldName + " must be one of: " + String.join(", ", allowedStatuses) + ".");
        }
        return normalized;
    }

    private Time parseTime(String rawValue, String fieldName) {
        String normalized = requireNotBlank(rawValue, fieldName);
        try {
            return Time.valueOf(LocalTime.parse(normalized));
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " must be in HH:mm format.");
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

    private String safeForSort(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void showFeedback(String text) {
        feedbackLabel.setText(text);
    }
}
