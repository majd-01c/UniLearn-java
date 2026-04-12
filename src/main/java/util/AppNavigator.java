package util;

import controller.AppShellController;
import entities.*;
import entities.Module;

import java.util.List;

public final class AppNavigator {

    private static AppShellController appShellController;

    private AppNavigator() {}

    public static void registerShell(AppShellController controller) { appShellController = controller; }

    // Auth
    public static void showLogin() { if (appShellController != null) appShellController.showLoginView(); }
    public static void loginSuccess(User user) { if (appShellController != null) appShellController.handleLoginSuccess(user); }
    public static void showHome() { if (appShellController != null) appShellController.showHomeView(); }
    public static void logout() { if (appShellController != null) appShellController.logout(); }

    // User management
    public static void showUsers() { if (appShellController != null) appShellController.showUsersView(); }
    public static void showProfile() { if (appShellController != null) appShellController.showProfileView(); }
    public static void showPasswordResetRequest() { if (appShellController != null) appShellController.showPasswordResetRequestView(); }
    public static void showPasswordReset(String tokenOrUrl) { if (appShellController != null) appShellController.showPasswordResetView(tokenOrUrl); }
    public static void showChangePassword() { if (appShellController != null) appShellController.showChangePasswordView(); }
    public static void showUserDetails(User user) { if (appShellController != null) appShellController.showUserDetailsView(user); }

    // Admin LMS
    public static void showPrograms() { if (appShellController != null) appShellController.showProgramsView(); }
    public static void showProgramForm(Program program) { if (appShellController != null) appShellController.showProgramForm(program); }
    public static void showProgramDetail(Program program) { if (appShellController != null) appShellController.showProgramDetail(program); }
    public static void showModules() { if (appShellController != null) appShellController.showModulesView(); }
    public static void showModuleForm(Module module) { if (appShellController != null) appShellController.showModuleForm(module); }
    public static void showCourses() { if (appShellController != null) appShellController.showCoursesView(); }
    public static void showCourseForm(Course course) { if (appShellController != null) appShellController.showCourseForm(course); }
    public static void showContenu() { if (appShellController != null) appShellController.showContenuView(); }
    public static void showContenuForm(Contenu contenu) { if (appShellController != null) appShellController.showContenuForm(contenu); }
    public static void showClasses() { if (appShellController != null) appShellController.showClassesView(); }
    public static void showClasseForm(Classe classe) { if (appShellController != null) appShellController.showClasseForm(classe); }
    public static void showClasseDetail(Classe classe) { if (appShellController != null) appShellController.showClasseDetail(classe); }

    // Teacher
    public static void showTeacherClasses() { if (appShellController != null) appShellController.showTeacherClasses(); }
    public static void showTeacherWorkspace(dto.lms.TeacherAssignmentRowDto tc) { if (appShellController != null) appShellController.showTeacherWorkspace(tc); }

    // Student
    public static void showStudentLearning() { if (appShellController != null) appShellController.showStudentLearning(); }
    public static void showStudentClasseView(dto.lms.StudentClasseRowDto classe) { if (appShellController != null) appShellController.showStudentClasseView(classe); }
    public static void showStudentCourseView(dto.lms.CourseRowDto cc) { if (appShellController != null) appShellController.showStudentCourseView(cc); }
    public static void showStudentContenuView(dto.lms.ContenuRowDto contenu, List<dto.lms.ContenuRowDto> allVisible, int idx) { if (appShellController != null) appShellController.showStudentContenuView(contenu, allVisible, idx); }

    // Direct access to load custom content
    public static void loadCenter(String fxml, java.util.function.Consumer<Object> init) { if (appShellController != null) appShellController.loadCenter(fxml, init); }
    public static void setHeader(String title, String sub) { if (appShellController != null) appShellController.setHeader(title, sub); }
    public static User getCurrentUser() { return appShellController != null ? appShellController.getCurrentUser() : null; }
}
