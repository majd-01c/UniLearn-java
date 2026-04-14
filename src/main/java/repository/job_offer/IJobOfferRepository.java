package repository.job_offer;

import dto.job_offer.JobOfferDto;
import dto.job_offer.JobOfferOptionDto;
import dto.job_offer.JobOfferRowDto;
import entities.job_offer.JobOffer;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for JobOffer persistence operations.
 * Defines contracts for job offer data access with support for:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Public search by all users (students/partners)
 * - Partner-specific queries (own offers)
 * - Admin oversight queries (all statuses)
 * - DTO projections for UI layers
 */
public interface IJobOfferRepository {

    // ─────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────

    /**
     * Find a job offer by ID.
     * @param offerId Job offer primary key
     * @return Optional containing offer if found
     */
    Optional<JobOffer> findById(Integer offerId);

    /**
     * Save (create or update) a job offer.
     * @param offer JobOffer entity to persist
     * @return Persisted JobOffer with ID assigned
     * @throws IllegalStateException if persistence fails
     */
    JobOffer save(JobOffer offer);

    /**
     * Delete a job offer by ID.
     * @param offerId Job offer to remove
     * @throws IllegalStateException if deletion fails
     */
    void delete(Integer offerId);

    // ─────────────────────────────────────────────────────────
    // Public Search (All Users)
    // ─────────────────────────────────────────────────────────

    /**
     * Search active job offers available to students/partners.
     * Filters: type (INTERNSHIP/APPRENTICESHIP/JOB), location, text search
     * 
     * @param offset Pagination offset (0-based)
     * @param limit Results per page
     * @param type Optional job type filter
     * @param location Optional location filter
     * @param searchText Optional title/description search
     * @return List of matching active offers
     */
    List<JobOffer> findActiveOffers(int offset, int limit, String type, String location, String searchText);

    /**
     * Count active offers matching filters (for pagination UI).
     * 
     * @param type Optional filter
     * @param location Optional filter
     * @param searchText Optional filter
     * @return Total matching offer count
     */
    long countActiveOffers(String type, String location, String searchText);

    // ─────────────────────────────────────────────────────────
    // Partner-Specific Queries
    // ─────────────────────────────────────────────────────────

    /**
     * Get all offers posted by a specific partner.
     * 
     * @param partnerId Partner's user ID
     * @param offset Pagination offset
     * @param limit Results per page
     * @return Partner's job offers
     */
    List<JobOffer> findByPartnerId(Integer partnerId, int offset, int limit);

    /**
     * Count partner's offers.
     * 
     * @param partnerId Partner's user ID
     * @return Total offer count for partner
     */
    long countByPartnerId(Integer partnerId);

    // ─────────────────────────────────────────────────────────
    // Admin Search (All Statuses + Filters)
    // ─────────────────────────────────────────────────────────

    /**
     * Admin search: find all offers regardless of status.
     * Filters: status, type, partner, text search
     * 
     * @param offset Pagination offset
     * @param limit Results per page
     * @param status Optional specific status (PENDING/ACTIVE/CLOSED/REJECTED)
     * @param type Optional job type
     * @param partnerId Optional filter by partner
     * @param searchText Optional text search
     * @return Matching offers
     */
    List<JobOffer> findAllForAdmin(int offset, int limit, String status, String type, 
                                   Integer partnerId, String searchText);

    /**
     * Count all offers matching admin filters.
     * 
     * @param status Optional filter
     * @param type Optional filter
     * @param partnerId Optional filter
     * @param searchText Optional filter
     * @return Total matching offer count
     */
    long countAllForAdmin(String status, String type, Integer partnerId, String searchText);

    // ─────────────────────────────────────────────────────────
    // DTO Projections (for UI Layers)
    // ─────────────────────────────────────────────────────────

    /**
     * Get optimized JobOfferRowDto for table/list display.
     * Includes: id, title, type, status, location, partner email, application count, date
     * 
     * @param offset Pagination offset
     * @param limit Results per page
     * @param type Optional filter
     * @param location Optional filter
     * @param searchText Optional filter
     * @return DTO list for ListView/TableView binding
     */
    List<JobOfferRowDto> findActiveOfferRows(int offset, int limit, String type, 
                                            String location, String searchText);

    /**
     * Get JobOfferOptionDto for ComboBox/dropdown selection.
     * Includes: id, title only
     * 
     * @param partnerId Filter by partner
     * @return DTO list for UI selections
     */
    List<JobOfferOptionDto> findPartnerOfferOptions(Integer partnerId);

    /**
     * Get full JobOfferDto for detailed view/editing.
     * Includes: all 19 fields, relationship counts
     * 
     * @param offerId Offer to fetch
     * @return Full detail DTO
     */
    Optional<JobOfferDto> findOfferDto(Integer offerId);
}
