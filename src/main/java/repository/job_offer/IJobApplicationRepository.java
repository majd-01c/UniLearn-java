package repository.job_offer;

import dto.job_offer.ApplicationReviewRowDto;
import dto.job_offer.JobApplicationDto;
import dto.job_offer.JobApplicationRowDto;
import entities.job_offer.JobApplication;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for JobApplication persistence operations.
 * Defines contracts for application data access with support for:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Duplicate prevention checks (critical business rule)
 * - Student application history queries
 * - Partner review queue queries
 * - Admin bulk operations
 * - DTO projections for UI layers
 * 
 * Key Constraint: UNIQUE(job_offer_id, student_id) enforced at DB level
 */
public interface IJobApplicationRepository {

    // ─────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────

    /**
     * Find a job application by ID.
     * @param applicationId Application primary key
     * @return Optional containing application if found
     */
    Optional<JobApplication> findById(Integer applicationId);

    /**
     * Save (create or update) a job application.
     * @param application JobApplication entity to persist
     * @return Persisted JobApplication with ID assigned
     * @throws IllegalStateException if persistence fails
     */
    JobApplication save(JobApplication application);

    /**
     * Delete a job application by ID.
     * @param applicationId Application to remove
     * @throws IllegalStateException if deletion fails
     */
    void delete(Integer applicationId);

    // ─────────────────────────────────────────────────────────
    // Duplicate Prevention (Critical for Business Rules)
    // ─────────────────────────────────────────────────────────

    /**
     * Check if a student has already applied to a specific offer.
     * CRITICAL: Prevent duplicate applications (enforced by UNIQUE constraint at DB).
     * 
     * Must be called BEFORE creating new application.
     * 
     * @param studentId Student's user ID
     * @param offerId Job offer ID
     * @return true if student has existing application for this offer
     */
    boolean hasStudentApplied(Integer studentId, Integer offerId);

    /**
     * Get all offer IDs that a student has applied to.
     * Useful for UI to mark "Already Applied" badges on offer list.
     * 
     * @param studentId Student's user ID
     * @return Set of offer IDs the student has applications for
     */
    Set<Integer> getStudentAppliedOfferIds(Integer studentId);

    /**
     * Alias required by prompt contract naming.
     *
     * @param studentId Student's user ID
     * @return Set of offer IDs the student has applications for
     */
    Set<Integer> getAppliedOfferIds(Integer studentId);

    // ─────────────────────────────────────────────────────────
    // Student Application History
    // ─────────────────────────────────────────────────────────

    /**
     * Get all applications submitted by a student with pagination.
     * Used by: MyApplicationsController for student's application history
     * 
     * @param studentId Student's user ID
     * @param offset Pagination offset (0-based)
     * @param limit Results per page
    * @return Student's applications (newest first)
     */
    List<JobApplication> findByStudentId(Integer studentId, int offset, int limit);

    /**
     * Count applications for a student.
     * 
     * @param studentId Student's user ID
     * @return Total application count
     */
    long countByStudentId(Integer studentId);

    // ─────────────────────────────────────────────────────────
    // Partner Review Queue (Applications to Review)
    // ─────────────────────────────────────────────────────────

    /**
     * Get applications for a partner's offers that need review.
     * Filters: offer owner = partner AND status = SUBMITTED (awaiting review)
     * Used by: ApplicationReviewController to show partner's pending reviews
     * 
     * @param partnerId Partner's user ID
     * @param offset Pagination offset
     * @param limit Results per page
     * @return Applications waiting for partner's decision
     */
    List<JobApplication> findApplicationsForPartnerReview(Integer partnerId, int offset, int limit);

    /**
     * Count applications awaiting partner's review.
     * 
     * @param partnerId Partner's user ID
     * @return Total pending review count
     */
    long countApplicationsForPartnerReview(Integer partnerId);

    // ─────────────────────────────────────────────────────────
    // Admin Queries (Bulk Operations)
    // ─────────────────────────────────────────────────────────

    /**
     * Find all applications with a specific status.
     * Used by: Admin dashboard for bulk operations/reporting
     * 
     * @param status Filter by status (SUBMITTED/REVIEWED/ACCEPTED/REJECTED)
     * @param offset Pagination offset
     * @param limit Results per page
     * @return Matching applications
     */
    List<JobApplication> findByStatus(String status, int offset, int limit);

    /**
     * Find all applications for a specific offer.
     * Used by: Admin viewing all candidates for an offer
     * 
     * @param offerId Filter by offer
     * @param offset Pagination offset
     * @param limit Results per page
     * @return All applications for offer
     */
    List<JobApplication> findByOfferId(Integer offerId, int offset, int limit);

    // ─────────────────────────────────────────────────────────
    // DTO Projections (for UI Layers)
    // ─────────────────────────────────────────────────────────

    /**
     * Get optimized JobApplicationRowDto for student application list display.
     * Includes: id, offer title/type, status, score, dates
     * 
     * @param studentId Filter by student
     * @param offset Pagination offset
     * @param limit Results per page
     * @return DTO list for ListView/TableView binding
     */
    List<JobApplicationRowDto> findStudentApplicationRows(Integer studentId, int offset, int limit);

    /**
     * Get optimized ApplicationReviewRowDto for partner review queue display.
     * Includes: id, offer title, student email, status, score, date
     * 
     * @param partnerId Filter by partner (offer owner)
     * @param offset Pagination offset
     * @param limit Results per page
     * @return DTO list for partner's review interface
     */
    List<ApplicationReviewRowDto> findPartnerReviewRows(Integer partnerId, int offset, int limit);

    /**
     * Get full JobApplicationDto for detailed review/editing view.
     * Includes: all fields, extracted CV data (Phase 2), notification status
     * 
     * @param applicationId Application to fetch
     * @return Full detail DTO
     */
    Optional<JobApplicationDto> findApplicationDto(Integer applicationId);
}
