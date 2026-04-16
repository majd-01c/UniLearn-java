package controller;

import entities.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import service.ThemeManager;
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
        private static final String[] DIALOG_STYLESHEETS = {
            "/view/styles/desktop-tokens.css",
            "/view/styles/desktop-shell.css",
            "/view/styles/desktop-components.css",
            "/view/styles/desktop-admin.css",
            "/view/styles/desktop-frontoffice.css",
            "/view/styles/desktop-animations.css",
            "/view/styles/unilearn-desktop.css"
        };

    @FXML
    private FlowPane userCardsContainer;

    @FXML
    private ScrollPane usersScrollPane;

    @FXML
    private Label emptyStateLabel;

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
    private User selectedUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureFilters();
        configurePagination();
        configureCardsLayout();
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
        User currentSelection = requireSelectedUser("edit");
        if (currentSelection == null) {
            return;
        }

        openUserForm(currentSelection);
    }

    public void onEdit() {
        onEditUser();
    }

    @FXML
    public void onDeleteUser() {
        User currentSelection = requireSelectedUser("delete");
        if (currentSelection == null) {
            return;
        }

        boolean confirmed = showConfirmation(
                "Delete user",
                "Delete selected user",
                "Are you sure you want to delete " + safeOrPlaceholder(currentSelection.getEmail()) + "?"
        );
        if (!confirmed) {
            return;
        }

        userService.deleteUser(currentSelection.getId().longValue());
        selectedUser = null;
        refreshUsers();
    }

    public void onDelete() {
        onDeleteUser();
    }

    @FXML
    public void onToggleStatus() {
        User currentSelection = requireSelectedUser("toggle status");
        if (currentSelection == null) {
            return;
        }

        String action = currentSelection.getIsActive() == (byte) 1 ? "deactivate" : "activate";
        boolean confirmed = showConfirmation(
                "Toggle status",
                "Change user status",
                "Are you sure you want to " + action + " " + safeOrPlaceholder(currentSelection.getEmail()) + "?"
        );
        if (!confirmed) {
            return;
        }

        userService.toggleUserStatus(currentSelection.getId().longValue());
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

            if (searchField.getScene() != null && searchField.getScene().getWindow() != null) {
                dialogStage.initOwner(searchField.getScene().getWindow());
            }

            Scene dialogScene = new Scene(root);
            applyDialogStyles(dialogScene);
            dialogStage.setScene(dialogScene);
            dialogStage.showAndWait();
        } catch (IOException exception) {
            showWarning("Open form failed", "Could not open user form view: " + exception.getMessage());
        }
    }

    private void applyDialogStyles(Scene scene) {
        for (String stylesheetPath : DIALOG_STYLESHEETS) {
            URL stylesheetUrl = getClass().getResource(stylesheetPath);
            if (stylesheetUrl != null) {
                scene.getStylesheets().add(stylesheetUrl.toExternalForm());
            }
        }

        ThemeManager.getInstance().applySavedTheme(scene);
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
        pagination.currentPageIndexProperty().addListener((obs, oldIndex, newIndex) -> updateCardsPage(newIndex.intValue()));
        pagination.setPageFactory(pageIndex -> {
            updateCardsPage(pageIndex);
            return new Region();
        });
    }

    private void configureCardsLayout() {
        usersScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                userCardsContainer.setPrefWrapLength(Math.max(320, newBounds.getWidth() - 6)));

        if (usersScrollPane.getViewportBounds() != null) {
            userCardsContainer.setPrefWrapLength(Math.max(320, usersScrollPane.getViewportBounds().getWidth() - 6));
        }
    }

    private void refreshUsers() {
        applyFilters();
    }

    public void loadPage(int pageIndex) {
        int safePageIndex = Math.max(pageIndex, 0);
        int maxPageIndex = Math.max(0, pagination.getPageCount() - 1);
        safePageIndex = Math.min(safePageIndex, maxPageIndex);
        pagination.setCurrentPageIndex(safePageIndex);
        updateCardsPage(safePageIndex);
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
        syncSelectionWithFilteredData();

        int pageCount = Math.max(1, (int) Math.ceil((double) filteredUsers.size() / PAGE_SIZE));
        pagination.setPageCount(pageCount);
        pagination.setCurrentPageIndex(0);
        updateCardsPage(0);
    }

    private void updateCardsPage(int pageIndex) {
        int safePageIndex = Math.max(pageIndex, 0);

        if (filteredUsers.isEmpty()) {
            userCardsContainer.getChildren().clear();
            setEmptyStateVisible(true);
            return;
        }

        int fromIndex = safePageIndex * PAGE_SIZE;
        if (fromIndex >= filteredUsers.size()) {
            int lastPageIndex = Math.max(0, pagination.getPageCount() - 1);
            if (lastPageIndex != safePageIndex) {
                pagination.setCurrentPageIndex(lastPageIndex);
                return;
            }
            fromIndex = 0;
        }

        int toIndex = Math.min(fromIndex + PAGE_SIZE, filteredUsers.size());
        renderUserCards(filteredUsers.subList(fromIndex, toIndex));
        setEmptyStateVisible(false);
    }

    private void renderUserCards(List<User> users) {
        userCardsContainer.getChildren().clear();
        users.forEach(user -> userCardsContainer.getChildren().add(buildUserCard(user)));
    }

    private VBox buildUserCard(User user) {
        VBox card = new VBox(10);
        card.getStyleClass().add("user-card");
        if (isSelected(user)) {
            card.getStyleClass().add("user-card-selected");
        }
        card.setPadding(new Insets(14));
        card.setPrefWidth(290);
        card.setMinWidth(250);
        card.setMaxWidth(320);

        HBox headingRow = new HBox(8);
        headingRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(displayName(user));
        nameLabel.getStyleClass().add("user-card-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label statusBadge = new Label(user.getIsActive() == (byte) 1 ? "Active" : "Inactive");
        statusBadge.getStyleClass().addAll("user-card-badge",
                user.getIsActive() == (byte) 1 ? "user-card-badge-active" : "user-card-badge-inactive");
        headingRow.getChildren().addAll(nameLabel, statusBadge);

        Label emailLabel = new Label(safeOrPlaceholder(user.getEmail()));
        emailLabel.getStyleClass().add("user-card-email");
        emailLabel.setWrapText(true);

        HBox chipsRow = new HBox(6);

        Label roleBadge = new Label(formatRole(user.getRole()));
        roleBadge.getStyleClass().addAll("user-card-badge", "user-card-badge-role");

        chipsRow.getChildren().add(roleBadge);

        VBox metaRows = new VBox(5);
        metaRows.getStyleClass().add("user-card-meta-box");
        metaRows.getChildren().addAll(
                buildMetaRow("Phone", safeOrPlaceholder(user.getPhone())),
                buildMetaRow("Location", safeOrPlaceholder(user.getLocation())),
                buildMetaRow("Created", safeOrPlaceholder(formatCreatedAt(user.getCreatedAt())))
        );

        Label hintLabel = new Label("Click to select, double-click for details.");
        hintLabel.getStyleClass().add("user-card-hint");

        card.getChildren().addAll(headingRow, emailLabel, chipsRow, metaRows, hintLabel);

        card.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            selectedUser = user;
            updateCardsPage(pagination.getCurrentPageIndex());

            if (event.getClickCount() == 2) {
                onViewUserDetails(user);
            }
        });

        return card;
    }

    private HBox buildMetaRow(String labelText, String valueText) {
        Label label = new Label(labelText + ":");
        label.getStyleClass().add("user-card-meta-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("user-card-meta-value");
        value.setWrapText(true);
        value.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(value, Priority.ALWAYS);

        HBox row = new HBox(6, label, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void setEmptyStateVisible(boolean visible) {
        emptyStateLabel.setVisible(visible);
        emptyStateLabel.setManaged(visible);
    }

    private User requireSelectedUser(String actionLabel) {
        if (selectedUser == null) {
            showWarning("Select a user", "Please select a user to " + actionLabel + ".");
            return null;
        }
        return selectedUser;
    }

    private void syncSelectionWithFilteredData() {
        if (selectedUser == null || selectedUser.getId() == null) {
            selectedUser = null;
            return;
        }

        Integer selectedId = selectedUser.getId();
        selectedUser = filteredUsers.stream()
                .filter(user -> user.getId() != null && user.getId().equals(selectedId))
                .findFirst()
                .orElse(null);
    }

    private boolean isSelected(User user) {
        if (selectedUser == null || selectedUser.getId() == null || user == null || user.getId() == null) {
            return false;
        }

        return selectedUser.getId().equals(user.getId());
    }

    private String displayName(User user) {
        String name = normalizeText(user == null ? null : user.getName());
        if (name != null) {
            return name;
        }

        String email = normalizeText(user == null ? null : user.getEmail());
        if (email != null) {
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                return email.substring(0, atIndex);
            }
            return email;
        }

        if (user != null && user.getId() != null) {
            return "User #" + user.getId();
        }

        return "Unknown User";
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

    private String safeOrPlaceholder(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "Not provided" : normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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