package validation;

import java.util.ArrayList;
import java.util.List;

public final class LmsValidator {

    private LmsValidator() {}

    public static List<String> validateProgramForm(String name) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) errors.add("Program name is required.");
        if (name != null && name.length() > 255) errors.add("Program name must be 255 characters or less.");
        return errors;
    }

    public static List<String> validateModuleForm(String name, String periodUnit, int duration) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) errors.add("Module name is required.");
        if (name != null && name.length() > 255) errors.add("Module name must be 255 characters or less.");
        if (periodUnit == null || periodUnit.trim().isEmpty()) errors.add("Period unit is required.");
        if (duration <= 0) errors.add("Duration must be a positive number.");
        return errors;
    }

    public static List<String> validateCourseForm(String title) {
        List<String> errors = new ArrayList<>();
        if (title == null || title.trim().isEmpty()) errors.add("Course title is required.");
        if (title != null && title.length() > 255) errors.add("Course title must be 255 characters or less.");
        return errors;
    }

    public static List<String> validateContenuForm(String title, String type, boolean writeMode,
                                                   String contentHtml, boolean hasFile) {
        List<String> errors = new ArrayList<>();
        if (title == null || title.trim().isEmpty()) errors.add("Content title is required.");
        if (type == null || type.trim().isEmpty()) errors.add("Content type is required.");
        if (writeMode) {
            if (contentHtml == null || contentHtml.replaceAll("(?s)<[^>]*>", "").replace("&nbsp;", " ").trim().isEmpty()) {
                errors.add("Content body is required when using the in-app editor.");
            }
        } else if (!hasFile) {
            errors.add("Select a file or switch to the in-app editor.");
        }
        return errors;
    }

    public static List<String> validateClasseForm(String name, String level, String specialty,
                                                   int capacity, java.sql.Date startDate, java.sql.Date endDate) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) errors.add("Class name is required.");
        if (level == null || level.trim().isEmpty()) errors.add("Level is required.");
        if (specialty == null || specialty.trim().isEmpty()) errors.add("Specialty is required.");
        if (capacity <= 0) errors.add("Capacity must be a positive number.");
        if (startDate == null) errors.add("Start date is required.");
        if (endDate == null) errors.add("End date is required.");
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            errors.add("End date must be after start date.");
        }
        return errors;
    }

    public static boolean isValidFileType(String mimeType) {
        if (mimeType == null) return false;
        String t = mimeType.toLowerCase();
        return t.equals("application/pdf")
                || t.equals("application/msword")
                || t.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || t.equals("application/vnd.ms-powerpoint")
                || t.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                || t.equals("application/vnd.ms-excel")
                || t.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || t.startsWith("video/")
                || t.startsWith("audio/")
                || t.startsWith("image/");
    }

    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB
}
