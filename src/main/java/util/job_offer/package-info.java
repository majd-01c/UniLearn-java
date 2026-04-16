/**
 * Utility classes for the Job Offer module.
 * 
 * Packages:
 * - JobOfferBusinessRules: Domain invariants, state transitions, permission checks
 * - JobOfferValidators: Input validation, field validators, helper functions
 * 
 * Business Rules Enforced:
 * - JobOfferStatus state machine: PENDING → ACTIVE/REJECTED → CLOSED (with reopen)
 * - JobApplicationStatus state machine: SUBMITTED → REVIEWED → ACCEPTED/REJECTED (terminal)
 * - One application per student per offer (via unique DB constraint)
 * - Only ACTIVE offers accept new applications
 * - Partner can only manage own offers; Admin can manage all
 * - Closed offers can be reopened to PENDING status
 * 
 * Validation Performed:
 * - Required field presence
 * - Field length constraints
 * - Email format validation
 * - Education level normalization
 * - CV file extension validation
 * - Score range (0-100)
 * - Experience years (0-100)
 * - Date/expiration checks
 * 
 * @author UniLearn Development Team
 * @since 2026-04
 */
package util.job_offer;
