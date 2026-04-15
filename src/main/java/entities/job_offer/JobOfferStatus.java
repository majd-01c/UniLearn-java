package entities.job_offer;

/**
 * Enumeration of job offer statuses.
 * Represents the lifecycle state of a job offer.
 *
 * State transitions:
 *   PENDING → ACTIVE        (Admin approval)
 *   PENDING → REJECTED      (Admin rejection)
 *   ACTIVE → CLOSED         (Expired or partner closed)
 *   CLOSED → PENDING        (Reopen for re-posting)
 *   REJECTED → (terminal)   (Cannot transition out)
 */
public enum JobOfferStatus {
    PENDING("Pending approval by admin"),
    ACTIVE("Published and accepting applications"),
    REJECTED("Rejected by admin (terminal state)"),
    CLOSED("Closed or expired (no new applications)");

    private final String description;

    JobOfferStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if students can apply to offers in this status.
     * Only ACTIVE offers accept new applications.
     */
    public boolean acceptsApplications() {
        return this == ACTIVE;
    }

    /**
     * Check if this is a terminal state (no transitions possible).
     */
    public boolean isTerminal() {
        return this == REJECTED;
    }

    /**
     * Parse from string, case-insensitive.
     * Defaults to PENDING if not recognized.
     */
    public static JobOfferStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return JobOfferStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
