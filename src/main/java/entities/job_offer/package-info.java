/**
 * Domain entities for the Job Offer module.
 * 
 * Entities:
 * - JobOffer: Core job posting entity with title, description, requirements, and status
 * - JobApplication: Student application to a job offer with decision tracking
 * - GeneralChatMessage: Messaging between partners and students (phase 2)
 * 
 * Enums:
 * - JobOfferType: INTERNSHIP, APPRENTICESHIP, JOB
 * - JobOfferStatus: PENDING, ACTIVE, CLOSED, REJECTED (with state transition rules)
 * - JobApplicationStatus: SUBMITTED, REVIEWED, ACCEPTED, REJECTED (with terminal states)
 * 
 * All entities are Hibernate-managed and mapped to MySQL database.
 * Relationships use LAZY loading for performance (fetch on demand).
 * 
 * @author UniLearn Development Team
 * @since 2026-04
 */
package entities.job_offer;
