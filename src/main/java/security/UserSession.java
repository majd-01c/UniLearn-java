package security;

import entities.User;

import java.util.Optional;

/**
 * Thread-local session context for the currently logged-in user.
 * Caches the normalized role (ADMIN / TEACHER / STUDENT) to avoid
 * repeated database lookups during authorization checks.
 */
public final class UserSession {

    private static volatile Integer currentUserId;
    private static volatile String currentUserRole;

    private UserSession() {
    }

    public static void setCurrentUser(User user) {
        if (user == null) {
            currentUserId = null;
            currentUserRole = null;
        } else {
            currentUserId = user.getId();
            currentUserRole = normalizeRole(user.getRole());
        }
    }

    public static Optional<Integer> getCurrentUserId() {
        return Optional.ofNullable(currentUserId);
    }

    /** Returns the cached, normalized role string (e.g. "ADMIN", "TEACHER", "STUDENT"). */
    public static String getCurrentUserRole() {
        return currentUserRole != null ? currentUserRole : "";
    }

    public static boolean isLoggedIn() {
        return currentUserId != null;
    }

    public static void clear() {
        currentUserId = null;
        currentUserRole = null;
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "";
        String r = role.trim().toUpperCase();
        return r.startsWith("ROLE_") ? r.substring(5) : r;
    }
}
