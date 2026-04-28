package controller.iarooms;

import entities.iarooms.Room;
import entities.iarooms.RoomBooking;
import entities.iarooms.RoomConflict;
import entities.iarooms.TimetableUpload;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import service.iarooms.IARoomsService;
import service.iarooms.IARoomsService.AvailabilityResult;
import service.iarooms.IARoomsService.OccupiedRoom;
import service.iarooms.IARoomsService.Slot;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IARoomsDashboardController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

    @FXML private TextField roomSearchField;
    @FXML private TextField availabilityRoomFilter;
    @FXML private TextField availabilityBuildingFilter;
    @FXML private DatePicker availabilityDatePicker;
    @FXML private ComboBox<Slot> availabilitySlotCombo;

    @FXML private TableView<Room> roomsTable;
    @FXML private TableView<RoomBooking> roomBookingsTable;
    @FXML private TableView<OccupiedRoom> occupiedRoomsTable;
    @FXML private TableView<RoomConflict> conflictsTable;

    private final IARoomsService service = new IARoomsService();
    private TimetableUpload latestUpload;
    private Map<Integer, Long> bookingCounts = Map.of();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureRoomsTable();
        configureRoomBookingsTable();
        configureOccupiedRoomsTable();
        configureConflictsTable();

        availabilitySlotCombo.setItems(FXCollections.observableArrayList(service.standardSlots()));
        if (!availabilitySlotCombo.getItems().isEmpty()) {
            availabilitySlotCombo.getSelectionModel().selectFirst();
        }

        roomSearchField.textProperty().addListener((observable, oldValue, newValue) -> refreshRooms());
        roomsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldRoom, newRoom) -> showRoomBookings(newRoom));

        loadDashboard();
    }

    @FXML
    private void onRefresh() {
        loadDashboard();
    }

    @FXML
    private void onSearchAvailability() {
        refreshAvailability();
    }

    private void loadDashboard() {
        latestUpload = service.findLatestUpload().orElse(null);
        if (latestUpload == null) {
            latestUploadLabel.setText("No Esprit scrape found");
            latestUploadMetaLabel.setText("Run the Symfony IArooms scrape first, then refresh this Java screen.");
            roomCountLabel.setText("0");
            bookingCountLabel.setText("0");
            conflictCountLabel.setText("0");
            emptyCountLabel.setText("0");
            occupiedCountLabel.setText("0");
            emptyRoomsLabel.setText("No data");
            statusLabel.setText("Java and Symfony are pointing to the same database, but IArooms has no upload yet.");
            roomsTable.getItems().clear();
            roomBookingsTable.getItems().clear();
            occupiedRoomsTable.getItems().clear();
            conflictsTable.getItems().clear();
            return;
        }

        latestUploadLabel.setText(valueOrDefault(latestUpload.getOriginalFilename(), "Latest timetable upload"));
        latestUploadMetaLabel.setText("Uploaded " + formatDateTime(latestUpload.getUploadedAt())
                + " | " + latestUpload.getTotalBookings() + " bookings"
                + " | " + latestUpload.getTotalRooms() + " rooms");

        if (availabilityDatePicker.getValue() == null) {
            availabilityDatePicker.setValue(service.defaultSearchDate(latestUpload));
        }

        bookingCounts = service.countBookingsByRoom(latestUpload);
        refreshRooms();
        refreshAvailability();
        refreshConflicts();
        statusLabel.setText("Loaded IArooms from shared MySQL database upload #" + latestUpload.getId() + ".");
    }

    private void refreshRooms() {
        if (latestUpload == null) {
            return;
        }

        List<Room> rooms = service.findObservedRooms(latestUpload, roomSearchField.getText());
        roomsTable.setItems(FXCollections.observableArrayList(rooms));
        roomCountLabel.setText(String.valueOf(rooms.size()));
        bookingCountLabel.setText(String.valueOf(latestUpload.getTotalBookings()));

        if (!rooms.isEmpty()) {
            roomsTable.getSelectionModel().selectFirst();
        } else {
            showRoomBookings(null);
        }
    }

    private void showRoomBookings(Room room) {
        if (room == null || latestUpload == null) {
            selectedRoomLabel.setText("Select a room to see its timetable bookings.");
            roomBookingsTable.getItems().clear();
            return;
        }

        List<RoomBooking> bookings = service.findBookings(room, latestUpload);
        selectedRoomLabel.setText(room.getName() + " | " + bookings.size() + " bookings in selected upload");
        roomBookingsTable.setItems(FXCollections.observableArrayList(bookings));
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
        occupiedRoomsTable.setItems(FXCollections.observableArrayList(result.getOccupiedRooms()));
        emptyRoomsLabel.setText(result.getEmptyRooms().isEmpty()
                ? "No empty rooms found for this slot."
                : result.getEmptyRooms().stream()
                        .map(Room::getName)
                        .limit(80)
                        .collect(Collectors.joining(", ")));
    }

    private void refreshConflicts() {
        if (latestUpload == null) {
            return;
        }

        List<RoomConflict> conflicts = service.findConflicts(latestUpload);
        conflictsTable.setItems(FXCollections.observableArrayList(conflicts));
        conflictCountLabel.setText(String.valueOf(conflicts.size()));
    }

    private void configureRoomsTable() {
        roomsTable.getColumns().setAll(
                stringColumn("Room", Room::getName, 130),
                stringColumn("Building", room -> valueOrDefault(room.getBuilding(), "N/A"), 110),
                stringColumn("Type", room -> valueOrDefault(room.getType(), "N/A"), 110),
                stringColumn("Capacity", room -> room.getCapacity() == null ? "N/A" : String.valueOf(room.getCapacity()), 90),
                stringColumn("Bookings", room -> {
                    if (room.getId() == null) {
                        return "0";
                    }
                    return String.valueOf(bookingCounts.getOrDefault(room.getId(), 0L));
                }, 90)
        );
    }

    private void configureRoomBookingsTable() {
        roomBookingsTable.getColumns().setAll(
                stringColumn("Date", booking -> formatDate(booking.getBookingDate()), 105),
                stringColumn("Time", booking -> formatTimeRange(booking.getStartTime(), booking.getEndTime()), 115),
                stringColumn("Group", RoomBooking::getGroupName, 120),
                stringColumn("Course", RoomBooking::getCourseName, 240),
                stringColumn("Page", booking -> String.valueOf(booking.getSourcePage()), 70)
        );
    }

    private void configureOccupiedRoomsTable() {
        occupiedRoomsTable.getColumns().setAll(
                stringColumn("Room", OccupiedRoom::room, 110),
                stringColumn("Group", OccupiedRoom::groupName, 120),
                stringColumn("Course", OccupiedRoom::courseName, 230),
                stringColumn("Time", room -> formatTimeRange(room.startTime(), room.endTime()), 115),
                stringColumn("Page", room -> String.valueOf(room.sourcePage()), 70)
        );
    }

    private void configureConflictsTable() {
        conflictsTable.getColumns().setAll(
                stringColumn("Room", conflict -> conflict.getRoom() == null ? "N/A" : conflict.getRoom().getName(), 100),
                stringColumn("Date", conflict -> formatDate(conflict.getBookingDate()), 105),
                stringColumn("Overlap", conflict -> formatTimeRange(conflict.getStartTime(), conflict.getEndTime()), 115),
                stringColumn("Groups", conflict -> conflict.getBookingAGroupName() + " / " + conflict.getBookingBGroupName(), 180),
                stringColumn("Courses", conflict -> conflict.getBookingACourseName() + " / " + conflict.getBookingBCourseName(), 260),
                stringColumn("Pages", conflict -> conflict.getBookingASourcePage() + " / " + conflict.getBookingBSourcePage(), 80),
                stringColumn("Description", RoomConflict::getDescription, 360)
        );
    }

    private <T> TableColumn<T, String> stringColumn(String title, Function<T, String> valueFactory, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueOrDefault(valueFactory.apply(cell.getValue()), "")));
        return column;
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

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
