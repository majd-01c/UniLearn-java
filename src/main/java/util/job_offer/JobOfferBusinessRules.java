package util.job_offer;

import entities.job_offer.JobOfferStatus;
import entities.job_offer.JobApplicationStatus;

/**
 * Centralized domain rules and business logic enforcement for Job Offer module.
 * Contains all invariants and state transition validators.
 *
 * Business Rules:
 * - JobOfferType: INTERNSHIP, APPRENTICESHIP, JOB (immutable)
 * - JobOfferStatus: PENDING → ACTIVE → CLOSED; or PENDING → REJECTED (terminal)
 * - JobApplicationStatus: SUBMITTED → REVIEWED → ACCEPTED/REJECTED (terminal)
 * - One student cannot apply twice to same offer (unique constraint)
 * - Student can only apply when offer status is ACTIVE
 * - Partner can manage only their own offers
 * - Admin can override any offer/application
 * - Reopen closed offer sets it back to PENDING
 */
public final class JobOfferBusinessRules {

    private JobOfferBusinessRules() {}

    // ─────────────────────────────────────────────────────────
    // JobOffer State Transition Validators
    // ─────────────────────────────────────────────────────────

    /**
     * Validate if a job offer can transition from current status to new status.
     */
    public static boolean canTransitionOfferStatus(JobOfferStatus currentStatus, JobOfferStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }

        // Terminal state: cannot transition out of REJECTED
        if (currentStatus.isTerminal()) {
            return false;
        }

        // Allow some transitions
        if (currentStatus == newStatus) {
            return true;  // Idempotent
        }

        return switch (currentStatus) {
            case PENDING -> newStatus == JobOfferStatus.ACTIVE || newStatus == JobOfferStatus.REJECTED;
            case ACTIVE -> newStatus == JobOfferStatus.CLOSED;
            case CLOSED -> newStatus == JobOfferStatus.PENDING;  // Reopen
            case REJECTED -> false;  // Terminal, no exit
        };
    }

    /**
     * Validate error message for invalid offer status transition.
     */
    public static String offerTransitionErrorMessage(JobOfferStatus from, JobOfferStatus to) {
        if (from != null && from.isTerminal()) {
            return "Cannot transition out of terminal status: " + from.name();
        }
        return from + " → " + to + " is not a valid transition";
    }

    // ─────────────────────────────────────────────────────────
    // JobApplication State Transition Validators
    // ─────────────────────────────────────────────────────────

    /**
     * Validate if a job application can transition from current status to new status.
     */
    public static boolean canTransitionApplicationStatus(JobApplicationStatus currentStatus, 
                                                          JobApplicationStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }

        // Terminal state: cannot transition out of ACCEPTED or REJECTED
        if (currentStatus.isTerminal()) {
            return false;
        }

        // Allow some transitions
        if (currentStatus == newStatus) {
            return true;  // Idempotent
        }

        return currentStatus.canTransitionTo(newStatus);
    }

    /**
     * Validate error message for invalid application status transition.
     */
    public static String applicationTransitionErrorMessage(JobApplicationStatus from, 
                                                            JobApplicationStatus to) {
        if (from != null && from.isTerminal()) {
            return "Cannot transition out of terminal status: " + from.name();
        }
        return from + " → " + to + " is not a valid transition";
    }

    // ─────────────────────────────────────────────────────────
    // Application Permission & Eligibility Rules
    // ─────────────────────────────────────────────────────────

    /**
     * Check if a student is eligible to apply to an offer.
     * Requirements:
     * - Offer status must be ACTIVE
     * - Student has not already applied (checked separately via DB constraint)
     */
    public static boolean canStudentApplyToOffer(JobOfferStatus offerStatus) {
        return offerStatus != null && offerStatus.acceptsApplications();
    }

    /**
     * Check if an application needs status notification (state transition requires notification).
     */
    public static boolean needsStatusNotification(JobApplicationStatus applicationStatus) {
        return applicationStatus != null && applicationStatus.requiresNotification();
    }

    /**
     * Check if an application has received a final decision.
     */
    public static boolean hasApplicationDecision(JobApplicationStatus applicationStatus) {
        return applicationStatus != null && applicationStatus.hasDecision();
    }

    // ─────────────────────────────────────────────────────────
    // Partner/Admin Permission Rules
    // ─────────────────────────────────────────────────────────

    /**
     * Check if a partner can edit/delete an offer.
     * - Must be offer owner (partnerId matches)
     * - OR must be ADMIN (handled separately)
     */
    public static boolean canPartnerManageOffer(Integer partnerId, Integer offerOwnerId) {
        return partnerId != null && offerOwnerId != null && partnerId.equals(offerOwnerId);
    }

    /**
     * Check if a partner can review applications for an offer.
     * - Must be offer owner OR ADMIN
     */
    public static boolean canPartnerReviewOffer(Integer partnerId, Integer offerOwnerId) {
        return canPartnerManageOffer(partnerId, offerOwnerId);
    }

    // ─────────────────────────────────────────────────────────
    // Field Validation Rules
    // ─────────────────────────────────────────────────────────

    /**
     * Validate job offer required fields.
     */
    public static String validateJobOfferInput(String title, String description, String type) {
        if (title == null || title.isBlank()) {
            return "Title is required";
        }
        if (title.length() < 3 || title.length() > 255) {
            return "Title must be between 3 and 255 characters";
        }
        if (description == null || description.isBlank()) {
            return "Description is required";
        }
        if (type == null || type.isBlank()) {
            return "Job type is required";
        }
        return null;  // Valid
    }

    /**
     * Validate job application input.
     */
    public static String validateJobApplicationInput(String message, String cvFileName) {
        // CV filename is optional in current phase
        // Message is optional (student can apply without motivation)
        return null;  // Valid (permissive in phase 1)
    }

    /**
     * Validate experience years.
     */
    public static String validateExperienceYears(Integer years) {
        if (years == null) {
            return null;  // Optional field
        }
        if (years < 0 || years > 100) {
            return "Experience years must be between 0 and 100";
        }
        return null;
    }

    /**
     * Validate application score (0-100).
     */
    public static String validateApplicationScore(Integer score) {
        if (score == null) {
            return null;  // Not yet scored
        }
        if (score < 0 || score > 100) {
            return "Score must be between 0 and 100";
        }
        return null;
    }
}
