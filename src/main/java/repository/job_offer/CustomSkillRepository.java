package repository.job_offer;

import entities.CustomSkill;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.*;

/**
 * Implementation of ICustomSkillRepository using Hibernate ORM with Criteria API.
 * 
 * Manages partner-defined custom skills for job offer templates.
 * Partners can create reusable skill definitions to use across multiple offers.
 */
public class CustomSkillRepository implements ICustomSkillRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomSkillRepository.class);
    private static final String SORT_FIELD_CREATED_AT = "createdAt";
    private static final String SORT_FIELD_ID = "id";

    // ─────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────

    @Override
    public Optional<CustomSkill> findById(Integer skillId) {
        if (skillId == null || skillId <= 0) {
            return Optional.empty();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            return Optional.ofNullable(session.get(CustomSkill.class, skillId));
        } catch (Exception exception) {
            LOGGER.error("Failed to find custom skill by id {}", skillId, exception);
            throw new IllegalStateException("Unable to query custom skill by id", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public CustomSkill save(CustomSkill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("CustomSkill cannot be null");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            CustomSkill merged = session.merge(skill);
            session.flush();
            transaction.commit();
            return merged;
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Failed to save custom skill", exception);
            throw new IllegalStateException("Unable to save custom skill", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public void delete(Integer skillId) {
        if (skillId == null || skillId <= 0) {
            throw new IllegalArgumentException("Invalid skill ID");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            CustomSkill skill = session.get(CustomSkill.class, skillId);
            if (skill != null) {
                session.remove(skill);
            }
            transaction.commit();
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Failed to delete custom skill {}", skillId, exception);
            throw new IllegalStateException("Unable to delete custom skill", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Partner-Specific Queries
    // ─────────────────────────────────────────────────────────

    @Override
    public List<CustomSkill> findByPartnerId(Integer partnerId) {
        if (partnerId == null || partnerId <= 0) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<CustomSkill> criteria = builder.createQuery(CustomSkill.class);
            Root<CustomSkill> root = criteria.from(CustomSkill.class);

            criteria.select(root)
                    .where(builder.equal(root.get("user").get("id"), partnerId))
                    .orderBy(builder.asc(root.get("name")));

            Query<CustomSkill> query = session.createQuery(criteria);
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find skills for partner {}", partnerId, exception);
            throw new IllegalStateException("Unable to query partner skills", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public List<CustomSkill> searchPartnerSkills(Integer partnerId, String searchText) {
        if (partnerId == null || partnerId <= 0) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<CustomSkill> criteria = builder.createQuery(CustomSkill.class);
            Root<CustomSkill> root = criteria.from(CustomSkill.class);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("user").get("id"), partnerId));

            if (searchText != null && !searchText.isBlank()) {
                String pattern = "%" + searchText.toLowerCase() + "%";
                Predicate nameMatch = builder.like(builder.lower(root.get("name")), pattern);
                Predicate descMatch = builder.like(builder.lower(root.get("description")), pattern);
                predicates.add(builder.or(nameMatch, descMatch));
            }

            criteria.select(root)
                    .where(predicates.toArray(new Predicate[0]))
                    .orderBy(builder.asc(root.get("name")));

            Query<CustomSkill> query = session.createQuery(criteria);
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to search partner skills", exception);
            throw new IllegalStateException("Unable to search partner skills", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Admin Queries
    // ─────────────────────────────────────────────────────────

    @Override
    public List<CustomSkill> findAll() {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<CustomSkill> criteria = builder.createQuery(CustomSkill.class);
            Root<CustomSkill> root = criteria.from(CustomSkill.class);

            criteria.select(root)
                    .orderBy(builder.asc(root.get("name")));

            Query<CustomSkill> query = session.createQuery(criteria);
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find all custom skills", exception);
            throw new IllegalStateException("Unable to query custom skills", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public List<CustomSkill> search(String searchText) {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<CustomSkill> criteria = builder.createQuery(CustomSkill.class);
            Root<CustomSkill> root = criteria.from(CustomSkill.class);

            if (searchText != null && !searchText.isBlank()) {
                String pattern = "%" + searchText.toLowerCase() + "%";
                Predicate nameMatch = builder.like(builder.lower(root.get("name")), pattern);
                Predicate descMatch = builder.like(builder.lower(root.get("description")), pattern);
                criteria.where(builder.or(nameMatch, descMatch));
            }

            criteria.orderBy(builder.asc(root.get("name")));

            Query<CustomSkill> query = session.createQuery(criteria);
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to search custom skills", exception);
            throw new IllegalStateException("Unable to search custom skills", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Utility Methods
    // ─────────────────────────────────────────────────────────

    @Override
    public boolean existsByNameAndPartnerId(Integer partnerId, String skillName) {
        if (partnerId == null || partnerId <= 0 || skillName == null || skillName.isBlank()) {
            return false;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<CustomSkill> root = criteria.from(CustomSkill.class);

            criteria.select(builder.count(root))
                    .where(
                        builder.and(
                            builder.equal(root.get("user").get("id"), partnerId),
                            builder.equal(root.get("name"), skillName)
                        )
                    );

            long count = session.createQuery(criteria).getSingleResult();
            return count > 0;
        } catch (Exception exception) {
            LOGGER.error("Failed to check skill existence", exception);
            return false;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public long countByPartnerId(Integer partnerId) {
        if (partnerId == null || partnerId <= 0) {
            return 0;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<CustomSkill> root = criteria.from(CustomSkill.class);

            criteria.select(builder.count(root))
                    .where(builder.equal(root.get("user").get("id"), partnerId));

            return session.createQuery(criteria).getSingleResult();
        } catch (Exception exception) {
            LOGGER.error("Failed to count partner skills", exception);
            return 0;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }
}
