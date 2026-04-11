package controller;

import entities.User;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import service.UserService;

import java.net.URL;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class UserListController implements Initializable {

    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private TableView<User> userTable;

    @FXML
    private TableColumn<User, Number> idColumn;

    @FXML
    private TableColumn<User, String> emailColumn;

    @FXML
    private TableColumn<User, String> roleColumn;

    @FXML
    private TableColumn<User, String> statusColumn;

    @FXML
    private TableColumn<User, String> createdDateColumn;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> roleFilterComboBox;

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private Pagination pagination;

    private final UserService userService = new UserService();
    private final ObservableList<User> filteredUsers = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureTableColumns();
        configureFilters();
        configurePagination();
        configureRowDoubleClick();
        refreshUsers();
    }

    @FXML
    public void onCreateUser() {
        showInfo("Create User", "Open user creation form from this action.");
    }

    @FXML
    public void onEditUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showWarning("Select a user", "Please select a user to edit.");
            return;
        }

        showInfo("Edit User", "Open edit form for: " + selectedUser.getEmail());
    }

    @FXML
    public void onDeleteUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showWarning("Select a user", "Please select a user to delete.");
            return;
        }

        boolean confirmed = showConfirmation(
                "Delete user",
                "Delete selected user",
                "Are you sure you want to delete " + selectedUser.getEmail() + "?"
        );
        if (!confirmed) {
            return;
        }

        userService.deleteUser(selectedUser.getId().longValue());
        refreshUsers();
    }

    @FXML
    public void onToggleStatus() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showWarning("Select a user", "Please select a user to toggle status.");
            return;
        }

        String action = selectedUser.getIsActive() == (byte) 1 ? "deactivate" : "activate";
        boolean confirmed = showConfirmation(
                "Toggle status",
                "Change user status",
                "Are you sure you want to " + action + " " + selectedUser.getEmail() + "?"
        );
        if (!confirmed) {
            return;
        }

        userService.toggleUserStatus(selectedUser.getId().longValue());
        refreshUsers();
    }

    @FXML
    public void onRefreshUsers() {
        refreshUsers();
    }

    private void configureTableColumns() {
        idColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getId()));
        emailColumn.setCellValueFactory(cellData -> new SimpleStringProperty(safeText(cellData.getValue().getEmail())));
        roleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatRole(cellData.getValue().getRole())));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getIsActive() == (byte) 1 ? "Active" : "Inactive"));
        createdDateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatCreatedAt(cellData.getValue().getCreatedAt())));
    }

    private void configureFilters() {
        roleFilterComboBox.setItems(FXCollections.observableArrayList(
                "ALL",
                "ADMIN",
                "TEACHER",
                "STUDENT",
                "PARTNER"
        ));
        roleFilterComboBox.getSelectionModel().selectFirst();

        statusFilterComboBox.setItems(FXCollections.observableArrayList(
                "ALL",
                "ACTIVE",
                "INACTIVE"
        ));
        statusFilterComboBox.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        roleFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
    }

    private void configurePagination() {
        pagination.currentPageIndexProperty().addListener((obs, oldIndex, newIndex) -> updateTablePage(newIndex.intValue()));
        pagination.setPageFactory(pageIndex -> {
            updateTablePage(pageIndex);
            return new Region();
        });
    }

    private void configureRowDoubleClick() {
        userTable.setRowFactory(tableView -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onViewUserDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void refreshUsers() {
        applyFilters();
    }

    private void applyFilters() {
        String searchTerm = blankToNull(searchField.getText());
        String roleFilter = normalizeRoleFilter(roleFilterComboBox.getValue());
        Boolean activeFilter = normalizeStatusFilter(statusFilterComboBox.getValue());

        List<User> users = userService.searchUsers(searchTerm, roleFilter, activeFilter);
        filteredUsers.setAll(users);

        int pageCount = Math.max(1, (int) Math.ceil((double) filteredUsers.size() / PAGE_SIZE));
        pagination.setPageCount(pageCount);
        pagination.setCurrentPageIndex(0);
        updateTablePage(0);
    }

    private void updateTablePage(int pageIndex) {
        int safePageIndex = Math.max(pageIndex, 0);
        int fromIndex = safePageIndex * PAGE_SIZE;

        if (fromIndex >= filteredUsers.size()) {
            userTable.setItems(FXCollections.observableArrayList());
            return;
        }

        int toIndex = Math.min(fromIndex + PAGE_SIZE, filteredUsers.size());
        userTable.setItems(FXCollections.observableArrayList(filteredUsers.subList(fromIndex, toIndex)));
    }

    private void onViewUserDetails(User user) {
        showInfo("User Details", "Open details view for: " + user.getEmail());
    }

    private String normalizeRoleFilter(String selectedRole) {
        if (selectedRole == null || "ALL".equalsIgnoreCase(selectedRole)) {
            return null;
        }

        return switch (selectedRole.toUpperCase()) {
            case "ADMIN" -> "ROLE_ADMIN";
            case "TEACHER" -> "ROLE_TEACHER";
            case "STUDENT" -> "ROLE_STUDENT";
            case "PARTNER" -> "ROLE_PARTNER";
            default -> selectedRole;
        };
    }

    private Boolean normalizeStatusFilter(String selectedStatus) {
        if (selectedStatus == null || "ALL".equalsIgnoreCase(selectedStatus)) {
            return null;
        }

        if ("ACTIVE".equalsIgnoreCase(selectedStatus)) {
            return true;
        }

        if ("INACTIVE".equalsIgnoreCase(selectedStatus)) {
            return false;
        }

        return null;
    }

    private String formatRole(String role) {
        if (role == null) {
            return "";
        }

        if (role.startsWith("ROLE_")) {
            return role.substring("ROLE_".length());
        }

        return role;
    }

    private String formatCreatedAt(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }

        return timestamp.toLocalDateTime().format(CREATED_AT_FORMATTER);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean showConfirmation(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}