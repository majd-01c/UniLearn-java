package repository.job_offer;

import entities.job_offer.AtsAuditLog;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Collections;
import java.util.List;

/**
 * Repository for ATS audit log persistence.
 * Uses the same Hibernate session factory pattern as other repositories in this project.
 */
public class AtsAuditLogRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtsAuditLogRepository.class);

    /**
     * Persist a new audit log entry.
     */
    public AtsAuditLog save(AtsAuditLog entry) {
        if (entry == null) throw new IllegalArgumentException("Audit entry cannot be null");

        Session session = HibernateSessionFactory.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            AtsAuditLog merged = session.merge(entry);
            session.flush();
            tx.commit();
            return merged;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            LOGGER.error("Failed to save audit log entry", e);
            throw new IllegalStateException("Unable to save audit log entry", e);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    /**
     * Find all audit entries for a specific entity (e.g., a job application).
     *
     * @param entityType e.g. "JOB_APPLICATION"
     * @param entityId   the entity's DB id
     * @return chronological list of audit events (oldest first)
     */
    public List<AtsAuditLog> findByEntity(String entityType, int entityId) {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<AtsAuditLog> cq = cb.createQuery(AtsAuditLog.class);
            Root<AtsAuditLog> root = cq.from(AtsAuditLog.class);

            cq.select(root)
              .where(
                  cb.and(
                      cb.equal(root.get("entityType"), entityType),
                      cb.equal(root.get("entityId"), entityId)
                  )
              )
              .orderBy(cb.asc(root.get("createdAt")));

            return session.createQuery(cq).getResultList();
        } catch (Exception e) {
            LOGGER.error("Failed to find audit log for entity {} #{}", entityType, entityId, e);
            return Collections.emptyList();
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    /**
     * Find recent audit entries for an actor (user), limited by count.
     */
    public List<AtsAuditLog> findByActor(Integer actorId, int limit) {
        if (actorId == null) return Collections.emptyList();

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<AtsAuditLog> cq = cb.createQuery(AtsAuditLog.class);
            Root<AtsAuditLog> root = cq.from(AtsAuditLog.class);

            cq.select(root)
              .where(cb.equal(root.get("actorId"), actorId))
              .orderBy(cb.desc(root.get("createdAt")));

            Query<AtsAuditLog> q = session.createQuery(cq);
            q.setMaxResults(Math.max(1, limit));
            return q.getResultList();
        } catch (Exception e) {
            LOGGER.error("Failed to find audit log for actor {}", actorId, e);
            return Collections.emptyList();
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }
}
