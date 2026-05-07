package repository.job_offer;

import dto.job_offer.ApplicationReviewRowDto;
import dto.job_offer.JobApplicationDto;
import dto.job_offer.JobApplicationRowDto;
import entities.job_offer.JobApplication;
import entities.job_offer.JobOffer;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.*;

/**
 * Implementation of IJobApplicationRepository using Hibernate ORM with Criteria API.
 * 
 * Critical Feature: hasStudentApplied() check prevents duplicate applications.
 * UNIQUE constraint enforced at DB level: (job_offer_id, student_id)
 * 
 * Transaction Strategy:
 * - Each method manages its own session and transaction
 * - Save/delete operations are atomic (begin/commit/rollback)
 * - Queries are read-only (no explicit transaction needed)
 * - Exception safety: Finally block ensures session.close() always executes
 */
public class JobApplicationRepository implements IJobApplicationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobApplicationRepository.class);
    private static final String SORT_FIELD_CREATED_AT = "createdAt";
    private static final String SORT_FIELD_ID = "id";

    // ─────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────

    @Override
    public Optional<JobApplication> findById(Integer applicationId) {
        if (applicationId == null || applicationId <= 0) {
            return Optional.empty();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            return session.createQuery("""
                            SELECT a FROM JobApplication a
                            JOIN FETCH a.user
                            JOIN FETCH a.jobOffer offer
                            JOIN FETCH offer.user
                            WHERE a.id = :applicationId
                            """, JobApplication.class)
                    .setParameter("applicationId", applicationId)
                    .uniqueResultOptional();
        } catch (Exception exception) {
            LOGGER.error("Failed to find application by id {}", applicationId, exception);
            throw new IllegalStateException("Unable to query application by id", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public JobApplication save(JobApplication application) {
        if (application == null) {
            throw new IllegalArgumentException("JobApplication cannot be null");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            JobApplication merged = session.merge(application);
            session.flush();
            transaction.commit();
            return merged;
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Failed to save job application", exception);
            throw new IllegalStateException("Unable to save job application", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public void delete(Integer applicationId) {
        if (applicationId == null || applicationId <= 0) {
            throw new IllegalArgumentException("Invalid application ID");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            JobApplication app = session.get(JobApplication.class, applicationId);
            if (app != null) {
                session.remove(app);
            }
            transaction.commit();
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Failed to delete job application {}", applicationId, exception);
            throw new IllegalStateException("Unable to delete job application", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Duplicate Prevention (Critical for Business Rules)
    // ─────────────────────────────────────────────────────────

    @Override
    public boolean hasStudentApplied(Integer studentId, Integer offerId) {
        if (studentId == null || studentId <= 0 || offerId == null || offerId <= 0) {
            return false;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);

            criteria.select(builder.count(root))
                    .where(
                        builder.and(
                            builder.equal(root.get("user").get("id"), studentId),
                            builder.equal(root.get("jobOffer").get("id"), offerId)
                        )
                    );

            long count = session.createQuery(criteria).getSingleResult();
            return count > 0;
        } catch (Exception exception) {
            LOGGER.error("Failed to check student application", exception);
            return false;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public Set<Integer> getStudentAppliedOfferIds(Integer studentId) {
        if (studentId == null || studentId <= 0) {
            return Collections.emptySet();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Integer> criteria = builder.createQuery(Integer.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);

            criteria.select(root.get("jobOffer").get("id"))
                    .where(builder.equal(root.get("user").get("id"), studentId))
                    .distinct(true);

            List<Integer> offerIds = session.createQuery(criteria).getResultList();
            return new HashSet<>(offerIds);
        } catch (Exception exception) {
            LOGGER.error("Failed to get student applied offer IDs", exception);
            return Collections.emptySet();
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public Set<Integer> getAppliedOfferIds(Integer studentId) {
        return getStudentAppliedOfferIds(studentId);
    }

    // ─────────────────────────────────────────────────────────
    // Student Application History
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobApplication> findByStudentId(Integer studentId, int offset, int limit) {
        if (studentId == null || studentId <= 0) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<JobApplication> criteria = builder.createQuery(JobApplication.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);

            criteria.select(root)
                    .where(builder.equal(root.get("user").get("id"), studentId))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)), 
                            builder.desc(root.get(SORT_FIELD_ID)));

            Query<JobApplication> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find applications for student {}", studentId, exception);
            throw new IllegalStateException("Unable to query student applications", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public long countByStudentId(Integer studentId) {
        if (studentId == null || studentId <= 0) {
            return 0;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);

            criteria.select(builder.count(root))
                    .where(builder.equal(root.get("user").get("id"), studentId));

            return session.createQuery(criteria).getSingleResult();
        } catch (Exception exception) {
            LOGGER.error("Failed to count student applications", exception);
            return 0;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Partner Review Queue (Applications to Review)
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobApplication> findApplicationsForPartnerReview(Integer partnerId, int offset, int limit) {
        if (partnerId == null || partnerId <= 0) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<JobApplication> criteria = builder.createQuery(JobApplication.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);
            Join<JobApplication, JobOffer> offerJoin = root.join("jobOffer");

            criteria.select(root)
                    .where(
                        builder.and(
                            builder.equal(offerJoin.get("user").get("id"), partnerId),
                            builder.equal(root.get("status"), "SUBMITTED")
                        )
                    )
                    .orderBy(builder.asc(root.get(SORT_FIELD_CREATED_AT)));

            Query<JobApplication> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find applications for partner review", exception);
            throw new IllegalStateException("Unable to query partner review queue", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public long countApplicationsForPartnerReview(Integer partnerId) {
        if (partnerId == null || partnerId <= 0) {
            return 0;
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);
            Join<JobApplication, JobOffer> offerJoin = root.join("jobOffer");

            criteria.select(builder.count(root))
                    .where(
                        builder.and(
                            builder.equal(offerJoin.get("user").get("id"), partnerId),
                            builder.equal(root.get("status"), "SUBMITTED")
                        )
                    );

            return session.createQuery(criteria).getSingleResult();
        } catch (Exception exception) {
            LOGGER.error("Failed to count partner review queue", exception);
            return 0;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Admin Queries (Bulk Operations)
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobApplication> findByStatus(String status, int offset, int limit) {
        if (status == null || status.isBlank()) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<JobApplication> criteria = builder.createQuery(JobApplication.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);

            criteria.select(root)
                    .where(builder.equal(root.get("status"), status))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)));

            Query<JobApplication> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find applications by status", exception);
            throw new IllegalStateException("Unable to query applications", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public List<JobApplication> findByOfferId(Integer offerId, int offset, int limit) {
        if (offerId == null || offerId <= 0) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<JobApplication> criteria = builder.createQuery(JobApplication.class);
            Root<JobApplication> root = criteria.from(JobApplication.class);

            criteria.select(root)
                    .where(builder.equal(root.get("jobOffer").get("id"), offerId))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)));

            Query<JobApplication> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find applications by offer", exception);
            throw new IllegalStateException("Unable to query offer applications", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // DTO Projections (for UI Layers)
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobApplicationRowDto> findStudentApplicationRows(Integer studentId, int offset, int limit) {
        List<JobApplication> apps = findByStudentId(studentId, offset, limit);
        return apps.stream()
                .map(a -> new JobApplicationRowDto(
                        a.getId(),
                        a.getJobOffer() != null ? a.getJobOffer().getId() : null,
                        a.getJobOffer() != null ? a.getJobOffer().getTitle() : "Unknown",
                        a.getJobOffer() != null ? a.getJobOffer().getType() : null,
                        a.getStatus(),
                        a.getScore(),
                        a.getCreatedAt(),
                        a.getUpdatedAt()
                ))
                .toList();
    }

    @Override
    public List<ApplicationReviewRowDto> findPartnerReviewRows(Integer partnerId, int offset, int limit) {
        List<JobApplication> apps = findApplicationsForPartnerReview(partnerId, offset, limit);
        return apps.stream()
                .map(a -> new ApplicationReviewRowDto(
                        a.getId(),
                        a.getJobOffer() != null ? a.getJobOffer().getId() : null,
                        a.getJobOffer() != null ? a.getJobOffer().getTitle() : "Unknown",
                        a.getUser() != null ? a.getUser().getEmail() : "Unknown",
                        a.getStatus(),
                        a.getScore(),
                        a.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public Optional<JobApplicationDto> findApplicationDto(Integer applicationId) {
        Optional<JobApplication> app = findById(applicationId);
        return app.map(a -> new JobApplicationDto(
                a.getId(),
                a.getJobOffer() != null ? a.getJobOffer().getId() : null,
                a.getJobOffer() != null ? a.getJobOffer().getTitle() : null,
                a.getUser() != null ? a.getUser().getId() : null,
                a.getUser() != null ? a.getUser().getEmail() : null,
                a.getMessage(),
                a.getCvFileName(),
                a.getStatus(),
                a.getScore(),
                a.getScoreBreakdown(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getScoredAt(),
                a.getExtractedData(),
                a.getStatusNotified(),
                a.getStatusNotifiedAt(),
                a.getStatusMessage()
        ));
    }
}
