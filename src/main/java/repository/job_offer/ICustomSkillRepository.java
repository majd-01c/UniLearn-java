package repository.job_offer;

import entities.CustomSkill;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CustomSkill persistence operations.
 * Defines contracts for job-related skills management:
 * - Partner can define custom skills for their job offers
 * - Skills can be marked as required or preferred
 * - Admin can manage skill taxonomy
 * 
 * Note: This may integrate with existing skill/competency system in Phase 2
 */
public interface ICustomSkillRepository {

    // ─────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────

    /**
     * Find a custom skill by ID.
     * @param skillId Skill primary key
     * @return Optional containing skill if found
     */
    Optional<CustomSkill> findById(Integer skillId);

    /**
     * Save (create or update) a custom skill.
     * @param skill CustomSkill entity to persist
     * @return Persisted CustomSkill with ID assigned
     * @throws IllegalStateException if persistence fails
     */
    CustomSkill save(CustomSkill skill);

    /**
     * Delete a custom skill by ID.
     * @param skillId Skill to remove
     * @throws IllegalStateException if deletion fails
     */
    void delete(Integer skillId);

    // ─────────────────────────────────────────────────────────
    // Partner-Specific Queries
    // ─────────────────────────────────────────────────────────

    /**
     * Get all custom skills defined by a specific partner.
     * Partners can create reusable skill lists for their job offer templates.
     * 
     * @param partnerId Partner's user ID
     * @return All skills created by partner
     */
    List<CustomSkill> findByPartnerId(Integer partnerId);

    /**
     * Search partner's skills by name/description.
     * Used for: Skill selection UI when creating job offers
     * 
     * @param partnerId Partner's user ID
     * @param searchText Skill name or description search
     * @return Matching skills
     */
    List<CustomSkill> searchPartnerSkills(Integer partnerId, String searchText);

    // ─────────────────────────────────────────────────────────
    // Admin Queries
    // ─────────────────────────────────────────────────────────

    /**
     * Get all custom skills across all partners (admin view).
     * @return All custom skills
     */
    List<CustomSkill> findAll();

    /**
     * Search all custom skills (admin reporting).
     * @param searchText Skill name search
     * @return Matching skills
     */
    List<CustomSkill> search(String searchText);

    // ─────────────────────────────────────────────────────────
    // Utility Methods
    // ─────────────────────────────────────────────────────────

    /**
     * Check if a skill with given name exists for partner.
     * Prevent duplicates by name.
     * 
     * @param partnerId Partner's user ID
     * @param skillName Skill name to check
     * @return true if skill exists
     */
    boolean existsByNameAndPartnerId(Integer partnerId, String skillName);

    /**
     * Count skills created by partner.
     * @param partnerId Partner's user ID
     * @return Total skill count
     */
    long countByPartnerId(Integer partnerId);
}
