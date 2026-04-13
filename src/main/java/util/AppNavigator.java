package util;

import controller.AppShellController;
import entities.User;
import entities.forum.ForumCategory;
import entities.forum.ForumComment;
import entities.forum.ForumTopic;

public final class AppNavigator {

    private static AppShellController appShellController;

    private AppNavigator() {
    }

    public static void registerShell(AppShellController controller) {
        appShellController = controller;
    }

    public static void showLogin() {
        if (appShellController != null) {
            appShellController.showLoginView();
        }
    }

    public static void loginSuccess(User user) {
        if (appShellController != null) {
            appShellController.handleLoginSuccess(user);
        }
    }

    public static void showHome() {
        if (appShellController != null) {
            appShellController.showHomeView();
        }
    }

    public static void showUsers() {
        if (appShellController != null) {
            appShellController.showUsersView();
        }
    }

    public static void showProfile() {
        if (appShellController != null) {
            appShellController.showProfileView();
        }
    }

    public static void showPasswordResetRequest() {
        if (appShellController != null) {
            appShellController.showPasswordResetRequestView();
        }
    }

    public static void showPasswordReset(String tokenOrUrl) {
        if (appShellController != null) {
            appShellController.showPasswordResetView(tokenOrUrl);
        }
    }

    public static void showChangePassword() {
        if (appShellController != null) {
            appShellController.showChangePasswordView();
        }
    }

    public static void showUserDetails(User user) {
        if (appShellController != null) {
            appShellController.showUserDetailsView(user);
        }
    }

    public static void logout() {
        if (appShellController != null) {
            appShellController.logout();
        }
    }

    // ========================
    // FORUM NAVIGATION
    // ========================

    public static void showForum() {
        if (appShellController != null) {
            appShellController.showForumView();
        }
    }

    public static void showForumCategory(ForumCategory category) {
        if (appShellController != null) {
            appShellController.showForumCategoryView(category);
        }
    }

    public static void showForumTopic(ForumTopic topic) {
        if (appShellController != null) {
            appShellController.showForumTopicView(topic);
        }
    }

    public static void showForumNewTopic() {
        if (appShellController != null) {
            appShellController.showForumNewTopicView();
        }
    }

    public static void showForumEditTopic(ForumTopic topic) {
        if (appShellController != null) {
            appShellController.showForumEditTopicView(topic);
        }
    }

    public static void showForumEditComment(ForumComment comment) {
        if (appShellController != null) {
            appShellController.showForumEditCommentView(comment);
        }
    }

    public static void showForumAdminCategories() {
        if (appShellController != null) {
            appShellController.showForumAdminCategoriesView();
        }
    }
}
