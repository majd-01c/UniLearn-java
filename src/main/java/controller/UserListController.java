package controller;

import entities.User;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import service.UserService;
import util.AppNavigator;

import java.io.IOException;
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
        openUserForm(null);
    }

    public void onCreate() {
        onCreateUser();
    }

    @FXML
    public void onEditUser() {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showWarning("Select a user", "Please select a user to edit.");
            return;
        }

        openUserForm(selectedUser);
    }

    public void onEdit() {
        onEditUser();
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

    public void onDelete() {
        onDeleteUser();
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

    public void onToggleStatusAction() {
        onToggleStatus();
    }

    @FXML
    public void onRefreshUsers() {
        refreshUsers();
    }

    public void refreshData() {
        refreshUsers();
    }

    @FXML
    public void onChangeMyPassword() {
        AppNavigator.showChangePassword();
    }

    private void openUserForm(User userToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user/user-form.fxml"));
            Parent root = loader.load();

            UserFormController formController = loader.getController();
            formController.setOnSaveSuccess(this::refreshUsers);

            if (userToEdit == null) {
                formController.setCreateMode();
            } else {
                formController.setEditMode(userToEdit);
            }

            Stage dialogStage = new Stage();
            dialogStage.setTitle(userToEdit == null ? "Create User" : "Edit User");
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            if (userTable.getScene() != null && userTable.getScene().getWindow() != null) {
                dialogStage.initOwner(userTable.getScene().getWindow());
            }

            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();
        } catch (IOException exception) {
            showWarning("Open form failed", "Could not open user form view: " + exception.getMessage());
        }
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
                "PARTNER",
                "TRAINER"
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

    public void loadPage(int pageIndex) {
        int safePageIndex = Math.max(pageIndex, 0);
        pagination.setCurrentPageIndex(safePageIndex);
        updateTablePage(safePageIndex);
    }

    public void applyFilters(String search, String role, String status) {
        searchField.setText(search == null ? "" : search);

        if (role == null || role.isBlank()) {
            roleFilterComboBox.getSelectionModel().select("ALL");
        } else {
            roleFilterComboBox.getSelectionModel().select(role.toUpperCase());
        }

        if (status == null || status.isBlank()) {
            statusFilterComboBox.getSelectionModel().select("ALL");
        } else {
            statusFilterComboBox.getSelectionModel().select(status.toUpperCase());
        }

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
        openUserDetails(user);
    }

    private void openUserDetails(User user) {
        AppNavigator.showUserDetails(user);
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
            case "TRAINER" -> "ROLE_TRAINER";
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

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}