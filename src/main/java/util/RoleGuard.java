package util;

import entities.User;
import security.UserSession;

/**
 * Centralized role-based access control guard.
 *
 * <h3>Permission Matrix</h3>
 * <pre>
 * ┌──────────────────────────────────┬───────┬─────────┬─────────┐
 * │ Action                           │ ADMIN │ TEACHER │ STUDENT │
 * ├──────────────────────────────────┼───────┼─────────┼─────────┤
 * │ Global CRUD Program/Module/      │  ✓    │   ✗     │   ✗     │
 * │   Course/Content/Class           │       │         │         │
 * │ Assign program to class          │  ✓    │   ✗     │   ✗     │
 * │ Enroll/unenroll students         │  ✓    │   ✗     │   ✗     │
 * │ Assign/unassign teachers         │  ✓    │   ✗     │   ✗     │
 * │ Create module for own assignment │  ✗    │   ✓     │   ✗     │
 * │ Add course to own class module   │  ✓    │   ✓*    │   ✗     │
 * │ Add content to own class course  │  ✓    │   ✓*    │   ✗     │
 * │ Toggle visibility on own items   │  ✓    │   ✓*    │   ✗     │
 * │ Read own learning path           │  ✓    │   ✓     │   ✓     │
 * └──────────────────────────────────┴───────┴─────────┴─────────┘
 *  * = ownership + active-assignment check required
 * </pre>
 */
public final class RoleGuard {

    // ── Role constants (single source of truth) ──
    public static final String ROLE_ADMIN   = "ADMIN";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_STUDENT = "STUDENT";

    private RoleGuard() {}

    // ── Normalize from entity ──
    public static String normalize(User user) {
        if (user == null || user.getRole() == null) return "";
        String r = user.getRole().trim().toUpperCase();
        return r.startsWith("ROLE_") ? r.substring(5) : r;
    }

    // ── Entity-based checks ──
    public static boolean isAdmin(User u)   { return ROLE_ADMIN.equals(normalize(u)); }
    public static boolean isTeacher(User u) { return ROLE_TEACHER.equals(normalize(u)); }
    public static boolean isStudent(User u) { return ROLE_STUDENT.equals(normalize(u)); }

    public static void requireAdmin(User u) {
        if (!isAdmin(u)) throw new SecurityException("Access denied: administrator privileges required.");
    }
    public static void requireTeacher(User u) {
        if (!isTeacher(u)) throw new SecurityException("Access denied: teacher privileges required.");
    }
    public static void requireStudent(User u) {
        if (!isStudent(u)) throw new SecurityException("Access denied: student privileges required.");
    }

    // ── Session-based checks (for service-layer hard security) ──

    /** Returns the current session role, never null. */
    public static String currentRole() {
        return UserSession.getCurrentUserRole();
    }

    public static boolean isCurrentAdmin()   { return ROLE_ADMIN.equals(currentRole()); }
    public static boolean isCurrentTeacher() { return ROLE_TEACHER.equals(currentRole()); }
    public static boolean isCurrentStudent() { return ROLE_STUDENT.equals(currentRole()); }

    /**
     * Throws SecurityException if the logged-in user is not an ADMIN.
     * Use at the top of every admin-only write operation in service layer.
     */
    public static void requireCurrentAdmin() {
        if (!isCurrentAdmin()) {
            throw new SecurityException("Access denied: you do not have permission to perform this action. Administrator privileges required.");
        }
    }

    /**
     * Throws SecurityException if the logged-in user is not a TEACHER.
     */
    public static void requireCurrentTeacher() {
        if (!isCurrentTeacher()) {
            throw new SecurityException("Access denied: teacher privileges required.");
        }
    }

    /**
     * Throws SecurityException if the logged-in user is not an ADMIN or TEACHER.
     */
    public static void requireCurrentAdminOrTeacher() {
        if (!isCurrentAdmin() && !isCurrentTeacher()) {
            throw new SecurityException("Access denied: administrator or teacher privileges required.");
        }
    }
}
