package controller;

import controller.lms.*;
import controller.forum.*;
import controller.job_offer.*;
import entities.ClassMeeting;
import entities.User;
import entities.forum.ForumCategory;
import entities.forum.ForumComment;
import entities.forum.ForumTopic;
import entities.job_offer.JobOffer;
import entities.job_offer.JobApplication;
import entities.job_offer.JobOfferMeeting;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import security.UserSession;
import service.ThemeManager;
import service.UserService;
import util.AppNavigator;
import util.RoleGuard;
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
    private ScrollPane sideRailScroll;
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
    private Button themeToggleButton;
    @FXML
    private Button navForumButton;
    @FXML
    private Button navEvaluationButton;
    @FXML
    private Button navEvaluationGradesButton;
    @FXML
    private Button navEvaluationRecommendationsButton;
    @FXML
    private Button navEvaluationScheduleButton;
    @FXML
    private Button navEvaluationComplaintsButton;
    @FXML
    private Button navEvaluationDocumentsButton;
    @FXML
    private Button navJobOffersButton;
    @FXML
    private Button navIARoomsButton;
    @FXML
    private StackPane contentHost;

    // Admin LMS nav
    @FXML
    private Label adminSectionLabel;
    @FXML
    private Button navProgramsButton;
    @FXML
    private Button navModulesButton;
    @FXML
    private Button navCoursesButton;
    @FXML
    private Button navContenuButton;
    @FXML
    private Button navClassesButton;

    // Teacher nav
    @FXML
    private Label teacherSectionLabel;
    @FXML
    private Button navMyClassesButton;

    // Student nav
    @FXML
    private Label studentSectionLabel;
    @FXML
    private Button navMyLearningButton;
    @FXML
    private Button navQuizzesButton;

    @FXML
    private Label breadcrumbsLabel;

    @FXML
    private StackPane currentUserAvatarContainer;

    @FXML
    private ImageView currentUserAvatarImageView;

    @FXML
    private Label currentUserInitialsLabel;

    private final UserService userService = new UserService();
    private ViewRouter viewRouter;

    private User currentUser;
    private String selectedModule = "";
    private List<String> breadcrumbs = new ArrayList<>();
    private boolean evaluationSubNavExpanded;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewRouter = new ViewRouter(contentHost);

        contentHost.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                ThemeManager.getInstance().applySavedTheme(newScene);
                updateThemeToggleLabel(ThemeManager.getInstance().getActiveTheme(newScene));
            }
        });

        AppNavigator.registerShell(this);
        showLoginView();
    }

    // ==================== Auth ====================
    private void setCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            this.currentUser = null;
            return;
        }
        this.currentUser = userService.getUserById(user.getId().longValue()).orElse(user);
        UserSession.setCurrentUser(this.currentUser);

        setNavigationVisible(true);
        configureNavForRole();
        currentUserLabel.setText(buildUserBadge(this.currentUser));
        updateThemeToggleLabel(ThemeManager.getInstance().getActiveTheme(contentHost.getScene()));
        updateUserAvatar(this.currentUser);
        navUsersButton.setDisable(!isAdmin(this.currentUser));
    }

    public void navigateTo(String moduleId) {
        String normalized = moduleId == null ? "" : moduleId.trim().toUpperCase();

        switch (normalized) {
            case "HOME", "BACKOFFICE_HOME", "FRONTOFFICE_HOME" -> showHomeView();
            case "USERS", "USER_LIST" -> showUsersView();
            case "PROFILE", "MY_PROFILE" -> showProfileView();
            case "CHANGE_PASSWORD" -> showChangePasswordView();
            case "EVALUATION" -> onNavEvaluation();
            case "EVALUATION_GRADES" -> onNavEvaluationGrades();
            case "EVALUATION_RECOMMENDATIONS" -> onNavEvaluationRecommendations();
            case "EVALUATION_SCHEDULE" -> onNavEvaluationSchedule();
            case "EVALUATION_COMPLAINTS" -> onNavEvaluationComplaints();
            case "EVALUATION_DOCUMENTS" -> onNavEvaluationDocuments();
            case "IAROOMS" -> showIARoomsView();
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
                "Sign in to continue");

        loadCenter("/view/user/login.fxml", null);
    }

    public void handleLoginSuccess(User user) {
        setCurrentUser(user);

        if (currentUser == null) {
            return;
        }

        if (currentUser.getMustChangePassword() == (byte) 1) {
            showChangePasswordView();
            return;
        }

        showHomeView();
    }

    public void showSmsVerificationView(User user) {
        if (user == null) {
            showLoginView();
            return;
        }

        setCurrentUser(user);
        setNavigationVisible(false);
        setNavigationState("SMS_VERIFICATION", "Login", "Verify Phone");
        setHeader("Verify Your Phone Number", "Complete SMS verification to continue");
        loadCenter("/view/user/sms-verification.fxml", controller -> {
            // Controller will be SmsVerificationController and will auto-initialize from FXML
        });
    }

    // ==================== Role-Based Nav ====================
    private void configureNavForRole() {
        boolean admin = RoleGuard.isAdmin(currentUser);
        boolean teacher = RoleGuard.isTeacher(currentUser);
        boolean student = RoleGuard.isStudent(currentUser);

        // Admin section: Programs, Classes, Users
        setNodeVisible(adminSectionLabel, admin);
        setNodeVisible(navProgramsButton, admin);
        setNodeVisible(navClassesButton, admin);
        setNodeVisible(navUsersButton, admin);

        // Teacher section: My Classes only (Modules/Courses/Content managed in
        // workspace)
        setNodeVisible(teacherSectionLabel, teacher);
        setNodeVisible(navModulesButton, false);
        setNodeVisible(navCoursesButton, false);
        setNodeVisible(navContenuButton, false);
        setNodeVisible(navMyClassesButton, teacher);

        // Student section
        setNodeVisible(studentSectionLabel, student);
        setNodeVisible(navMyLearningButton, student);
        setNodeVisible(navQuizzesButton, student);

        // Evaluation module for authenticated academic roles.
        setNodeVisible(navEvaluationButton, currentUser != null && currentUser.getId() != null);
        setNodeVisible(navIARoomsButton, currentUser != null && currentUser.getId() != null);
        updateEvaluationSubNavVisibility();
    }

    private void setNodeVisible(javafx.scene.Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    // ==================== Navigation Methods ====================
    public void showHomeView() {
        if (!ensureAuthenticated()) {
            return;
        }

        boolean admin = isAdmin(currentUser);

        if (admin) {
            setHeader(
                    roleHomeTitle(currentUser),
                    roleHomeSubtitle(currentUser));
            setNavigationState("HOME", "Home", "BackOffice Dashboard");
        } else {
            setHeader("", "");
            setNavigationState("HOME");
        }

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
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isAdmin(currentUser)) {
            showWarning("Access denied", "Only administrators can access User Management.");
            showHomeView();
            return;
        }
        setHeader("User Management", "Manage user accounts, roles, and statuses");
        setNavigationState("USERS", "Home", "User Management");
        loadCenter("/view/user/user-list.fxml", null);
    }

    public void showProfileView() {
        if (!ensureAuthenticated())
            return;
        setHeader("My Profile", "Review and update your personal information");
        setNavigationState("PROFILE", "Home", "My Profile");
        loadCenter("/view/user/user-profile.fxml", controller -> {
            if (controller instanceof UserProfileController pc)
                pc.setCurrentUser(currentUser);
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
            if (controller instanceof PasswordResetController prc)
                prc.setTokenInput(tokenOrUrl);
        });
    }

    public void showChangePasswordView() {
        if (!ensureAuthenticated())
            return;
        setHeader("Change Password", "Update your account password securely");
        setNavigationState("CHANGE_PASSWORD", "Home", "Change Password");
        loadCenter("/view/user/change-password.fxml", controller -> {
            if (controller instanceof ChangePasswordController cpc)
                cpc.setCurrentUser(currentUser);
        });
    }

    public void showUserDetailsView(User user) {
        if (!ensureAuthenticated())
            return;
        if (user == null || user.getId() == null) {
            showWarning("User unavailable", "Selected user details are not available.");
            return;
        }
        setHeader("User Details", "Read-only user profile and activity summary");
        setNavigationState("USERS", "Home", "User Management", "User Details");
        loadCenter("/view/user/user-details.fxml", controller -> {
            if (controller instanceof UserDetailsController dc) {
                dc.setOnDataChanged(this::showUsersView);
                dc.setUser(user);
            }
        });
    }

    // ==================== LMS Admin Navigation ====================
    public void showProgramsView() {
        if (!ensureAuthenticated())
            return;
        RoleGuard.requireAdmin(currentUser);
        setHeader("Program Management", "Create and manage academic programs");
        loadCenter("/view/lms/admin/program-list.fxml", null);
    }

    public void showProgramForm(entities.Program program) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isAdmin(currentUser)) {
            showWarning("Access Denied", "You do not have permission to access this page.");
            showHomeView();
            return;
        }
        setHeader(program == null ? "New Program" : "Edit Program", "Program form");
        loadCenter("/view/lms/admin/program-form.fxml", controller -> {
            if (controller instanceof AdminProgramFormController fc)
                fc.setProgram(program);
        });
    }

    public void showProgramDetail(entities.Program program) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isAdmin(currentUser)) {
            showWarning("Access Denied", "You do not have permission to access this page.");
            showHomeView();
            return;
        }
        setHeader("Program: " + program.getName(), "Manage modules and settings");
        loadCenter("/view/lms/admin/program-detail.fxml", controller -> {
            if (controller instanceof AdminProgramDetailController dc)
                dc.setProgram(program);
        });
    }

    public void showModulesView() {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can manage modules.");
            showHomeView();
            return;
        }
        setHeader("Module Management", "Create and manage modules");
        loadCenter("/view/lms/admin/module-list.fxml", null);
    }

    public void showModuleForm(entities.Module module) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can manage modules.");
            showHomeView();
            return;
        }
        setHeader(module == null ? "New Module" : "Edit Module", "Module form");
        loadCenter("/view/lms/admin/module-form.fxml", controller -> {
            if (controller instanceof AdminModuleFormController fc)
                fc.setModule(module);
        });
    }

    public void showCoursesView() {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can manage courses.");
            showHomeView();
            return;
        }
        setHeader("Course Management", "Create and manage courses");
        loadCenter("/view/lms/admin/course-list.fxml", null);
    }

    public void showCourseForm(entities.Course course) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can manage courses.");
            showHomeView();
            return;
        }
        setHeader(course == null ? "New Course" : "Edit Course", "Course form");
        loadCenter("/view/lms/admin/course-form.fxml", controller -> {
            if (controller instanceof AdminCourseFormController fc)
                fc.setCourse(course);
        });
    }

    public void showContenuView() {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can manage content.");
            showHomeView();
            return;
        }
        setHeader("Content Management", "Create and manage learning content");
        loadCenter("/view/lms/admin/contenu-list.fxml", null);
    }

    public void showContenuForm(entities.Contenu contenu) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can manage content.");
            showHomeView();
            return;
        }
        setHeader(contenu == null ? "New Content" : "Edit Content", "Content form");
        loadCenter("/view/lms/admin/contenu-form.fxml", controller -> {
            if (controller instanceof AdminContenuFormController fc)
                fc.setContenu(contenu);
        });
    }

    public void showClassesView() {
        if (!ensureAuthenticated())
            return;
        RoleGuard.requireAdmin(currentUser);
        setHeader("Class Management", "Create and manage classes");
        loadCenter("/view/lms/admin/classe-list.fxml", null);
    }

    public void showClasseForm(entities.Classe classe) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isAdmin(currentUser)) {
            showWarning("Access Denied", "You do not have permission to access this page.");
            showHomeView();
            return;
        }
        setHeader(classe == null ? "New Class" : "Edit Class", "Class form");
        loadCenter("/view/lms/admin/classe-form.fxml", controller -> {
            if (controller instanceof AdminClasseFormController fc)
                fc.setClasse(classe);
        });
    }

    public void showClasseDetail(entities.Classe classe) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isAdmin(currentUser)) {
            showWarning("Access Denied", "You do not have permission to access this page.");
            showHomeView();
            return;
        }
        setHeader("Class: " + classe.getName(), "Students, teachers, and program assignment");
        loadCenter("/view/lms/admin/classe-detail.fxml", controller -> {
            if (controller instanceof AdminClasseDetailController dc)
                dc.setClasse(classe);
        });
    }

    // ==================== Teacher Navigation ====================
    public void showTeacherClasses() {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can access this page.");
            showHomeView();
            return;
        }
        setHeader("My Classes", "Manage your assigned classes");
        loadCenter("/view/lms/teacher/teacher-classe-list.fxml", controller -> {
            if (controller instanceof TeacherClasseListController tc)
                tc.setTeacher(currentUser);
        });
    }

    public void showTeacherWorkspace(dto.lms.TeacherAssignmentRowDto tc) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can access this page.");
            showHomeView();
            return;
        }
        setHeader("Class Workspace", "Manage your module, courses, and content");
        loadCenter("/view/lms/teacher/teacher-classe-workspace.fxml", controller -> {
            if (controller instanceof TeacherClasseWorkspaceController wc)
                wc.setTeacherClasse(tc);
        });
    }

    public void showTeacherMeetings(dto.lms.TeacherAssignmentRowDto tc) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can access this page.");
            showHomeView();
            return;
        }
        setHeader("Class Meetings", tc != null ? tc.getClasseName() : "Meetings");
        loadCenter("/view/lms/teacher/teacher-meetings.fxml", controller -> {
            if (controller instanceof MeetingController mc)
                mc.setTeacherClasse(tc, currentUser);
        });
    }

    // ==================== Student Navigation ====================
    public void showStudentLearning() {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isStudent(currentUser)) {
            showWarning("Access Denied", "Only students can access this page.");
            showHomeView();
            return;
        }
        setHeader("My Learning", "Your enrolled classes and learning progress");
        loadCenter("/view/lms/student/student-learning.fxml", controller -> {
            if (controller instanceof StudentLearningController sc)
                sc.setStudent(currentUser);
        });
    }

    public void showStudentClasseView(dto.lms.StudentClasseRowDto classe) {
        if (!ensureAuthenticated())
            return;
        setHeader("Class: " + classe.getClasseName(), "Explore modules and courses");
        loadCenter("/view/lms/student/student-classe-view.fxml", controller -> {
            if (controller instanceof StudentClasseViewController sc)
                sc.init(classe, currentUser);
        });
    }

    public void showStudentMeetings(dto.lms.StudentClasseRowDto classe) {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isStudent(currentUser)) {
            showWarning("Access Denied", "Only students can access this page.");
            showHomeView();
            return;
        }
        setHeader("Class Meetings", classe != null ? classe.getClasseName() : "Meetings");
        loadCenter("/view/lms/student/student-meetings.fxml", controller -> {
            if (controller instanceof MeetingController mc)
                mc.setStudentClasse(classe, currentUser);
        });
    }

    public void showStudentCourseView(dto.lms.CourseRowDto classeCourse) {
        if (!ensureAuthenticated())
            return;
        setHeader("Course Content", "View lesson content");
        loadCenter("/view/lms/student/student-course-view.fxml", controller -> {
            if (controller instanceof StudentCourseViewController sc)
                sc.setClasseCourse(classeCourse);
        });
    }

    public void showStudentContenuView(dto.lms.ContenuRowDto contenu, java.util.List<dto.lms.ContenuRowDto> allVisible,
            int currentIndex) {
        if (!ensureAuthenticated())
            return;
        setHeader(contenu.getTitle(), contenu.getType() + " content");
        loadCenter("/view/lms/student/student-contenu-view.fxml", controller -> {
            if (controller instanceof StudentContenuViewController sc)
                sc.init(contenu, allVisible, currentIndex);
        });
    }

    public void showStudentQuizzesView(User student) {
        if (!ensureAuthenticated())
            return;
        setHeader("Quizzes", "Take and review your quizzes");
        loadCenter("/view/lms/student/student-quiz.fxml", controller -> {
            if (controller instanceof StudentQuizController sc)
                sc.setStudent(student);
        });
    }

    public void showMeetingRoom(ClassMeeting meeting, dto.lms.TeacherAssignmentRowDto teacherContext,
                                dto.lms.StudentClasseRowDto studentContext, boolean isTeacher) {
        if (!ensureAuthenticated())
            return;
        if (isTeacher && !RoleGuard.isTeacher(currentUser)) {
            showWarning("Access Denied", "Only teachers can access this meeting as host.");
            showHomeView();
            return;
        }
        if (!isTeacher && !RoleGuard.isStudent(currentUser)) {
            showWarning("Access Denied", "Only students can access this meeting as attendee.");
            showHomeView();
            return;
        }
        setHeader(meeting != null ? meeting.getTitle() : "Meeting", "Video meeting");
        loadCenter("/view/lms/meeting-room.fxml", controller -> {
            if (controller instanceof MeetingController mc)
                mc.setRoom(meeting, teacherContext, studentContext, currentUser, isTeacher);
        });
    }

    public void showJobOfferMeetingRoom(JobOfferMeeting meeting, boolean isPartner) {
        if (!ensureAuthenticated())
            return;
        if (isPartner && RoleGuard.isStudent(currentUser)) {
            showWarning("Access Denied", "Only partners can access this meeting as host.");
            showJobOffersView();
            return;
        }
        if (!isPartner && !RoleGuard.isStudent(currentUser)) {
            showWarning("Access Denied", "Only students can access this meeting as attendee.");
            showJobOffersView();
            return;
        }
        setHeader(meeting != null ? meeting.getTitle() : "Interview Meeting", "Job offer video meeting");
        loadCenter("/view/lms/meeting-room.fxml", controller -> {
            if (controller instanceof MeetingController mc)
                mc.setJobOfferRoom(meeting, currentUser, isPartner);
        });
    }

    public void logout() {
        showLoginView();
    }

    // ========================
    // IAROOMS NAVIGATION
    // ========================

    public void showIARoomsView() {
        if (!ensureAuthenticated()) {
            return;
        }
        setHeader("IArooms", "Shared Symfony and Java room availability workspace");
        setNavigationState("IAROOMS", "Home", "IArooms");
        loadCenter("/view/iarooms/iarooms-dashboard.fxml", null);
    }

    // ========================
    // FORUM NAVIGATION
    // ========================

    public void showForumView() {
        if (!ensureAuthenticated())
            return;
        setHeader("Community Forum", "Ask questions, share knowledge, and connect with others");
        setNavigationState("FORUM", "Home", "Forum");
        loadCenter("/view/forum/forum-home.fxml", null);
    }

    public void showForumCategoryView(ForumCategory category) {
        if (!ensureAuthenticated())
            return;
        setHeader("Forum · " + (category.getName() != null ? category.getName() : "Category"),
                "Browse topics in this category");
        loadCenter("/view/forum/forum-category.fxml", controller -> {
            if (controller instanceof ForumCategoryController c) {
                c.setCategory(category);
            }
        });
    }

    public void showForumTopicView(ForumTopic topic) {
        if (!ensureAuthenticated())
            return;
        setHeader("Forum · Topic", topic.getTitle() != null ? topic.getTitle() : "Topic");
        loadCenter("/view/forum/forum-topic.fxml", controller -> {
            if (controller instanceof ForumTopicController c) {
                c.setTopic(topic);
            }
        });
    }

    public void showForumNewTopicView() {
        if (!ensureAuthenticated())
            return;
        setHeader("Forum · New Topic", "Create a new discussion");
        loadCenter("/view/forum/forum-new-topic.fxml", null);
    }

    public void showForumEditTopicView(ForumTopic topic) {
        if (!ensureAuthenticated())
            return;
        setHeader("Forum · Edit Topic", "Modify your topic");
        loadCenter("/view/forum/forum-edit-topic.fxml", controller -> {
            if (controller instanceof ForumEditTopicController c) {
                c.setTopic(topic);
            }
        });
    }

    public void showForumEditCommentView(ForumComment comment) {
        if (!ensureAuthenticated())
            return;
        setHeader("Forum · Edit Comment", "Modify your comment");
        loadCenter("/view/forum/forum-edit-comment.fxml", controller -> {
            if (controller instanceof ForumEditCommentController c) {
                c.setComment(comment);
            }
        });
    }

    public void showForumAdminCategoriesView() {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isAdmin(currentUser)) {
            showWarning("Access denied", "Only administrators can manage categories.");
            return;
        }
        setHeader("Forum · Manage Categories", "Add, edit, and delete forum categories");
        loadCenter("/view/forum/forum-admin-categories.fxml", null);
    }

    // ========================
    // JOB OFFER NAVIGATION
    // ========================

    public void showJobOffersView() {
        if (!ensureAuthenticated())
            return;
        if (RoleGuard.isAdmin(currentUser)) {
            setHeader("Job Offers Management", "Approve, reject, edit, close, and delete offers");
        } else {
            setHeader("Job Opportunities", "Browse and apply to job offers");
        }
        setNavigationState("JOB_OFFERS", "Home", "Job Offers");
        loadCenter("/view/job_offer/job-offers-list.fxml", controller -> {
            if (controller instanceof JobOfferListController c) {
                c.setCurrentUser(currentUser);
            }
        });
    }

    public void showJobOfferDetailView(JobOffer jobOffer) {
        if (!ensureAuthenticated())
            return;
        if (jobOffer == null || jobOffer.getId() <= 0) {
            showWarning("Job offer unavailable", "Selected job offer is not available.");
            return;
        }
        setHeader("Job Offer Detail", jobOffer.getTitle() != null ? jobOffer.getTitle() : "Opportunity");
        loadCenter("/view/job_offer/job-offer-detail.fxml", controller -> {
            if (controller instanceof JobOfferDetailController c) {
                c.setJobOffer(jobOffer);
                c.setCurrentUser(currentUser);
            }
        });
    }

    public void showJobOfferFormView(JobOffer jobOffer) {
        if (!ensureAuthenticated())
            return;
        // Only partners (non-admin users managing offers) can create/edit
        if (jobOffer != null && !jobOffer.getUser().getId().equals(currentUser.getId()) && !RoleGuard.isAdmin(currentUser)) {
            showWarning("Access Denied", "You can only manage your own job offers.");
            showJobOffersView();
            return;
        }
        setHeader(jobOffer == null ? "Create New Job Offer" : "Edit Job Offer", "Employer form");
        loadCenter("/view/job_offer/job-offer-form.fxml", controller -> {
            if (controller instanceof JobOfferFormController c) {
                c.setJobOffer(jobOffer);
                c.setCurrentUser(currentUser);
            }
        });
    }

    public void showMyJobApplicationsView() {
        if (!ensureAuthenticated())
            return;
        if (!RoleGuard.isStudent(currentUser)) {
            showWarning("Access Denied", "Only students can view applications.");
            showJobOffersView();
            return;
        }
        setHeader("My Job Applications", "Track and manage your applications");
        loadCenter("/view/job_offer/my-applications.fxml", controller -> {
            if (controller instanceof MyApplicationsController c) {
                c.setCurrentUser(currentUser);
            }
        });
    }

    public void showPartnerApplicationsView() {
        showPartnerApplicationsView(null, null, 1);
    }

    public void showPartnerApplicationsView(Integer offerId, Integer applicationId, int step) {
        if (!ensureAuthenticated())
            return;
        if (RoleGuard.isStudent(currentUser)) {
            showWarning("Access Denied", "Only partners and administrators can review applications.");
            showJobOffersView();
            return;
        }

        setHeader("Applications to Review", "Review candidates who applied to your offers");
        loadCenter("/view/job_offer/partner-applications.fxml", controller -> {
            if (controller instanceof PartnerApplicationsController c) {
                c.setCurrentUser(currentUser);
                c.restoreNavigationState(offerId, applicationId, step);
            }
        });
    }

    public void showJobApplicationReviewView(JobApplication application) {
        if (!ensureAuthenticated())
            return;
        if (application == null || application.getId() <= 0) {
            showWarning("Application unavailable", "Selected application is not available.");
            return;
        }
        setHeader("Application Review", "Review candidate application");
        loadCenter("/view/job_offer/application-review.fxml", controller -> {
            if (controller instanceof ApplicationReviewController c) {
                c.setApplication(application);
                c.setCurrentUser(currentUser);
            }
        });
    }

    public void showAtsApplicationDetailView(JobApplication application) {
        showAtsApplicationDetailView(application, null, null, 1);
    }

    public void showAtsApplicationDetailView(JobApplication application,
                                             Integer offerId,
                                             Integer applicationId,
                                             int returnStep) {
        if (!ensureAuthenticated()) return;
        if (application == null || application.getId() <= 0) {
            showWarning("Application unavailable", "Selected application is not available.");
            return;
        }
        setHeader("ATS · Application Detail", "Score breakdown, pipeline stage, and candidate profile");
        loadCenter("/view/job_offer/ats-application-detail.fxml", controller -> {
            if (controller instanceof AtsApplicationDetailController c) {
                c.setApplication(application);
                c.setCurrentUser(currentUser);
                c.setPartnerReviewReturnContext(offerId, applicationId, returnStep);
            }
        });
    }

    // ==================== FXML Handlers ====================
    @FXML
    private void onNavHome() {
        showHomeView();
    }

    @FXML
    private void onNavUsers() {
        showUsersView();
    }

    @FXML
    private void onNavForum() {
        showForumView();
    }

    @FXML
    private void onNavJobOffers() {
        showJobOffersView();
    }

    @FXML
    private void onNavIARooms() {
        showIARoomsView();
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
    private void onNavPrograms() {
        showProgramsView();
    }

    @FXML
    private void onNavModules() {
        showModulesView();
    }

    @FXML
    private void onNavCourses() {
        showCoursesView();
    }

    @FXML
    private void onNavContenu() {
        showContenuView();
    }

    @FXML
    private void onNavClasses() {
        showClassesView();
    }

    @FXML
    private void onNavMyClasses() {
        showTeacherClasses();
    }

    @FXML
    private void onNavMyLearning() {
        showStudentLearning();
    }

    @FXML
    private void onNavQuizzes() {
        showStudentQuizzesView(currentUser);
    }

    @FXML
    private void onNavEvaluation() {
        if (selectedModule != null && selectedModule.startsWith("EVALUATION") && evaluationSubNavExpanded) {
            evaluationSubNavExpanded = false;
            updateEvaluationSubNavVisibility();
            return;
        }
        evaluationSubNavExpanded = true;
        showEvaluationSection("GRADES", "Grades");
    }

    @FXML
    private void onNavEvaluationGrades() {
        evaluationSubNavExpanded = true;
        showEvaluationSection("GRADES", "Grades");
    }

    @FXML
    private void onNavEvaluationRecommendations() {
        evaluationSubNavExpanded = true;
        showEvaluationSection("RECOMMENDATIONS", "Recommendations");
    }

    @FXML
    private void onNavEvaluationSchedule() {
        evaluationSubNavExpanded = true;
        showEvaluationSection("SCHEDULE", "Schedule");
    }

    @FXML
    private void onNavEvaluationComplaints() {
        evaluationSubNavExpanded = true;
        showEvaluationSection("COMPLAINTS", "Complaints");
    }

    @FXML
    private void onNavEvaluationDocuments() {
        evaluationSubNavExpanded = true;
        showEvaluationSection("DOCUMENTS", "Documents");
    }

    // ==================== Internals ====================
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
        if (currentUser != null && currentUser.getId() != null)
            return true;
        Optional<Integer> sessionUserId = UserSession.getCurrentUserId();
        if (sessionUserId.isPresent()) {
            currentUser = userService.getUserById(sessionUserId.get().longValue()).orElse(null);
            if (currentUser != null) {
                setNavigationVisible(true);
                configureNavForRole();
                currentUserLabel.setText(buildUserBadge(currentUser));
                updateThemeToggleLabel(ThemeManager.getInstance().getActiveTheme(contentHost.getScene()));
                updateUserAvatar(currentUser);
                navUsersButton.setDisable(!isAdmin(currentUser));
            }
        }
        if (currentUser == null) {
            showLoginView();
            return false;
        }
        return true;
    }

    public void loadCenter(String fxmlPath, Consumer<Object> controllerInitializer) {
        try {
            ViewRouter.LoadedView loadedView = viewRouter.navigate(fxmlPath, controllerInitializer);
            contentHost.getChildren().setAll(loadedView.root());
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

    public void setHeader(String title, String subtitle) {
        headerTitleLabel.setText(title == null ? "" : title);
        headerSubtitleLabel.setText(subtitle == null ? "" : subtitle);
    }

    private void setNavigationState(String moduleKey, String... breadcrumbParts) {
        selectedModule = moduleKey == null ? "" : moduleKey;
        if (selectedModule == null || !selectedModule.startsWith("EVALUATION")) {
            evaluationSubNavExpanded = false;
        }
        breadcrumbs = Arrays.stream(breadcrumbParts)
                .filter(value -> value != null && !value.isBlank())
                .toList();

        updateBreadcrumbs();
        updateActiveNavigationButton();
        updateEvaluationSubNavVisibility();
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
        clearActiveState(navForumButton);
        clearActiveState(navJobOffersButton);
        clearActiveState(navIARoomsButton);
        clearActiveState(navEvaluationButton);
        clearActiveState(navEvaluationGradesButton);
        clearActiveState(navEvaluationRecommendationsButton);
        clearActiveState(navEvaluationScheduleButton);
        clearActiveState(navEvaluationComplaintsButton);
        clearActiveState(navEvaluationDocumentsButton);

        if (selectedModule != null && selectedModule.startsWith("EVALUATION")) {
            markActive(navEvaluationButton);
            switch (selectedModule) {
                case "EVALUATION_GRADES" -> markActive(navEvaluationGradesButton);
                case "EVALUATION_RECOMMENDATIONS" -> markActive(navEvaluationRecommendationsButton);
                case "EVALUATION_SCHEDULE" -> markActive(navEvaluationScheduleButton);
                case "EVALUATION_COMPLAINTS" -> markActive(navEvaluationComplaintsButton);
                case "EVALUATION_DOCUMENTS" -> markActive(navEvaluationDocumentsButton);
                default -> {
                    // Default entry highlights first section for students.
                    markActive(navEvaluationGradesButton);
                }
            }
            return;
        }

        switch (selectedModule) {
            case "HOME" -> markActive(navHomeButton);
            case "USERS" -> markActive(navUsersButton);
            case "PROFILE" -> markActive(navProfileButton);
            case "CHANGE_PASSWORD" -> markActive(navChangePasswordButton);
            case "FORUM" -> markActive(navForumButton);
            case "JOB_OFFERS" -> markActive(navJobOffersButton);
            case "IAROOMS" -> markActive(navIARoomsButton);
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
        if (sideRailScroll != null) {
            sideRailScroll.setManaged(visible);
            sideRailScroll.setVisible(visible);
        }

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

    private String normalizeRole(User user) {
        return RoleGuard.normalize(user);
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

    public User getCurrentUser() {
        return currentUser;
    }

    private void showEvaluationSection(String sectionKey, String sectionLabel) {
        if (!ensureAuthenticated()) {
            return;
        }

        String normalizedSection = sectionKey == null ? "GRADES" : sectionKey.trim().toUpperCase();
        setHeader("Evaluation Module", "Student, Teacher, and Admin evaluation workspace");
        setNavigationState("EVALUATION_" + normalizedSection, "Home", "Evaluation", sectionLabel);
        loadCenter("/view/evaluation/module-shell.fxml", controller -> {
            if (controller instanceof controller.evaluation.EvaluationShellController shellController) {
                shellController.setInitialStudentSection(normalizedSection);
            }
        });
    }

    private void updateEvaluationSubNavVisibility() {
        boolean student = RoleGuard.isStudent(currentUser);
        boolean visible = student && selectedModule != null && selectedModule.startsWith("EVALUATION") && evaluationSubNavExpanded;
        setNodeVisible(navEvaluationGradesButton, visible);
        setNodeVisible(navEvaluationRecommendationsButton, visible);
        setNodeVisible(navEvaluationScheduleButton, visible);
        setNodeVisible(navEvaluationComplaintsButton, visible);
        setNodeVisible(navEvaluationDocumentsButton, visible);
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
