package util.job_offer;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Utility validators for Job Offer module.
 * Provides common validation helpers for forms, services, and controllers.
 */
public final class JobOfferValidators {

    private JobOfferValidators() {}

    /**
     * Validate email format (basic).
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        // Simple pattern: word@domain.extension
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z]{2,}$");
    }

    /**
     * Validate if an offer is expired.
     */
    public static boolean isOfferExpired(Timestamp expiresAt) {
        if (expiresAt == null) {
            return false;  // No expiration set
        }
        return expiresAt.before(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Check if a timestamp is in the past.
     */
    public static boolean isPastDate(Timestamp date) {
        if (date == null) {
            return false;
        }
        return date.before(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Check if a timestamp is in the future.
     */
    public static boolean isFutureDate(Timestamp date) {
        if (date == null) {
            return false;
        }
        return date.after(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Validate location field (non-empty for now).
     */
    public static boolean isValidLocation(String location) {
        return location != null && !location.isBlank() && location.length() >= 2;
    }

    /**
     * Validate CV filename.
     */
    public static boolean isValidCvFileName(String cvFileName) {
        if (cvFileName == null || cvFileName.isBlank()) {
            return false;  // CV required for application
        }
        // Allow common file extensions: .pdf, .doc, .docx
        String lower = cvFileName.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") ||
               lower.endsWith(".txt") || lower.endsWith(".rtf");
    }

    /**
     * Validate application message length (with reasonable bounds).
     */
    public static boolean isValidApplicationMessage(String message) {
        if (message == null) {
            return true;  // Optional field
        }
        if (message.isBlank()) {
            return true;  // Empty is OK
        }
        return message.length() <= 5000;  // Max 5000 chars
    }

    /**
     * Validate skills field (comma-separated list).
     */
    public static boolean isValidSkillsField(String skills) {
        if (skills == null) {
            return true;  // Optional
        }
        return skills.length() <= 5000;  // Reasonable max
    }

    /**
     * Parse a comma-separated skills string into array.
     */
    public static String[] parseSkillsList(String skills) {
        if (skills == null || skills.isBlank()) {
            return new String[0];
        }
        return skills.split(",");
    }

    /**
     * Validate education level string.
     */
    public static boolean isValidEducationLevel(String education) {
        if (education == null) {
            return true;  // Optional
        }
        String upper = education.toUpperCase();
        return upper.equals("HIGH_SCHOOL") || upper.equals("BACHELOR") || 
               upper.equals("MASTER") || upper.equals("PHD") || 
               upper.equals("NOT_REQUIRED");
    }

    /**
     * Normalize education level string.
     */
    public static String normalizeEducationLevel(String education) {
        if (education == null || education.isBlank()) {
            return "NOT_REQUIRED";
        }
        String upper = education.trim().toUpperCase();
        return upper.equals("HIGH_SCHOOL") || upper.equals("BACHELOR") || 
               upper.equals("MASTER") || upper.equals("PHD") || 
               upper.equals("NOT_REQUIRED") ? upper : "NOT_REQUIRED";
    }
}
