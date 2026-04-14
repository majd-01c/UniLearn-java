package entities.job_offer;

/**
 * Enumeration of job application statuses.
 * Represents the state of a student's application to a job offer.
 *
 * State transitions:
 *   SUBMITTED → REVIEWED     (Partner/Admin reviews)
 *   REVIEWED → ACCEPTED      (Candidate successful)
 *   REVIEWED → REJECTED      (Candidate unsuccessful)
 *   SUBMITTED → REJECTED     (Direct rejection without review)
 *   ACCEPTED → (terminal)    (Cannot transition out)
 *   REJECTED → (terminal)    (Cannot transition out)
 */
public enum JobApplicationStatus {
    SUBMITTED("Application submitted, awaiting review"),
    REVIEWED("Application reviewed, awaiting decision"),
    ACCEPTED("Application accepted by employer"),
    REJECTED("Application rejected by employer");

    private final String description;

    JobApplicationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this is a decision state (no further review possible).
     */
    public boolean hasDecision() {
        return this == ACCEPTED || this == REJECTED;
    }

    /**
     * Check if status notification should be sent to student.
     * Notifications sent when a decision is made.
     */
    public boolean requiresNotification() {
        return this == ACCEPTED || this == REJECTED;
    }

    /**
     * Check if this is a terminal state (no transitions possible).
     */
    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED;
    }

    /**
     * Parse from string, case-insensitive.
     * Defaults to SUBMITTED if not recognized.
     */
    public static JobApplicationStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return SUBMITTED;
        }
        try {
            return JobApplicationStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SUBMITTED;
        }
    }
}
