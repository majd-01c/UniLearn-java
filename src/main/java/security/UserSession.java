package security;

import entities.User;

import java.util.Optional;

public final class UserSession {

    private static volatile Integer currentUserId;

    private UserSession() {
    }

    public static void setCurrentUser(User user) {
        currentUserId = user == null ? null : user.getId();
    }

    public static Optional<Integer> getCurrentUserId() {
        return Optional.ofNullable(currentUserId);
    }

    public static boolean isLoggedIn() {
        return currentUserId != null;
    }

    public static void clear() {
        currentUserId = null;
    }
}
