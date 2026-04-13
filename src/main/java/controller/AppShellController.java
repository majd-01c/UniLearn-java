package controller;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import security.UserSession;
import service.UserService;
import util.AppNavigator;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class AppShellController implements Initializable {

    @FXML
    private VBox sideRail;

    @FXML
    private Label headerTitleLabel;

    @FXML
    private Label headerSubtitleLabel;

    @FXML
    private Label currentUserLabel;

    @FXML
    private Button navHomeButton;

    @FXML
    private Button navUsersButton;

    @FXML
    private Button navProfileButton;

    @FXML
    private Button navChangePasswordButton;

    @FXML
    private StackPane contentHost;

    private final UserService userService = new UserService();

    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AppNavigator.registerShell(this);
        showLoginView();
    }

    public void showLoginView() {
        currentUser = null;
        UserSession.clear();

        setNavigationVisible(false);
        setHeader(
                "UniLearn Desktop",
                "Sign in to continue"
        );

        loadCenter("/view/user/login.fxml", null);
    }

    public void handleLoginSuccess(User user) {
        if (user == null || user.getId() == null) {
            showLoginView();
            return;
        }

        currentUser = userService.getUserById(user.getId().longValue()).orElse(user);
        UserSession.setCurrentUser(currentUser);

        setNavigationVisible(true);
        currentUserLabel.setText(buildUserBadge(currentUser));
        navUsersButton.setDisable(!isAdmin(currentUser));

        showHomeView();
    }

    public void showHomeView() {
        if (!ensureAuthenticated()) {
            return;
        }

        setHeader(
                roleHomeTitle(currentUser),
                roleHomeSubtitle(currentUser)
        );

        loadCenter("/view/user/home.fxml", controller -> {
            if (controller instanceof HomeController homeController) {
                homeController.setUser(currentUser);
            }
        });
    }

    public void showUsersView() {
        if (!ensureAuthenticated()) {
            return;
        }

        if (!isAdmin(currentUser)) {
            showWarning("Access denied", "Only administrators can access User Management.");
            showHomeView();
            return;
        }

        setHeader("User Management", "Manage user accounts, roles, and statuses");
        loadCenter("/view/user/user-list.fxml", null);
    }

    public void showProfileView() {
        if (!ensureAuthenticated()) {
            return;
        }

        setHeader("My Profile", "Review and update your personal information");
        loadCenter("/view/user/user-profile.fxml", controller -> {
            if (controller instanceof UserProfileController profileController) {
                profileController.setCurrentUser(currentUser);
            }
        });
    }

    public void showPasswordResetRequestView() {
        setNavigationVisible(false);
        setHeader("Reset Password", "Request a reset link or token");
        loadCenter("/view/user/password-reset-request.fxml", null);
    }

    public void showPasswordResetView(String tokenOrUrl) {
        setNavigationVisible(false);
        setHeader("Reset Password", "Validate token and choose a new password");
        loadCenter("/view/user/password-reset.fxml", controller -> {
            if (controller instanceof PasswordResetController passwordResetController) {
                passwordResetController.setTokenInput(tokenOrUrl);
            }
        });
    }

    public void showChangePasswordView() {
        if (!ensureAuthenticated()) {
            return;
        }

        setHeader("Change Password", "Update your account password securely");
        loadCenter("/view/user/change-password.fxml", controller -> {
            if (controller instanceof ChangePasswordController changePasswordController) {
                changePasswordController.setCurrentUser(currentUser);
            }
        });
    }

    public void showUserDetailsView(User user) {
        if (!ensureAuthenticated()) {
            return;
        }

        if (user == null || user.getId() == null) {
            showWarning("User unavailable", "Selected user details are not available.");
            return;
        }

        setHeader("User Details", "Read-only user profile and activity summary");
        loadCenter("/view/user/user-details.fxml", controller -> {
            if (controller instanceof UserDetailsController detailsController) {
                detailsController.setOnDataChanged(this::showUsersView);
                detailsController.setUser(user);
            }
        });
    }

    public void logout() {
        showLoginView();
    }

    @FXML
    private void onNavHome() {
        showHomeView();
    }

    @FXML
    private void onNavUsers() {
        showUsersView();
    }

    @FXML
    private void onNavProfile() {
        showProfileView();
    }

    @FXML
    private void onNavChangePassword() {
        showChangePasswordView();
    }

    @FXML
    private void onNavLogout() {
        logout();
    }

    private boolean ensureAuthenticated() {
        if (currentUser != null && currentUser.getId() != null) {
            return true;
        }

        Optional<Integer> sessionUserId = UserSession.getCurrentUserId();
        if (sessionUserId.isPresent()) {
            currentUser = userService.getUserById(sessionUserId.get().longValue()).orElse(null);
        }

        if (currentUser == null) {
            showLoginView();
            return false;
        }

        return true;
    }

    private void loadCenter(String fxmlPath, Consumer<Object> controllerInitializer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent content = loader.load();

            if (controllerInitializer != null) {
                controllerInitializer.accept(loader.getController());
            }

            contentHost.getChildren().setAll(content);
        } catch (IOException exception) {
            showError("View loading failed", "Could not load view: " + fxmlPath + "\n" + exception.getMessage());
        }
    }

    private void setHeader(String title, String subtitle) {
        headerTitleLabel.setText(title == null ? "" : title);
        headerSubtitleLabel.setText(subtitle == null ? "" : subtitle);
    }

    private void setNavigationVisible(boolean visible) {
        sideRail.setManaged(visible);
        sideRail.setVisible(visible);
        currentUserLabel.setManaged(visible);
        currentUserLabel.setVisible(visible);

        if (!visible) {
            currentUserLabel.setText("");
        }
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equals(normalizeRole(user));
    }

    private String roleHomeTitle(User user) {
        return switch (normalizeRole(user)) {
            case "ADMIN" -> "Admin Home";
            case "TEACHER" -> "Teacher Home";
            case "STUDENT" -> "Student Home";
            case "PARTNER" -> "Partner Home";
            default -> "Home";
        };
    }

    private String roleHomeSubtitle(User user) {
        return switch (normalizeRole(user)) {
            case "ADMIN" -> "Monitor users, access controls, and platform operations";
            case "TEACHER" -> "Manage courses, learners, and teaching activities";
            case "STUDENT" -> "Track classes, grades, and learning progress";
            case "PARTNER" -> "Coordinate partner workflows and learning programs";
            default -> "UniLearn desktop workspace";
        };
    }

    private String buildUserBadge(User user) {
        if (user == null) {
            return "";
        }

        String email = user.getEmail() == null ? "unknown" : user.getEmail();
        String role = normalizeRole(user);
        if (role.isBlank()) {
            role = "USER";
        }
        return email + " | " + role;
    }

    private String normalizeRole(User user) {
        if (user == null || user.getRole() == null) {
            return "";
        }

        String role = user.getRole().trim().toUpperCase();
        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }

        return role;
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
