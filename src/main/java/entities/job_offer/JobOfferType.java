package entities.job_offer;

/**
 * Enumeration of job offer types.
 * Represents the classification of employment opportunities.
 */
public enum JobOfferType {
    INTERNSHIP("Internship - Temporary position for students/graduates gaining experience"),
    APPRENTICESHIP("Apprenticeship - Structured learning program combining work and training"),
    JOB("Job - Full-time or part-time permanent position");

    private final String description;

    JobOfferType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse from string, case-insensitive.
     * Defaults to JOB if not recognized.
     */
    public static JobOfferType fromString(String value) {
        if (value == null || value.isBlank()) {
            return JOB;
        }
        try {
            return JobOfferType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return JOB;
        }
    }
}
