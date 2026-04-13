package util;

import controller.AppShellController;
import entities.User;

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
}
