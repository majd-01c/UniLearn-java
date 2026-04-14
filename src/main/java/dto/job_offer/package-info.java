/**
 * Data Transfer Objects for the Job Offer module.
 * 
 * DTOs provide clean interfaces between layers and enable flexible data access patterns.
 * All DTOs are immutable (final fields, no setters) following the project convention.
 * 
 * DTO Types:
 * - JobOfferDto: Full job offer data for detailed views
 * - JobOfferOptionDto: Minimal DTO for ComboBox/dropdown selection (offerId + title)
 * - JobOfferRowDto: Optimized for table row display in list views
 * - JobApplicationDto: Full application data with all fields
 * - JobApplicationRowDto: Table row display for student's application list
 * - ApplicationReviewRowDto: Partner review queue display format
 * 
 * Design Pattern:
 * - Immutable: All fields final, set via constructor only
 * - toString() override for UI display
 * - Getters for all fields
 * - No business logic (data holder only)
 * 
 * @author UniLearn Development Team
 * @since 2026-04
 */
package dto.job_offer;
