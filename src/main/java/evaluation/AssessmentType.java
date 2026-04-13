package evaluation;

public enum AssessmentType {
    CC,
    EXAM,
    OTHER;

    public static AssessmentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String normalized = value.trim().toUpperCase();
        if ("CC".equals(normalized)) {
            return CC;
        }
        if ("EXAM".equals(normalized)) {
            return EXAM;
        }
        return OTHER;
    }
}
