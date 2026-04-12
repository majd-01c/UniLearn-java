package controller;

import controller.lms.*;
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
import util.RoleGuard;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class AppShellController implements Initializable {

    @FXML private VBox sideRail;
    @FXML private Label headerTitleLabel;
    @FXML private Label headerSubtitleLabel;
    @FXML private Label currentUserLabel;
    @FXML private Button navHomeButton;
    @FXML private Button navUsersButton;
    @FXML private Button navProfileButton;
    @FXML private Button navChangePasswordButton;
    @FXML private Button themeToggleButton;
    @FXML private StackPane contentHost;

    // Admin LMS nav
    @FXML private Label adminSectionLabel;
    @FXML private Button navProgramsButton;
    @FXML private Button navModulesButton;
    @FXML private Button navCoursesButton;
    @FXML private Button navContenuButton;
    @FXML private Button navClassesButton;

    // Teacher nav
    @FXML private Label teacherSectionLabel;
    @FXML private Button navMyClassesButton;

    // Student nav
    @FXML private Label studentSectionLabel;
    @FXML private Button navMyLearningButton;

    private final UserService userService = new UserService();
    private User currentUser;
    private boolean darkTheme = false;
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppShellController.class);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AppNavigator.registerShell(this);
        darkTheme = PREFS.getBoolean("darkTheme", false);
        applyTheme();
        showLoginView();
    }

    // ==================== Theme ====================
    @FXML
    private void onToggleTheme() {
        darkTheme = !darkTheme;
        PREFS.putBoolean("darkTheme", darkTheme);
        applyTheme();
    }

    private void applyTheme() {
        Parent root = contentHost.getScene() != null ? contentHost.getScene().getRoot() : contentHost.getParent();
        if (root == null) return;
        root.getStyleClass().remove("dark-theme");
        if (darkTheme) {
            root.getStyleClass().add("dark-theme");
            themeToggleButton.setText("☀ Light");
        } else {
            themeToggleButton.setText("◐ Dark");
        }
    }

    // ==================== Auth ====================
    public void showLoginView() {
        currentUser = null;
        UserSession.clear();
        setNavigationVisible(false);
        setHeader("UniLearn Desktop", "Sign in to continue");
        loadCenter("/view/user/login.fxml", null);
    }

    public void handleLoginSuccess(User user) {
        if (user == null || user.getId() == null) { showLoginView(); return; }
        currentUser = userService.getUserById(user.getId().longValue()).orElse(user);
        UserSession.setCurrentUser(currentUser);
        setNavigationVisible(true);
        configureNavForRole();
        currentUserLabel.setText(buildUserBadge(currentUser));
        applyTheme();
        showHomeView();
    }

    // ==================== Role-Based Nav ====================
    private void configureNavForRole() {
        boolean admin = RoleGuard.isAdmin(currentUser);
        boolean teacher = RoleGuard.isTeacher(currentUser);
        boolean student = RoleGuard.isStudent(currentUser);

        // Admin section
        setNodeVisible(adminSectionLabel, admin);
        setNodeVisible(navProgramsButton, admin);
        setNodeVisible(navModulesButton, admin);
        setNodeVisible(navCoursesButton, admin);
        setNodeVisible(navContenuButton, admin);
        setNodeVisible(navClassesButton, admin);
        setNodeVisible(navUsersButton, admin);

        // Teacher section
        setNodeVisible(teacherSectionLabel, teacher);
        setNodeVisible(navMyClassesButton, teacher);

        // Student section
        setNodeVisible(studentSectionLabel, student);
        setNodeVisible(navMyLearningButton, student);
    }

    private void setNodeVisible(javafx.scene.Node node, boolean visible) {
        if (node != null) { node.setVisible(visible); node.setManaged(visible); }
    }

    // ==================== Navigation Methods ====================
    public void showHomeView() {
        if (!ensureAuthenticated()) return;
        setHeader(roleHomeTitle(currentUser), roleHomeSubtitle(currentUser));
        loadCenter("/view/user/home.fxml", controller -> {
            if (controller instanceof HomeController hc) hc.setUser(currentUser);
        });
    }

    public void showUsersView() {
        if (!ensureAuthenticated()) return;
        if (!RoleGuard.isAdmin(currentUser)) { showWarning("Access denied", "Only administrators can access User Management."); showHomeView(); return; }
        setHeader("User Management", "Manage user accounts, roles, and statuses");
        loadCenter("/view/user/user-list.fxml", null);
    }

    public void showProfileView() {
        if (!ensureAuthenticated()) return;
        setHeader("My Profile", "Review and update your personal information");
        loadCenter("/view/user/user-profile.fxml", controller -> {
            if (controller instanceof UserProfileController pc) pc.setCurrentUser(currentUser);
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
            if (controller instanceof PasswordResetController prc) prc.setTokenInput(tokenOrUrl);
        });
    }

    public void showChangePasswordView() {
        if (!ensureAuthenticated()) return;
        setHeader("Change Password", "Update your account password securely");
        loadCenter("/view/user/change-password.fxml", controller -> {
            if (controller instanceof ChangePasswordController cpc) cpc.setCurrentUser(currentUser);
        });
    }

    public void showUserDetailsView(User user) {
        if (!ensureAuthenticated()) return;
        if (user == null || user.getId() == null) { showWarning("User unavailable", "Selected user details are not available."); return; }
        setHeader("User Details", "Read-only user profile and activity summary");
        loadCenter("/view/user/user-details.fxml", controller -> {
            if (controller instanceof UserDetailsController dc) { dc.setOnDataChanged(this::showUsersView); dc.setUser(user); }
        });
    }

    // ==================== LMS Admin Navigation ====================
    public void showProgramsView() {
        if (!ensureAuthenticated()) return;
        RoleGuard.requireAdmin(currentUser);
        setHeader("Program Management", "Create and manage academic programs");
        loadCenter("/view/lms/admin/program-list.fxml", null);
    }

    public void showProgramForm(entities.Program program) {
        if (!ensureAuthenticated()) return;
        setHeader(program == null ? "New Program" : "Edit Program", "Program form");
        loadCenter("/view/lms/admin/program-form.fxml", controller -> {
            if (controller instanceof AdminProgramFormController fc) fc.setProgram(program);
        });
    }

    public void showProgramDetail(entities.Program program) {
        if (!ensureAuthenticated()) return;
        setHeader("Program: " + program.getName(), "Manage modules and settings");
        loadCenter("/view/lms/admin/program-detail.fxml", controller -> {
            if (controller instanceof AdminProgramDetailController dc) dc.setProgram(program);
        });
    }

    public void showModulesView() {
        if (!ensureAuthenticated()) return;
        RoleGuard.requireAdmin(currentUser);
        setHeader("Module Management", "Create and manage modules");
        loadCenter("/view/lms/admin/module-list.fxml", null);
    }

    public void showModuleForm(entities.Module module) {
        if (!ensureAuthenticated()) return;
        setHeader(module == null ? "New Module" : "Edit Module", "Module form");
        loadCenter("/view/lms/admin/module-form.fxml", controller -> {
            if (controller instanceof AdminModuleFormController fc) fc.setModule(module);
        });
    }

    public void showCoursesView() {
        if (!ensureAuthenticated()) return;
        RoleGuard.requireAdmin(currentUser);
        setHeader("Course Management", "Create and manage courses");
        loadCenter("/view/lms/admin/course-list.fxml", null);
    }

    public void showCourseForm(entities.Course course) {
        if (!ensureAuthenticated()) return;
        setHeader(course == null ? "New Course" : "Edit Course", "Course form");
        loadCenter("/view/lms/admin/course-form.fxml", controller -> {
            if (controller instanceof AdminCourseFormController fc) fc.setCourse(course);
        });
    }

    public void showContenuView() {
        if (!ensureAuthenticated()) return;
        RoleGuard.requireAdmin(currentUser);
        setHeader("Content Management", "Create and manage learning content");
        loadCenter("/view/lms/admin/contenu-list.fxml", null);
    }

    public void showContenuForm(entities.Contenu contenu) {
        if (!ensureAuthenticated()) return;
        setHeader(contenu == null ? "New Content" : "Edit Content", "Content form");
        loadCenter("/view/lms/admin/contenu-form.fxml", controller -> {
            if (controller instanceof AdminContenuFormController fc) fc.setContenu(contenu);
        });
    }

    public void showClassesView() {
        if (!ensureAuthenticated()) return;
        RoleGuard.requireAdmin(currentUser);
        setHeader("Class Management", "Create and manage classes");
        loadCenter("/view/lms/admin/classe-list.fxml", null);
    }

    public void showClasseForm(entities.Classe classe) {
        if (!ensureAuthenticated()) return;
        setHeader(classe == null ? "New Class" : "Edit Class", "Class form");
        loadCenter("/view/lms/admin/classe-form.fxml", controller -> {
            if (controller instanceof AdminClasseFormController fc) fc.setClasse(classe);
        });
    }

    public void showClasseDetail(entities.Classe classe) {
        if (!ensureAuthenticated()) return;
        setHeader("Class: " + classe.getName(), "Students, teachers, and program assignment");
        loadCenter("/view/lms/admin/classe-detail.fxml", controller -> {
            if (controller instanceof AdminClasseDetailController dc) dc.setClasse(classe);
        });
    }

    // ==================== Teacher Navigation ====================
    public void showTeacherClasses() {
        if (!ensureAuthenticated()) return;
        setHeader("My Classes", "Manage your assigned classes");
        loadCenter("/view/lms/teacher/teacher-classe-list.fxml", controller -> {
            if (controller instanceof TeacherClasseListController tc) tc.setTeacher(currentUser);
        });
    }

    public void showTeacherWorkspace(dto.lms.TeacherAssignmentRowDto tc) {
        if (!ensureAuthenticated()) return;
        setHeader("Class Workspace", "Manage your module, courses, and content");
        loadCenter("/view/lms/teacher/teacher-classe-workspace.fxml", controller -> {
            if (controller instanceof TeacherClasseWorkspaceController wc) wc.setTeacherClasse(tc);
        });
    }

    // ==================== Student Navigation ====================
    public void showStudentLearning() {
        if (!ensureAuthenticated()) return;
        setHeader("My Learning", "Your enrolled classes and learning progress");
        loadCenter("/view/lms/student/student-learning.fxml", controller -> {
            if (controller instanceof StudentLearningController sc) sc.setStudent(currentUser);
        });
    }

    public void showStudentClasseView(dto.lms.StudentClasseRowDto classe) {
        if (!ensureAuthenticated()) return;
        setHeader("Class: " + classe.getClasseName(), "Explore modules and courses");
        loadCenter("/view/lms/student/student-classe-view.fxml", controller -> {
            if (controller instanceof StudentClasseViewController sc) sc.init(classe, currentUser);
        });
    }

    public void showStudentCourseView(dto.lms.CourseRowDto classeCourse) {
        if (!ensureAuthenticated()) return;
        setHeader("Course Content", "View lesson content");
        loadCenter("/view/lms/student/student-course-view.fxml", controller -> {
            if (controller instanceof StudentCourseViewController sc) sc.setClasseCourse(classeCourse);
        });
    }

    public void showStudentContenuView(dto.lms.ContenuRowDto contenu, java.util.List<dto.lms.ContenuRowDto> allVisible, int currentIndex) {
        if (!ensureAuthenticated()) return;
        setHeader(contenu.getTitle(), contenu.getType() + " content");
        loadCenter("/view/lms/student/student-contenu-view.fxml", controller -> {
            if (controller instanceof StudentContenuViewController sc) sc.init(contenu, allVisible, currentIndex);
        });
    }

    public void logout() { showLoginView(); }

    // ==================== FXML Handlers ====================
    @FXML private void onNavHome() { showHomeView(); }
    @FXML private void onNavUsers() { showUsersView(); }
    @FXML private void onNavProfile() { showProfileView(); }
    @FXML private void onNavChangePassword() { showChangePasswordView(); }
    @FXML private void onNavLogout() { logout(); }
    @FXML private void onNavPrograms() { showProgramsView(); }
    @FXML private void onNavModules() { showModulesView(); }
    @FXML private void onNavCourses() { showCoursesView(); }
    @FXML private void onNavContenu() { showContenuView(); }
    @FXML private void onNavClasses() { showClassesView(); }
    @FXML private void onNavMyClasses() { showTeacherClasses(); }
    @FXML private void onNavMyLearning() { showStudentLearning(); }

    // ==================== Internals ====================
    private boolean ensureAuthenticated() {
        if (currentUser != null && currentUser.getId() != null) return true;
        Optional<Integer> sessionUserId = UserSession.getCurrentUserId();
        if (sessionUserId.isPresent()) currentUser = userService.getUserById(sessionUserId.get().longValue()).orElse(null);
        if (currentUser == null) { showLoginView(); return false; }
        return true;
    }

    public void loadCenter(String fxmlPath, Consumer<Object> controllerInitializer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent content = loader.load();
            if (controllerInitializer != null) controllerInitializer.accept(loader.getController());
            contentHost.getChildren().setAll(content);
        } catch (IOException exception) {
            showError("View loading failed", "Could not load view: " + fxmlPath + "\n" + exception.getMessage());
        }
    }

    public void setHeader(String title, String subtitle) {
        headerTitleLabel.setText(title == null ? "" : title);
        headerSubtitleLabel.setText(subtitle == null ? "" : subtitle);
    }

    private void setNavigationVisible(boolean visible) {
        sideRail.setManaged(visible); sideRail.setVisible(visible);
        currentUserLabel.setManaged(visible); currentUserLabel.setVisible(visible);
        themeToggleButton.setManaged(visible); themeToggleButton.setVisible(visible);
        if (!visible) currentUserLabel.setText("");
    }

    private String roleHomeTitle(User user) {
        return switch (RoleGuard.normalize(user)) {
            case "ADMIN" -> "Admin Dashboard";
            case "TEACHER" -> "Teacher Dashboard";
            case "STUDENT" -> "Student Dashboard";
            case "PARTNER" -> "Partner Dashboard";
            default -> "Dashboard";
        };
    }

    private String roleHomeSubtitle(User user) {
        return switch (RoleGuard.normalize(user)) {
            case "ADMIN" -> "Monitor programs, classes, users, and platform operations";
            case "TEACHER" -> "Manage your classes, courses, and teaching activities";
            case "STUDENT" -> "Track your enrolled classes and learning progress";
            case "PARTNER" -> "Coordinate partner workflows and learning programs";
            default -> "UniLearn desktop workspace";
        };
    }

    private String buildUserBadge(User user) {
        if (user == null) return "";
        String email = user.getEmail() == null ? "unknown" : user.getEmail();
        String role = RoleGuard.normalize(user);
        if (role.isBlank()) role = "USER";
        return email + " │ " + role;
    }

    public User getCurrentUser() { return currentUser; }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }
}
