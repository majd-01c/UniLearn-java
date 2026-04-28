package controller.iarooms;

import entities.iarooms.Room;
import entities.iarooms.RoomBooking;
import entities.iarooms.RoomConflict;
import entities.iarooms.TimetableUpload;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import service.iarooms.IARoomsService;
import service.iarooms.IARoomsService.AvailabilityResult;
import service.iarooms.IARoomsService.OccupiedRoom;
import service.iarooms.IARoomsService.Slot;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

public class IARoomsDashboardController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter UPLOAD_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label latestUploadLabel;
    @FXML private Label latestUploadMetaLabel;
    @FXML private Label roomCountLabel;
    @FXML private Label bookingCountLabel;
    @FXML private Label conflictCountLabel;
    @FXML private Label emptyCountLabel;
    @FXML private Label occupiedCountLabel;
    @FXML private Label emptyRoomsLabel;
    @FXML private Label selectedRoomLabel;
    @FXML private Label statusLabel;
    @FXML private Label scrapeStatusLabel;

    @FXML private TextField roomSearchField;
    @FXML private TextField scrapeBackendUrlField;
    @FXML private TextField scrapeStudentIdField;
    @FXML private PasswordField scrapePasswordField;
    @FXML private TextField scrapeTimeoutField;
    @FXML private TextField availabilityRoomFilter;
    @FXML private TextField availabilityBuildingFilter;
    @FXML private DatePicker availabilityDatePicker;
    @FXML private ComboBox<Slot> availabilitySlotCombo;
    @FXML private ComboBox<TimetableUpload> uploadSelectorCombo;
    @FXML private Button scrapeButton;
    @FXML private Button deleteUploadButton;

    @FXML private FlowPane roomsCardsPane;
    @FXML private FlowPane roomBookingsCardsPane;
    @FXML private FlowPane emptyRoomsCardsPane;
    @FXML private FlowPane occupiedRoomsCardsPane;
    @FXML private FlowPane conflictsCardsPane;

    private final IARoomsService service = new IARoomsService();
    private TimetableUpload latestUpload;
    private Room selectedRoom;
    private List<Room> currentRooms = List.of();
    private Map<Integer, Long> bookingCounts = Map.of();
    private boolean loadingUploads;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureUploadSelector();

        availabilitySlotCombo.setItems(FXCollections.observableArrayList(service.standardSlots()));
        if (!availabilitySlotCombo.getItems().isEmpty()) {
            availabilitySlotCombo.getSelectionModel().selectFirst();
        }
        scrapeBackendUrlField.setText(service.configuredBackendUrl());
        scrapeTimeoutField.setText("20");

        roomSearchField.textProperty().addListener((observable, oldValue, newValue) -> refreshRooms());
        uploadSelectorCombo.getSelectionModel().selectedItemProperty().addListener((observable, oldUpload, newUpload) -> {
            if (!loadingUploads) {
                latestUpload = newUpload;
                refreshSelectedUpload();
            }
        });

        loadDashboard(null);
    }

    @FXML
    private void onRefresh() {
        loadDashboard(latestUpload == null ? null : latestUpload.getId());
    }

    @FXML
    private void onSearchAvailability() {
        refreshAvailability();
    }

    @FXML
    private void onScrapeEsprit() {
        double timeoutSeconds = parseTimeout(scrapeTimeoutField.getText());
        String backendUrl = scrapeBackendUrlField.getText();
        String studentId = scrapeStudentIdField.getText();
        String password = scrapePasswordField.getText();

        scrapeButton.setDisable(true);
        scrapeStatusLabel.setText("Scraping Emplois.aspx from Esprit...");
        statusLabel.setText("Scrape in progress. This can take a little while.");

        Task<IARoomsService.ScrapeSummary> task = new Task<>() {
            @Override
            protected IARoomsService.ScrapeSummary call() {
                return service.scrapeEsprit(backendUrl, studentId, password, timeoutSeconds);
            }
        };

        task.setOnSucceeded(event -> {
            scrapeButton.setDisable(false);
            scrapePasswordField.clear();
            IARoomsService.ScrapeSummary summary = task.getValue();
            scrapeStatusLabel.setText("Stored " + summary.totalBookings() + " bookings, "
                    + summary.totalRooms() + " rooms, " + summary.conflicts() + " conflicts."
                    + warningSuffix(summary.warnings()));
            loadDashboard(summary.uploadId());
        });

        task.setOnFailed(event -> {
            scrapeButton.setDisable(false);
            Throwable exception = task.getException();
            String message = exception == null ? "Unknown scrape error" : exception.getMessage();
            scrapeStatusLabel.setText("Scrape failed: " + message);
            statusLabel.setText("Scrape failed. Check that the Python IArooms backend is running.");
            showError("Scrape failed", message);
        });

        Thread thread = new Thread(task, "iarooms-esprit-scrape");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onDeleteSelectedUpload() {
        TimetableUpload selectedUpload = uploadSelectorCombo.getSelectionModel().getSelectedItem();
        if (selectedUpload == null) {
            showWarning("No scrape selected", "Choose an Esprit scrape before deleting.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Esprit scrape");
        confirm.setHeaderText("Delete selected Esprit scrape?");
        confirm.setContentText("Delete " + formatUploadOption(selectedUpload) + " and all its bookings/conflicts?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        deleteUploadButton.setDisable(true);
        statusLabel.setText("Deleting selected Esprit scrape...");

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return service.deleteUpload(selectedUpload);
            }
        };

        task.setOnSucceeded(event -> {
            boolean deleted = Boolean.TRUE.equals(task.getValue());
            statusLabel.setText(deleted
                    ? "Deleted " + formatUploadOption(selectedUpload) + " and its bookings."
                    : "Selected Esprit scrape was already deleted.");
            loadDashboard(null);
        });

        task.setOnFailed(event -> {
            deleteUploadButton.setDisable(false);
            Throwable exception = task.getException();
            String message = exception == null ? "Unknown delete error" : exception.getMessage();
            statusLabel.setText("Delete failed.");
            showError("Delete failed", message);
        });

        Thread thread = new Thread(task, "iarooms-delete-scrape");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadDashboard(Integer preferredUploadId) {
        List<TimetableUpload> uploads = service.findUploads();
        TimetableUpload selectedUpload = chooseUpload(uploads, preferredUploadId);

        loadingUploads = true;
        uploadSelectorCombo.setItems(FXCollections.observableArrayList(uploads));
        uploadSelectorCombo.setDisable(uploads.isEmpty());
        if (selectedUpload == null) {
            uploadSelectorCombo.getSelectionModel().clearSelection();
        } else {
            uploadSelectorCombo.getSelectionModel().select(selectedUpload);
        }
        loadingUploads = false;

        latestUpload = selectedUpload;
        refreshSelectedUpload();
    }

    private void refreshSelectedUpload() {
        if (latestUpload == null) {
            latestUploadLabel.setText("No Esprit scrape found");
            latestUploadMetaLabel.setText("Run the Java scrape tab or Symfony IArooms scrape first.");
            roomCountLabel.setText("0");
            bookingCountLabel.setText("0");
            conflictCountLabel.setText("0");
            emptyCountLabel.setText("0");
            occupiedCountLabel.setText("0");
            emptyRoomsLabel.setText("No data");
            selectedRoomLabel.setText("Select a room to see its timetable bookings.");
            statusLabel.setText("Java and Symfony are pointing to the same database, but IArooms has no upload yet.");
            bookingCounts = Map.of();
            selectedRoom = null;
            currentRooms = List.of();
            deleteUploadButton.setDisable(true);
            availabilityDatePicker.setValue(null);
            setEmptyState(roomsCardsPane, "No rooms yet. Run a scrape first.");
            setEmptyState(roomBookingsCardsPane, "Select a room to see bookings.");
            setEmptyState(emptyRoomsCardsPane, "No availability data.");
            setEmptyState(occupiedRoomsCardsPane, "No availability data.");
            setEmptyState(conflictsCardsPane, "No conflicts to show.");
            return;
        }

        deleteUploadButton.setDisable(false);
        latestUploadLabel.setText(valueOrDefault(latestUpload.getOriginalFilename(), "Latest timetable upload"));
        latestUploadMetaLabel.setText("Uploaded " + formatDateTime(latestUpload.getUploadedAt())
                + " | " + latestUpload.getTotalBookings() + " bookings"
                + " | " + latestUpload.getTotalRooms() + " rooms");

        availabilityDatePicker.setValue(service.defaultSearchDate(latestUpload));

        bookingCounts = service.countBookingsByRoom(latestUpload);
        refreshRooms();
        refreshAvailability();
        refreshConflicts();
        statusLabel.setText("Loaded " + formatUploadOption(latestUpload) + " from shared MySQL database.");
    }

    private void refreshRooms() {
        if (latestUpload == null) {
            return;
        }

        currentRooms = service.findObservedRooms(latestUpload, roomSearchField.getText());
        roomCountLabel.setText(String.valueOf(currentRooms.size()));
        bookingCountLabel.setText(String.valueOf(latestUpload.getTotalBookings()));

        if (currentRooms.isEmpty()) {
            selectedRoom = null;
            setEmptyState(roomsCardsPane, "No rooms match this scrape/search.");
            showRoomBookings(null);
            return;
        }

        Integer selectedRoomId = selectedRoom == null ? null : selectedRoom.getId();
        selectedRoom = currentRooms.stream()
                .filter(room -> room.getId() != null && room.getId().equals(selectedRoomId))
                .findFirst()
                .orElse(currentRooms.get(0));

        renderRoomCards();
        showRoomBookings(selectedRoom);
    }

    private void showRoomBookings(Room room) {
        if (room == null || latestUpload == null) {
            selectedRoomLabel.setText("Select a room to see its timetable bookings.");
            setEmptyState(roomBookingsCardsPane, "Select a room to see bookings.");
            return;
        }

        List<RoomBooking> bookings = service.findBookings(room, latestUpload);
        selectedRoomLabel.setText(room.getName() + " | " + bookings.size() + " bookings in selected upload");
        if (bookings.isEmpty()) {
            setEmptyState(roomBookingsCardsPane, "No bookings stored for this room.");
            return;
        }

        roomBookingsCardsPane.getChildren().setAll(bookings.stream()
                .map(this::createBookingCard)
                .toList());
    }

    private void refreshAvailability() {
        if (latestUpload == null) {
            return;
        }

        LocalDate date = availabilityDatePicker.getValue();
        Slot slot = availabilitySlotCombo.getSelectionModel().getSelectedItem();
        if (date == null || slot == null) {
            showWarning("Missing search values", "Choose a date and a time slot before searching availability.");
            return;
        }

        AvailabilityResult result = service.findAvailability(
                latestUpload,
                date,
                slot.startTime(),
                slot.endTime(),
                availabilityRoomFilter.getText(),
                availabilityBuildingFilter.getText());

        emptyCountLabel.setText(String.valueOf(result.getEmptyRooms().size()));
        occupiedCountLabel.setText(String.valueOf(result.getOccupiedRooms().size()));
        emptyRoomsLabel.setText(result.getEmptyRooms().size() + " empty rooms and "
                + result.getOccupiedRooms().size() + " occupied rooms for "
                + formatDate(date) + " at " + formatTimeRange(slot.startTime(), slot.endTime()) + ".");

        if (result.getEmptyRooms().isEmpty()) {
            setEmptyState(emptyRoomsCardsPane, "No empty rooms found for this slot.");
        } else {
            emptyRoomsCardsPane.getChildren().setAll(result.getEmptyRooms().stream()
                    .map(this::createEmptyRoomCard)
                    .toList());
        }

        if (result.getOccupiedRooms().isEmpty()) {
            setEmptyState(occupiedRoomsCardsPane, "No occupied rooms found for this slot.");
        } else {
            occupiedRoomsCardsPane.getChildren().setAll(result.getOccupiedRooms().stream()
                    .map(this::createOccupiedRoomCard)
                    .toList());
        }
    }

    private void refreshConflicts() {
        if (latestUpload == null) {
            return;
        }

        List<RoomConflict> conflicts = service.findConflicts(latestUpload);
        conflictCountLabel.setText(String.valueOf(conflicts.size()));
        if (conflicts.isEmpty()) {
            setEmptyState(conflictsCardsPane, "No overlapping room bookings detected.");
            return;
        }

        conflictsCardsPane.getChildren().setAll(conflicts.stream()
                .map(this::createConflictCard)
                .toList());
    }

    private void configureUploadSelector() {
        uploadSelectorCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TimetableUpload upload) {
                return upload == null ? "" : formatUploadOption(upload);
            }

            @Override
            public TimetableUpload fromString(String value) {
                return null;
            }
        });
    }

    private TimetableUpload chooseUpload(List<TimetableUpload> uploads, Integer preferredUploadId) {
        if (uploads == null || uploads.isEmpty()) {
            return null;
        }

        if (preferredUploadId != null) {
            for (TimetableUpload upload : uploads) {
                if (upload.getId() != null && upload.getId().equals(preferredUploadId)) {
                    return upload;
                }
            }
        }

        return uploads.get(0);
    }

    private void renderRoomCards() {
        roomsCardsPane.getChildren().setAll(currentRooms.stream()
                .map(this::createRoomCard)
                .toList());
    }

    private Node createRoomCard(Room room) {
        VBox card = card("iarooms-room-card");
        if (selectedRoom != null && Objects.equals(selectedRoom.getId(), room.getId())) {
            card.getStyleClass().add("iarooms-room-card-selected");
        }
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> {
            selectedRoom = room;
            renderRoomCards();
            showRoomBookings(room);
        });

        Label name = styledLabel(valueOrDefault(room.getName(), "Unnamed room"), "iarooms-card-title");
        Label bookings = chip(bookingCounts.getOrDefault(room.getId(), 0L) + " bookings", "iarooms-chip-info");
        HBox header = new HBox(8, name, spacer(), bookings);
        header.getStyleClass().add("iarooms-card-header");

        HBox meta = new HBox(6,
                chip(valueOrDefault(room.getBuilding(), "Building N/A"), "iarooms-chip-muted"),
                chip(valueOrDefault(room.getType(), "Type N/A"), "iarooms-chip-muted"));

        Label capacity = styledLabel("Capacity: " + (room.getCapacity() == null ? "N/A" : room.getCapacity()), "card-text");

        card.getChildren().addAll(header, meta, capacity);
        return card;
    }

    private Node createBookingCard(RoomBooking booking) {
        VBox card = card("iarooms-booking-card");
        HBox header = new HBox(8,
                chip(formatDate(booking.getBookingDate()), "iarooms-chip-info"),
                chip(formatTimeRange(booking.getStartTime(), booking.getEndTime()), "iarooms-chip-muted"),
                spacer(),
                chip("Page " + booking.getSourcePage(), "iarooms-chip-muted"));

        Label group = styledLabel(valueOrDefault(booking.getGroupName(), "Unknown group"), "iarooms-card-title");
        Label course = styledLabel(valueOrDefault(booking.getCourseName(), "Unknown course"), "card-text");
        course.setWrapText(true);

        card.getChildren().addAll(header, group, course);
        return card;
    }

    private Node createEmptyRoomCard(Room room) {
        VBox card = card("iarooms-availability-card", "iarooms-empty-room-card");
        Label title = styledLabel(valueOrDefault(room.getName(), "Unnamed room"), "iarooms-card-title");
        HBox meta = new HBox(6,
                chip(valueOrDefault(room.getBuilding(), "Building N/A"), "iarooms-chip-muted"),
                chip(valueOrDefault(room.getType(), "Type N/A"), "iarooms-chip-success"));
        card.getChildren().addAll(title, meta);
        return card;
    }

    private Node createOccupiedRoomCard(OccupiedRoom room) {
        VBox card = card("iarooms-availability-card", "iarooms-occupied-room-card");
        HBox header = new HBox(8,
                styledLabel(valueOrDefault(room.room(), "Unnamed room"), "iarooms-card-title"),
                spacer(),
                chip(formatTimeRange(room.startTime(), room.endTime()), "iarooms-chip-warning"));

        Label group = styledLabel(valueOrDefault(room.groupName(), "Unknown group"), "iarooms-card-kicker");
        Label course = styledLabel(valueOrDefault(room.courseName(), "Unknown course"), "card-text");
        course.setWrapText(true);
        Label page = chip("Page " + room.sourcePage(), "iarooms-chip-muted");

        card.getChildren().addAll(header, group, course, page);
        return card;
    }

    private Node createConflictCard(RoomConflict conflict) {
        VBox card = card("iarooms-conflict-card");
        String roomName = conflict.getRoom() == null ? "N/A" : conflict.getRoom().getName();

        HBox header = new HBox(8,
                styledLabel(valueOrDefault(roomName, "Room N/A"), "iarooms-card-title"),
                spacer(),
                chip(formatDate(conflict.getBookingDate()), "iarooms-chip-muted"),
                chip(formatTimeRange(conflict.getStartTime(), conflict.getEndTime()), "iarooms-chip-danger"));

        Label groups = styledLabel(valueOrDefault(conflict.getBookingAGroupName(), "Unknown group")
                + " / " + valueOrDefault(conflict.getBookingBGroupName(), "Unknown group"), "iarooms-card-kicker");
        Label courses = styledLabel(valueOrDefault(conflict.getBookingACourseName(), "Unknown course")
                + " / " + valueOrDefault(conflict.getBookingBCourseName(), "Unknown course"), "card-text");
        courses.setWrapText(true);
        Label pages = chip("Pages " + conflict.getBookingASourcePage() + " / " + conflict.getBookingBSourcePage(), "iarooms-chip-muted");
        Label description = styledLabel(valueOrDefault(conflict.getDescription(), "Overlapping room booking."), "card-text");
        description.setWrapText(true);

        card.getChildren().addAll(header, groups, courses, pages, description);
        return card;
    }

    private VBox card(String... extraStyles) {
        VBox card = new VBox(8);
        card.getStyleClass().add("iarooms-card");
        card.getStyleClass().addAll(extraStyles);
        card.setPadding(new Insets(12));
        return card;
    }

    private Label styledLabel(String text, String... styles) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styles);
        return label;
    }

    private Label chip(String text, String style) {
        Label label = styledLabel(text, "iarooms-chip", style);
        label.setWrapText(false);
        return label;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, javafx.scene.layout.Priority.ALWAYS);
        return region;
    }

    private void setEmptyState(FlowPane pane, String message) {
        Label label = styledLabel(message, "empty-state");
        pane.getChildren().setAll(label);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "N/A" : date.format(DATE_FORMATTER);
    }

    private String formatTimeRange(LocalTime start, LocalTime end) {
        return formatTime(start) + " - " + formatTime(end);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "--:--" : time.format(TIME_FORMATTER);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "N/A" : dateTime.format(DATETIME_FORMATTER);
    }

    private String formatUploadOption(TimetableUpload upload) {
        if (upload == null) {
            return "No Esprit scrape";
        }

        String label = valueOrDefault(upload.getOriginalFilename(), "ESPRIT Emplois scrape");
        String range = formatUploadRange(upload);
        if (!range.isBlank()) {
            return label + " (" + range + ")";
        }
        if (upload.getUploadedAt() != null) {
            return label + " - " + formatDateTime(upload.getUploadedAt());
        }
        return label;
    }

    private String formatUploadRange(TimetableUpload upload) {
        if (upload.getWeekStart() != null && upload.getWeekEnd() != null) {
            return upload.getWeekStart().format(UPLOAD_DATE_FORMATTER)
                    + " - " + upload.getWeekEnd().format(UPLOAD_DATE_FORMATTER);
        }
        if (upload.getWeekStart() != null) {
            return upload.getWeekStart().format(UPLOAD_DATE_FORMATTER);
        }
        if (upload.getWeekEnd() != null) {
            return upload.getWeekEnd().format(UPLOAD_DATE_FORMATTER);
        }
        return "";
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private double parseTimeout(String value) {
        try {
            double parsed = Double.parseDouble(value == null ? "" : value.trim());
            return Math.max(5, Math.min(90, parsed));
        } catch (Exception ignored) {
            return 20;
        }
    }

    private String warningSuffix(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return "";
        }
        return " Warnings: " + String.join(" ", warnings);
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
