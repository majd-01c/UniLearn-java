package controller.evaluation;

import entities.DocumentRequest;
import entities.Reclamation;
import entities.Schedule;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import service.evaluation.EvaluationService;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private ComboBox<String> reclamationStatusCombo;
    @FXML
    private TextArea reclamationResponseArea;

    @FXML
    private VBox documentRequestsCardsBox;
    @FXML
    private ComboBox<String> documentsSortBox;
    @FXML
    private ComboBox<String> documentStatusCombo;
    @FXML
    private VBox pdfDropZone;
    @FXML
    private Label pdfFileNameLabel;

    @FXML
    private VBox schedulesCardsBox; // Changed to VBox to match FXML and resolve loading error
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
    private Button deleteScheduleButton;

    @FXML
    private Label feedbackLabel;

    private final EvaluationService service = new EvaluationService();
    private Integer selectedReclamationId;
    private Integer selectedDocumentId;
    private Integer selectedScheduleId;
    private String selectedDocumentPath;

    @FXML
    public void initialize() {
        scheduleDayField.setText("monday");
        scheduleStartTimeField.setText("08:00");
        scheduleEndTimeField.setText("10:00");

        reclamationStatusCombo.getItems().setAll("pending", "resolved", "rejected", "approved");
        reclamationStatusCombo.getSelectionModel().select("pending");
        documentStatusCombo.getItems().setAll("pending", "processing", "approved", "rejected", "delivered");
        documentStatusCombo.getSelectionModel().select("pending");

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
            String status = normalizeStatus(reclamationStatusCombo.getValue(), "Reclamation status", ALLOWED_RECLAMATION_STATUS);
            String response = requireNotBlank(reclamationResponseArea.getText(), "Admin response");
            service.updateReclamationStatus(selectedReclamationId, status, response);
            refreshReclamations();
            showFeedback("Complaint updated.", false);
        } catch (Exception e) {
            showFeedback("Complaint update failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onUpdateDocumentRequest() {
        try {
            if (selectedDocumentId == null) {
                throw new IllegalArgumentException("Please select a document request card first.");
            }
            String status = normalizeStatus(documentStatusCombo.getValue(), "Document status", ALLOWED_DOCUMENT_STATUS);
            String path = requireNotBlank(selectedDocumentPath, "Document path (browse or drag a PDF)");
            service.updateDocumentRequest(selectedDocumentId, status, path);
            refreshDocumentRequests();
            showFeedback("Document request updated.", false);
        } catch (Exception e) {
            showFeedback("Document request update failed: " + e.getMessage(), true);
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
            showFeedback("Schedule created.", false);
        } catch (Exception e) {
            showFeedback("Create schedule failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onDeleteSelectedSchedule() {
        try {
            if (selectedScheduleId == null) {
                throw new IllegalArgumentException("Please select a schedule card first.");
            }
            if (!confirmDeletion("Delete selected schedule?")) {
                showFeedback("Deletion cancelled.", false);
                return;
            }
            service.deleteSchedule(selectedScheduleId);
            clearSelectedSchedule();
            refreshSchedules();
            showFeedback("Schedule deleted.", false);
        } catch (Exception e) {
            showFeedback("Delete schedule failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onDownloadSchedule() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Academic Schedule");
            fileChooser.setInitialFileName("Academic_Schedule.pdf");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fileChooser.showSaveDialog(feedbackLabel.getScene().getWindow());

            if (file != null) {
                service.downloadAcademicSchedule(file);
                showFeedback("Schedule downloaded: " + file.getName(), false);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
            }
        } catch (Exception e) {
            showFeedback("Download schedule failed: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onPdfDragOver(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        if (dragboard.hasFiles() && hasPdfFile(dragboard.getFiles())) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    @FXML
    private void onPdfDropped(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        boolean success = false;
        if (dragboard.hasFiles()) {
            File pdf = firstPdfFile(dragboard.getFiles());
            if (pdf != null) {
                selectedDocumentPath = pdf.getAbsolutePath();
                pdfFileNameLabel.setText(pdf.getName());
                showFeedback("PDF selected: " + pdf.getName(), false);
                success = true;
            }
        }
        event.setDropCompleted(success);
        event.consume();
    }

    @FXML
    private void onBrowsePdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File pdf = chooser.showOpenDialog(feedbackLabel.getScene() == null ? null : feedbackLabel.getScene().getWindow());
        if (pdf == null) {
            return;
        }
        selectedDocumentPath = pdf.getAbsolutePath();
        pdfFileNameLabel.setText(pdf.getName());
        showFeedback("PDF selected: " + pdf.getName(), false);
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

        // Group by Day (Monday-Sunday)
        Map<String, List<Schedule>> byDay = new LinkedHashMap<>();
        String[] daysOrder = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (String d : daysOrder) byDay.put(d, new ArrayList<>());

        for (Schedule s : rows) {
            String day = s.getDayOfWeek() == null ? "Monday" : s.getDayOfWeek().trim();
            String normalized = day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
            if (byDay.containsKey(normalized)) byDay.get(normalized).add(s);
        }

        for (Map.Entry<String, List<Schedule>> entry : byDay.entrySet()) {
            schedulesCardsBox.getChildren().add(createDayScheduleCard(entry.getKey(), entry.getValue()));
        }
    }

    private VBox createDayScheduleCard(String dayName, List<Schedule> dayClasses) {
        VBox dayCard = new VBox(10);
        dayCard.getStyleClass().add("eval-data-card");
        dayCard.setMinWidth(240);
        dayCard.setPadding(new Insets(12));
        dayCard.setStyle("-fx-background-color: rgba(30,60,120,0.35); -fx-border-color: rgba(56,139,255,0.2); -fx-background-radius: 16; -fx-border-radius: 16; -fx-border-width: 1;");

        Label dayLabel = new Label(dayName.toUpperCase());
        dayLabel.setStyle("-fx-font-weight: 900; -fx-text-fill: #7ec4ff; -fx-font-size: 14px;");
        dayCard.getChildren().add(dayLabel);

        if (dayClasses.isEmpty()) {
            Label empty = new Label("No sessions");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
            dayCard.getChildren().add(empty);
        } else {
            for (Schedule s : dayClasses) {
                VBox item = new VBox(2);
                item.setPadding(new Insets(8));
                item.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #f1f5f9;");

                String course = s.getCourse() == null ? "Course" : service.resolveCourseTitle(s.getCourse().getId());
                String classe = s.getClasse() == null ? "Class" : service.resolveClasseName(s.getClasse().getId());
                String teacher = s.getUser() == null ? "TBA" : service.resolveUserDisplayName(s.getUser().getId());

                Label title = new Label(course);
                title.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                Label sub = new Label(classe + " | " + teacher);
                sub.setStyle("-fx-text-fill: #90c8ff; -fx-font-size: 10px;");
                Label time = new Label("🕒 " + s.getStartTime() + " - " + s.getEndTime());
                time.setStyle("-fx-text-fill: #64748b; -fx-font-size: 10px;");

                Button sel = new Button("Select");
                sel.getStyleClass().add("eval-ghost-btn");
                sel.setPadding(new Insets(2, 6, 2, 6));
                sel.setOnAction(e -> {
                    selectedScheduleId = s.getId();
                    selectedScheduleLabel.setText("Selected: " + course);
                    if (deleteScheduleButton != null) deleteScheduleButton.setDisable(false);
                });

                item.getChildren().addAll(title, sub, time, sel);
                dayCard.getChildren().add(item);
            }
        }
        return dayCard;
    }

    private VBox createReclamationCard(Reclamation row) {
        VBox card = new VBox(8);
        card.getStyleClass().add("eval-data-card");

        HBox header = new HBox(8);
        Label title = new Label(safe(row.getSubject()));
        title.getStyleClass().add("eval-data-card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(safe(row.getStatus()).toUpperCase());
        status.getStyleClass().addAll("eval-status-badge", statusStyleClass(row.getStatus()));
        header.getChildren().addAll(title, spacer, status);

        String studentName = row.getUser() == null ? null : service.resolveUserDisplayName(row.getUser().getId());
        Label studentLabel = new Label("STUDENT: " + safe(studentName));
        studentLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d0e8ff;");
        Label descriptionLabel = new Label(safe(row.getDescription()));
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic;");

        Label responseLabel = new Label("RESPONSE: " + safe(row.getAdminResponse()));
        responseLabel.setWrapText(true);
        responseLabel.setStyle("-fx-text-fill: #0ea5e9;");

        Button selectButton = new Button("Review Complaint");
        selectButton.getStyleClass().add("eval-ghost-btn");
        selectButton.setOnAction(event -> {
            selectedReclamationId = row.getId();
            selectedReclamationLabel.setText("Selected: " + safe(row.getSubject()));
            reclamationStatusCombo.getSelectionModel().select(safe(row.getStatus()).toLowerCase(Locale.ROOT));
            reclamationResponseArea.setText(row.getAdminResponse() == null ? "" : row.getAdminResponse());
        });

        card.getChildren().addAll(header, studentLabel, descriptionLabel, responseLabel, selectButton);
        return card;
    }

    private VBox createDocumentRequestCard(DocumentRequest row) {
        VBox card = new VBox(8);
        card.getStyleClass().add("eval-data-card");

        HBox header = new HBox(8);
        Label title = new Label(safe(row.getDocumentType()).toUpperCase());
        title.getStyleClass().add("eval-data-card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label(safe(row.getStatus()).toUpperCase());
        status.getStyleClass().addAll("eval-status-badge", statusStyleClass(row.getStatus()));
        header.getChildren().addAll(title, spacer, status);

        String studentName = row.getUser() == null ? null : service.resolveUserDisplayName(row.getUser().getId());
        Label studentLabel = new Label("STUDENT: " + safe(studentName));
        studentLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d0e8ff;");

        Label infoLabel = new Label("DETAILS: " + safe(row.getAdditionalInfo()));
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-text-fill: #64748b;");

        Button selectButton = new Button("Fulfill Request");
        selectButton.getStyleClass().add("eval-ghost-btn");
        selectButton.setOnAction(event -> {
            selectedDocumentId = row.getId();
            selectedDocumentLabel.setText("Selected: " + safe(row.getDocumentType()));
            documentStatusCombo.getSelectionModel().select(safe(row.getStatus()).toLowerCase(Locale.ROOT));
            selectedDocumentPath = row.getDocumentPath();
            pdfFileNameLabel.setText(selectedDocumentPath == null || selectedDocumentPath.isBlank() ? "No file selected" : new File(selectedDocumentPath).getName());
        });

        card.getChildren().addAll(header, studentLabel, infoLabel, selectButton);
        return card;
    }

    private VBox emptyCard(String message) {
        VBox card = new VBox(8);
        card.getStyleClass().add("eval-empty-card");
        card.getChildren().add(new Label(message));
        return card;
    }

    private void sortReclamations(List<Reclamation> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(r -> safeForSort(r.getSubject())));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(Reclamation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(Reclamation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void sortDocumentRequests(List<DocumentRequest> rows, String mode) {
        if ("A-Z".equals(mode)) {
            rows.sort(Comparator.comparing(r -> safeForSort(r.getDocumentType())));
            return;
        }
        if ("OLD".equals(mode)) {
            rows.sort(Comparator.comparing(DocumentRequest::getRequestedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            return;
        }
        rows.sort(Comparator.comparing(DocumentRequest::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void sortSchedules(List<Schedule> rows, String mode) {
        rows.sort(Comparator.comparing(Schedule::getStartTime, Comparator.nullsLast(java.sql.Time::compareTo)));
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
        setLengthField(scheduleRoomField, 100);
        setTimeField(scheduleStartTimeField);
        setTimeField(scheduleEndTimeField);
        if (deleteScheduleButton != null) {
            deleteScheduleButton.setDisable(true);
        }
        clearSelectedSchedule();
    }

    private void clearSelectedSchedule() {
        selectedScheduleId = null;
        selectedScheduleLabel.setText("Selected: none");
        if (deleteScheduleButton != null) {
            deleteScheduleButton.setDisable(true);
        }
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

    private String formatDate(Timestamp value) {
        return value == null ? "-" : value.toLocalDateTime().toLocalDate().toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeForSort(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void showFeedback(String text, boolean isError) {
        feedbackLabel.setText(text);
        feedbackLabel.getStyleClass().removeAll("eval-feedback", "eval-feedback-error");
        if (isError) {
            feedbackLabel.getStyleClass().add("eval-feedback-error");
        } else {
            feedbackLabel.getStyleClass().add("eval-feedback");
        }
    }

    private boolean hasPdfFile(List<File> files) {
        return firstPdfFile(files) != null;
    }

    private File firstPdfFile(List<File> files) {
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file != null && file.getName() != null && file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                return file;
            }
        }
        return null;
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
