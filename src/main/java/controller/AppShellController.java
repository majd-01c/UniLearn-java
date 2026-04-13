package controller;

import entities.User;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import security.UserSession;
import service.ThemeManager;
import service.UserService;
import util.AppNavigator;
import util.ViewRouter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import javafx.util.Duration;

public class AppShellController implements Initializable {

    @FXML
    private VBox sideRail;

    @FXML
    private Label headerTitleLabel;

    @FXML
    private Label headerSubtitleLabel;

    @FXML
    private Label breadcrumbsLabel;

    @FXML
    private StackPane currentUserAvatarContainer;

    @FXML
    private ImageView currentUserAvatarImageView;

    @FXML
    private Label currentUserInitialsLabel;

    @FXML
    private Label currentUserLabel;

    @FXML
    private Button themeToggleButton;

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
    private ViewRouter viewRouter;

    private User currentUser;
    private String selectedModule = "";
    private List<String> breadcrumbs = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewRouter = new ViewRouter(contentHost);

        contentHost.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                updateThemeToggleLabel(ThemeManager.getInstance().getActiveTheme(newScene));
            }
        });

        AppNavigator.registerShell(this);
        showLoginView();
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            showLoginView();
            return;
        }

        currentUser = userService.getUserById(user.getId().longValue()).orElse(user);
        UserSession.setCurrentUser(currentUser);

        setNavigationVisible(true);
        currentUserLabel.setText(buildUserBadge(currentUser));
        updateUserAvatar(currentUser);
        navUsersButton.setDisable(!isAdmin(currentUser));
    }

    public void navigateTo(String moduleId) {
        String normalized = moduleId == null ? "" : moduleId.trim().toUpperCase();

        switch (normalized) {
            case "HOME", "BACKOFFICE_HOME", "FRONTOFFICE_HOME" -> showHomeView();
            case "USERS", "USER_LIST" -> showUsersView();
            case "PROFILE", "MY_PROFILE" -> showProfileView();
            case "CHANGE_PASSWORD" -> showChangePasswordView();
            case "LOGIN" -> showLoginView();
            default -> showHomeView();
        }
    }

    public void setHeader(String title, List<String> breadcrumbs) {
        String subtitle = breadcrumbs == null || breadcrumbs.isEmpty()
                ? ""
                : String.join(" / ", breadcrumbs);
        setHeader(title, subtitle);
    }

    public void showLoginView() {
        currentUser = null;
        UserSession.clear();

        setNavigationVisible(false);
        setNavigationState("LOGIN", "Login");
        setHeader(
                "UniLearn Desktop",
                "Sign in to continue"
        );

        loadCenter("/view/user/login.fxml", null);
    }

    public void handleLoginSuccess(User user) {
        setCurrentUser(user);

        if (currentUser == null) {
            return;
        }

        showHomeView();
    }

    public void showHomeView() {
        if (!ensureAuthenticated()) {
            return;
        }

        boolean admin = isAdmin(currentUser);

        setHeader(
                roleHomeTitle(currentUser),
                roleHomeSubtitle(currentUser)
        );

        setNavigationState(
                "HOME",
                "Home",
                admin ? "BackOffice Dashboard" : "FrontOffice Dashboard"
        );

        String homeViewPath = admin
                ? "/view/user/backoffice-home.fxml"
                : "/view/user/frontoffice-home.fxml";

        loadCenter(homeViewPath, controller -> {
            if (controller instanceof BackOfficeHomeController backOfficeHomeController) {
                backOfficeHomeController.setUser(currentUser);
            }
            if (controller instanceof FrontOfficeHomeController frontOfficeHomeController) {
                frontOfficeHomeController.setUser(currentUser);
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
        setNavigationState("USERS", "Home", "User Management");
        loadCenter("/view/user/user-list.fxml", null);
    }

    public void showProfileView() {
        if (!ensureAuthenticated()) {
            return;
        }

        setHeader("My Profile", "Review and update your personal information");
        setNavigationState("PROFILE", "Home", "My Profile");
        loadCenter("/view/user/user-profile.fxml", controller -> {
            if (controller instanceof UserProfileController profileController) {
                profileController.setCurrentUser(currentUser);
            }
        });
    }

    public void showPasswordResetRequestView() {
        setNavigationVisible(false);
        setNavigationState("PASSWORD_RESET_REQUEST", "Login", "Forgot Password");
        setHeader("Reset Password", "Request a reset link or token");
        loadCenter("/view/user/password-reset-request.fxml", null);
    }

    public void showPasswordResetView(String tokenOrUrl) {
        setNavigationVisible(false);
        setNavigationState("PASSWORD_RESET", "Login", "Reset Password");
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
        setNavigationState("CHANGE_PASSWORD", "Home", "Change Password");
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
        setNavigationState("USERS", "Home", "User Management", "User Details");
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

    @FXML
    private void onToggleTheme() {
        Scene scene = contentHost == null ? null : contentHost.getScene();
        if (scene == null) {
            return;
        }

        ThemeManager.Theme activeTheme = ThemeManager.getInstance().toggleTheme(scene);
        updateThemeToggleLabel(activeTheme);
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
            ViewRouter.LoadedView loadedView = viewRouter.navigate(fxmlPath, controllerInitializer);
            playViewEnterAnimation(loadedView.root());
        } catch (IOException exception) {
            showError("View loading failed", "Could not load view: " + fxmlPath + "\n" + exception.getMessage());
        }
    }

    private void playViewEnterAnimation(Parent viewRoot) {
        if (viewRoot == null) {
            return;
        }

        viewRoot.setOpacity(0.0);

        FadeTransition fadeTransition = new FadeTransition(Duration.millis(220), viewRoot);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);
        fadeTransition.play();
    }

    private void updateThemeToggleLabel(ThemeManager.Theme theme) {
        if (themeToggleButton == null || theme == null) {
            return;
        }

        themeToggleButton.setText(theme == ThemeManager.Theme.DARK ? "Light Theme" : "Dark Theme");
    }

    private void setHeader(String title, String subtitle) {
        headerTitleLabel.setText(title == null ? "" : title);
        headerSubtitleLabel.setText(subtitle == null ? "" : subtitle);
    }

    private void setNavigationState(String moduleKey, String... breadcrumbParts) {
        selectedModule = moduleKey == null ? "" : moduleKey;
        breadcrumbs = Arrays.stream(breadcrumbParts)
                .filter(value -> value != null && !value.isBlank())
                .toList();

        updateBreadcrumbs();
        updateActiveNavigationButton();
    }

    private void updateBreadcrumbs() {
        if (breadcrumbsLabel == null) {
            return;
        }

        String joined = breadcrumbs.isEmpty() ? "" : String.join(" / ", breadcrumbs);
        breadcrumbsLabel.setText(joined);
    }

    private void updateActiveNavigationButton() {
        clearActiveState(navHomeButton);
        clearActiveState(navUsersButton);
        clearActiveState(navProfileButton);
        clearActiveState(navChangePasswordButton);

        switch (selectedModule) {
            case "HOME" -> markActive(navHomeButton);
            case "USERS" -> markActive(navUsersButton);
            case "PROFILE" -> markActive(navProfileButton);
            case "CHANGE_PASSWORD" -> markActive(navChangePasswordButton);
            default -> {
                // Keep no active item for login/password reset screens.
            }
        }
    }

    private void clearActiveState(Button button) {
        if (button == null) {
            return;
        }
        button.getStyleClass().remove("side-button-active");
    }

    private void markActive(Button button) {
        if (button == null) {
            return;
        }
        if (!button.getStyleClass().contains("side-button-active")) {
            button.getStyleClass().add("side-button-active");
        }
    }

    private void setNavigationVisible(boolean visible) {
        sideRail.setManaged(visible);
        sideRail.setVisible(visible);

        if (currentUserAvatarContainer != null) {
            currentUserAvatarContainer.setManaged(visible);
            currentUserAvatarContainer.setVisible(visible);
        }

        currentUserLabel.setManaged(visible);
        currentUserLabel.setVisible(visible);

        if (!visible) {
            currentUserLabel.setText("");
            clearAvatar();
        }
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equals(normalizeRole(user));
    }

    private String roleHomeTitle(User user) {
        return switch (normalizeRole(user)) {
            case "ADMIN" -> "BackOffice Home";
            case "TEACHER" -> "FrontOffice Home";
            case "STUDENT" -> "FrontOffice Home";
            case "PARTNER" -> "FrontOffice Home";
            case "TRAINER" -> "FrontOffice Home";
            default -> "FrontOffice Home";
        };
    }

    private String roleHomeSubtitle(User user) {
        return switch (normalizeRole(user)) {
            case "ADMIN" -> "Admin dashboard for moderation, user operations, and management modules";
            case "TEACHER" -> "Learning dashboard for courses, schedule, meetings, and communication";
            case "STUDENT" -> "Learning dashboard for classes, assessments, and personal progress";
            case "PARTNER" -> "Front office dashboard for collaboration and opportunities";
            case "TRAINER" -> "Training dashboard for sessions, content, and learner support";
            default -> "UniLearn desktop workspace";
        };
    }

    private String buildUserBadge(User user) {
        if (user == null) {
            return "";
        }

        String displayName = normalizeText(user.getName());
        if (displayName == null) {
            displayName = normalizeText(user.getEmail());
        }

        if (displayName == null) {
            displayName = "Unknown User";
        }

        String role = normalizeRole(user);
        if (role.isBlank()) {
            role = "USER";
        }

        return displayName + " | " + role;
    }

    private void updateUserAvatar(User user) {
        if (currentUserAvatarImageView == null || currentUserInitialsLabel == null) {
            return;
        }

        Image image = tryLoadImage(user == null ? null : user.getProfilePic());
        if (image == null || image.isError()) {
            currentUserAvatarImageView.setImage(null);
            currentUserAvatarImageView.setManaged(false);
            currentUserAvatarImageView.setVisible(false);

            currentUserInitialsLabel.setText(resolveInitials(user));
            currentUserInitialsLabel.setManaged(true);
            currentUserInitialsLabel.setVisible(true);
            return;
        }

        currentUserAvatarImageView.setImage(image);
        currentUserAvatarImageView.setManaged(true);
        currentUserAvatarImageView.setVisible(true);

        currentUserInitialsLabel.setText("");
        currentUserInitialsLabel.setManaged(false);
        currentUserInitialsLabel.setVisible(false);
    }

    private void clearAvatar() {
        if (currentUserAvatarImageView != null) {
            currentUserAvatarImageView.setImage(null);
            currentUserAvatarImageView.setManaged(false);
            currentUserAvatarImageView.setVisible(false);
        }
        if (currentUserInitialsLabel != null) {
            currentUserInitialsLabel.setText("");
            currentUserInitialsLabel.setManaged(false);
            currentUserInitialsLabel.setVisible(false);
        }
    }

    private Image tryLoadImage(String imageSource) {
        String source = normalizeText(imageSource);
        if (source == null) {
            return null;
        }

        try {
            if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("file:")) {
                return new Image(source, true);
            }

            File directFile = new File(source);
            if (directFile.exists()) {
                return new Image(directFile.toURI().toString(), true);
            }

            String classpathSource = source.startsWith("/") ? source : "/" + source;
            URL resource = getClass().getResource(classpathSource);
            if (resource != null) {
                return new Image(resource.toExternalForm(), true);
            }

            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String resolveInitials(User user) {
        String base = normalizeText(user == null ? null : user.getName());
        if (base == null) {
            base = normalizeText(user == null ? null : user.getEmail());
        }

        if (base == null) {
            return "U";
        }

        String[] parts = base.split("\\s+");
        String first = parts.length > 0 ? parts[0] : "";
        String second = parts.length > 1 ? parts[1] : "";

        String initials = "";
        if (!first.isBlank()) {
            initials += first.substring(0, 1).toUpperCase();
        }
        if (!second.isBlank()) {
            initials += second.substring(0, 1).toUpperCase();
        }

        if (initials.isBlank()) {
            initials = base.substring(0, 1).toUpperCase();
        }

        return initials;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
