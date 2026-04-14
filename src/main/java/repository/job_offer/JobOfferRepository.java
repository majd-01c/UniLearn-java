package repository.job_offer;

import dto.job_offer.JobOfferDto;
import dto.job_offer.JobOfferOptionDto;
import dto.job_offer.JobOfferRowDto;
import entities.job_offer.JobOffer;
import jakarta.persistence.criteria.*;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.sql.Timestamp;
import java.util.*;

/**
 * Implementation of IJobOfferRepository using Hibernate ORM with Criteria API.
 * 
 * Transaction Strategy:
 * - Each method manages its own session and transaction
 * - Save/delete operations are atomic (begin/commit/rollback)
 * - Queries are read-only (no explicit transaction needed)
 * - Exception safety: Finally block ensures session.close() always executes
 */
public class JobOfferRepository implements IJobOfferRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobOfferRepository.class);
    private static final String SORT_FIELD_CREATED_AT = "createdAt";
    private static final String SORT_FIELD_ID = "id";

    // ─────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────

    @Override
    public Optional<JobOffer> findById(Integer offerId) {
        if (offerId == null || offerId <= 0) {
            return Optional.empty();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            return Optional.ofNullable(session.get(JobOffer.class, offerId));
        } catch (Exception exception) {
            LOGGER.error("Failed to find job offer by id {}", offerId, exception);
            throw new IllegalStateException("Unable to query job offer by id", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public JobOffer save(JobOffer offer) {
        if (offer == null) {
            throw new IllegalArgumentException("JobOffer cannot be null");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            JobOffer merged = session.merge(offer);
            session.flush();
            transaction.commit();
            return merged;
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Failed to save job offer", exception);
            throw new IllegalStateException("Unable to save job offer", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public void delete(Integer offerId) {
        if (offerId == null || offerId <= 0) {
            throw new IllegalArgumentException("Invalid offer ID");
        }

        Session session = HibernateSessionFactory.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            JobOffer offer = session.get(JobOffer.class, offerId);
            if (offer != null) {
                session.remove(offer);
            }
            transaction.commit();
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.error("Failed to delete job offer {}", offerId, exception);
            throw new IllegalStateException("Unable to delete job offer", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Public Search (All Users - Students/Partners)
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobOffer> findActiveOffers(int offset, int limit, String type, String location, String searchText) {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<JobOffer> criteria = builder.createQuery(JobOffer.class);
            Root<JobOffer> root = criteria.from(JobOffer.class);
            Timestamp now = Timestamp.from(java.time.Instant.now());

            List<Predicate> predicates = new ArrayList<>();

            // Must be ACTIVE
            predicates.add(builder.equal(root.get("status"), "ACTIVE"));

            // Must already be published, or have no explicit publication date.
            predicates.add(builder.or(
                builder.isNull(root.get("publishedAt")),
                builder.lessThanOrEqualTo(root.get("publishedAt"), now)
            ));

            // Must not be expired.
            predicates.add(builder.or(
                builder.isNull(root.get("expiresAt")),
                builder.greaterThan(root.get("expiresAt"), now)
            ));

            // Optional: type filter
            if (type != null && !type.isBlank()) {
                predicates.add(builder.equal(root.get("type"), type));
            }

            // Optional: location filter
            if (location != null && !location.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("location")), 
                    "%" + location.toLowerCase() + "%"));
            }

            // Optional: search in title and description
            if (searchText != null && !searchText.isBlank()) {
                String searchPattern = "%" + searchText.toLowerCase() + "%";
                Predicate titleMatch = builder.like(builder.lower(root.get("title")), searchPattern);
                Predicate descMatch = builder.like(builder.lower(root.get("description")), searchPattern);
                predicates.add(builder.or(titleMatch, descMatch));
            }

            criteria.select(root)
                    .where(predicates.toArray(new Predicate[0]))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)), 
                            builder.desc(root.get(SORT_FIELD_ID)));

            Query<JobOffer> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find active offers", exception);
            throw new IllegalStateException("Unable to query active offers", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public long countActiveOffers(String type, String location, String searchText) {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<JobOffer> root = criteria.from(JobOffer.class);
            Timestamp now = Timestamp.from(java.time.Instant.now());

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), "ACTIVE"));
            predicates.add(builder.or(
                builder.isNull(root.get("publishedAt")),
                builder.lessThanOrEqualTo(root.get("publishedAt"), now)
            ));
            predicates.add(builder.or(
                builder.isNull(root.get("expiresAt")),
                builder.greaterThan(root.get("expiresAt"), now)
            ));

            if (type != null && !type.isBlank()) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (location != null && !location.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("location")), 
                    "%" + location.toLowerCase() + "%"));
            }
            if (searchText != null && !searchText.isBlank()) {
                String searchPattern = "%" + searchText.toLowerCase() + "%";
                Predicate titleMatch = builder.like(builder.lower(root.get("title")), searchPattern);
                Predicate descMatch = builder.like(builder.lower(root.get("description")), searchPattern);
                predicates.add(builder.or(titleMatch, descMatch));
            }

            criteria.select(builder.count(root))
                    .where(predicates.toArray(new Predicate[0]));

            return session.createQuery(criteria).getSingleResult();
        } catch (Exception exception) {
            LOGGER.error("Failed to count active offers", exception);
            return 0;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Partner-Specific Search (Own Offers)
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobOffer> findByPartnerId(Integer partnerId, int offset, int limit) {
        if (partnerId == null || partnerId <= 0) {
            return Collections.emptyList();
        }

        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<JobOffer> criteria = builder.createQuery(JobOffer.class);
            Root<JobOffer> root = criteria.from(JobOffer.class);

            criteria.select(root)
                    .where(builder.equal(root.get("user").get("id"), partnerId))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)), 
                            builder.desc(root.get(SORT_FIELD_ID)));

            Query<JobOffer> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find offers for partner {}", partnerId, exception);
            throw new IllegalStateException("Unable to query partner offers", exception);
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
            Root<JobOffer> root = criteria.from(JobOffer.class);

            criteria.select(builder.count(root))
                    .where(builder.equal(root.get("user").get("id"), partnerId));

            return session.createQuery(criteria).getSingleResult();
        } catch (Exception exception) {
            LOGGER.error("Failed to count partner offers", exception);
            return 0;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Admin Search (All Offers with All Statuses)
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobOffer> findAllForAdmin(int offset, int limit, String status, String type, 
                                         Integer partnerId, String searchText) {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<JobOffer> criteria = builder.createQuery(JobOffer.class);
            Root<JobOffer> root = criteria.from(JobOffer.class);

            List<Predicate> predicates = new ArrayList<>();

            // Optional: status filter
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }

            // Optional: type filter
            if (type != null && !type.isBlank()) {
                predicates.add(builder.equal(root.get("type"), type));
            }

            // Optional: partner filter
            if (partnerId != null && partnerId > 0) {
                predicates.add(builder.equal(root.get("user").get("id"), partnerId));
            }

            // Optional: text search
            if (searchText != null && !searchText.isBlank()) {
                String pattern = "%" + searchText.toLowerCase() + "%";
                Predicate titleMatch = builder.like(builder.lower(root.get("title")), pattern);
                Predicate descMatch = builder.like(builder.lower(root.get("description")), pattern);
                predicates.add(builder.or(titleMatch, descMatch));
            }

            criteria.select(root)
                    .where(predicates.toArray(new Predicate[0]))
                    .orderBy(builder.desc(root.get(SORT_FIELD_CREATED_AT)), 
                            builder.desc(root.get(SORT_FIELD_ID)));

            Query<JobOffer> query = session.createQuery(criteria);
            query.setFirstResult(Math.max(offset, 0));
            query.setMaxResults(Math.max(limit, 1));
            return query.getResultList();
        } catch (Exception exception) {
            LOGGER.error("Failed to find offers for admin", exception);
            throw new IllegalStateException("Unable to query offers for admin", exception);
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    @Override
    public long countAllForAdmin(String status, String type, Integer partnerId, String searchText) {
        Session session = HibernateSessionFactory.getSession();
        try {
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
            Root<JobOffer> root = criteria.from(JobOffer.class);

            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (partnerId != null && partnerId > 0) {
                predicates.add(builder.equal(root.get("user").get("id"), partnerId));
            }
            if (searchText != null && !searchText.isBlank()) {
                String pattern = "%" + searchText.toLowerCase() + "%";
                Predicate titleMatch = builder.like(builder.lower(root.get("title")), pattern);
                Predicate descMatch = builder.like(builder.lower(root.get("description")), pattern);
                predicates.add(builder.or(titleMatch, descMatch));
            }

            criteria.select(builder.count(root))
                    .where(predicates.toArray(new Predicate[0]));

            return session.createQuery(criteria).getSingleResult();
        } catch (Exception exception) {
            LOGGER.error("Failed to count offers for admin", exception);
            return 0;
        } finally {
            HibernateSessionFactory.closeSession();
        }
    }

    // ─────────────────────────────────────────────────────────
    // DTO Projections (for UI Layers)
    // ─────────────────────────────────────────────────────────

    @Override
    public List<JobOfferRowDto> findActiveOfferRows(int offset, int limit, String type, 
                                                    String location, String searchText) {
        List<JobOffer> offers = findActiveOffers(offset, limit, type, location, searchText);
        return offers.stream()
                .map(o -> new JobOfferRowDto(
                        o.getId(),
                        o.getTitle(),
                        o.getType(),
                        o.getStatus(),
                        o.getLocation(),
                        o.getUser() != null ? o.getUser().getEmail() : "Unknown",
                        o.getJobApplications() != null ? o.getJobApplications().size() : 0,
                        o.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public List<JobOfferOptionDto> findPartnerOfferOptions(Integer partnerId) {
        if (partnerId == null || partnerId <= 0) {
            return Collections.emptyList();
        }

        List<JobOffer> offers = findByPartnerId(partnerId, 0, 1000);
        return offers.stream()
                .map(o -> new JobOfferOptionDto(o.getId(), o.getTitle()))
                .toList();
    }

    @Override
    public Optional<JobOfferDto> findOfferDto(Integer offerId) {
        Optional<JobOffer> offer = findById(offerId);
        return offer.map(o -> new JobOfferDto(
                o.getId(),
                o.getUser() != null ? o.getUser().getId() : null,
                o.getUser() != null ? o.getUser().getEmail() : null,
                o.getTitle(),
                o.getType(),
                o.getStatus(),
                o.getLocation(),
                o.getDescription(),
                o.getRequirements(),
                o.getRequiredSkills(),
                o.getPreferredSkills(),
                o.getMinExperienceYears(),
                o.getMinEducation(),
                o.getRequiredLanguages(),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                o.getPublishedAt(),
                o.getExpiresAt(),
                o.getJobApplications() != null ? o.getJobApplications().size() : 0
        ));
    }
}
