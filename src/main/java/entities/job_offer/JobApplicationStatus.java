package entities.job_offer;

/**
 * Enumeration of job application statuses and ATS pipeline stages.
 *
 * <h3>Original status flow (preserved for backward compat):</h3>
 * <pre>
 *   SUBMITTED → REVIEWED → ACCEPTED / REJECTED
 *   SUBMITTED → REJECTED  (direct)
 * </pre>
 *
 * <h3>Extended ATS pipeline stages:</h3>
 * <pre>
 *   SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → OFFER_SENT → HIRED
 *   Any non-HIRED → REJECTED
 *   Any non-terminal → WITHDRAWN (by candidate)
 * </pre>
 *
 * The {@code pipelineStage} concept maps to these values; both the legacy
 * simple statuses and the new stages coexist. The DB column {@code pipeline_stage}
 * stores the ATS stage; {@code status} stores the legacy status.
 */
public enum JobApplicationStatus {

    // ── Legacy statuses (preserved for backward compat) ───────────────────────
    SUBMITTED("Application submitted, awaiting review"),
    REVIEWED("Application reviewed, awaiting decision"),
    ACCEPTED("Application accepted by employer"),
    REJECTED("Application rejected by employer"),

    // ── Extended ATS pipeline stages ─────────────────────────────────────────
    SCREENING("Under initial ATS screening"),
    SHORTLISTED("Candidate shortlisted — promising match"),
    INTERVIEW("Interview stage"),
    OFFER_SENT("Job offer extended to candidate"),
    HIRED("Candidate hired — pipeline complete"),
    WITHDRAWN("Candidate withdrew application");

    private final String description;

    JobApplicationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Display-friendly label shown in the UI. */
    public String getLabel() {
        return switch (this) {
            case SUBMITTED   -> "Submitted";
            case REVIEWED    -> "Reviewed";
            case ACCEPTED    -> "Accepted";
            case REJECTED    -> "Rejected";
            case SCREENING   -> "Screening";
            case SHORTLISTED -> "Shortlisted";
            case INTERVIEW   -> "Interview";
            case OFFER_SENT  -> "Offer Sent";
            case HIRED       -> "Hired";
            case WITHDRAWN   -> "Withdrawn";
        };
    }

    /** Returns true if no further ATS transitions are valid. */
    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == HIRED || this == WITHDRAWN;
    }

    /** Returns true when a status-change notification should be sent to the candidate. */
    public boolean requiresNotification() {
        return this == ACCEPTED || this == REJECTED || this == HIRED || this == OFFER_SENT;
    }

    /** Returns true for statuses that represent a positive hiring outcome. */
    public boolean hasDecision() {
        return this == ACCEPTED || this == REJECTED || this == HIRED || this == WITHDRAWN;
    }

    /**
     * Whether a transition from {@code current} to {@code next} is valid.
     *
     * <pre>
     * SUBMITTED   → SCREENING | SHORTLISTED | REJECTED | WITHDRAWN | REVIEWED
     * SCREENING   → SHORTLISTED | REJECTED | WITHDRAWN
     * SHORTLISTED → INTERVIEW  | REJECTED | WITHDRAWN
     * INTERVIEW   → OFFER_SENT | REJECTED | WITHDRAWN | HIRED
     * OFFER_SENT  → HIRED | REJECTED | WITHDRAWN
     * REVIEWED    → ACCEPTED | REJECTED
     * HIRED, ACCEPTED, REJECTED, WITHDRAWN → (terminal)
     * </pre>
     */
    public boolean canTransitionTo(JobApplicationStatus next) {
        if (next == null || this == next) return false;
        return switch (this) {
            case SUBMITTED   -> next == SCREENING || next == SHORTLISTED
                             || next == REJECTED  || next == WITHDRAWN || next == REVIEWED;
            case SCREENING   -> next == SHORTLISTED || next == REJECTED || next == WITHDRAWN;
            case SHORTLISTED -> next == INTERVIEW   || next == REJECTED || next == WITHDRAWN;
            case INTERVIEW   -> next == OFFER_SENT  || next == REJECTED || next == WITHDRAWN || next == HIRED;
            case OFFER_SENT  -> next == HIRED || next == REJECTED || next == WITHDRAWN;
            case REVIEWED    -> next == ACCEPTED || next == REJECTED;
            default          -> false; // ACCEPTED, REJECTED, HIRED, WITHDRAWN are terminal
        };
    }

    /**
     * Parse from string, case-insensitive. Defaults to SUBMITTED if not recognized.
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

