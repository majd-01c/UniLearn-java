package repository.job_offer;

import entities.job_offer.JobOfferMeeting;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class JobOfferMeetingRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JobOfferMeetingRepository.class);
    private static final String FETCH_GRAPH = """
            SELECT m FROM JobOfferMeeting m
            JOIN FETCH m.application app
            JOIN FETCH m.jobOffer offer
            JOIN FETCH m.student student
            JOIN FETCH m.partner partner
            """;

    public Optional<JobOfferMeeting> findById(Integer id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + " WHERE m.id = :id", JobOfferMeeting.class)
                    .setParameter("id", id)
                    .uniqueResultOptional();
        } catch (Exception exception) {
            LOG.error("findById failed", exception);
            return Optional.empty();
        }
    }

    public Optional<JobOfferMeeting> findByApplicationId(Integer applicationId) {
        if (applicationId == null || applicationId <= 0) {
            return Optional.empty();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + " WHERE app.id = :applicationId", JobOfferMeeting.class)
                    .setParameter("applicationId", applicationId)
                    .uniqueResultOptional();
        } catch (Exception exception) {
            LOG.error("findByApplicationId failed", exception);
            return Optional.empty();
        }
    }

    public List<JobOfferMeeting> findByApplicationIds(Collection<Integer> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return Collections.emptyList();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + """
                            WHERE app.id IN :applicationIds
                            ORDER BY m.scheduledAt ASC
                            """, JobOfferMeeting.class)
                    .setParameter("applicationIds", applicationIds)
                    .list();
        } catch (Exception exception) {
            LOG.error("findByApplicationIds failed", exception);
            return Collections.emptyList();
        }
    }

    public List<JobOfferMeeting> findByStudentId(Integer studentId) {
        if (studentId == null || studentId <= 0) {
            return Collections.emptyList();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + """
                            WHERE student.id = :studentId
                            ORDER BY m.scheduledAt ASC, m.createdAt DESC
                            """, JobOfferMeeting.class)
                    .setParameter("studentId", studentId)
                    .list();
        } catch (Exception exception) {
            LOG.error("findByStudentId failed", exception);
            return Collections.emptyList();
        }
    }

    public List<JobOfferMeeting> findByPartnerId(Integer partnerId) {
        if (partnerId == null || partnerId <= 0) {
            return Collections.emptyList();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + """
                            WHERE partner.id = :partnerId
                            ORDER BY m.scheduledAt ASC, m.createdAt DESC
                            """, JobOfferMeeting.class)
                    .setParameter("partnerId", partnerId)
                    .list();
        } catch (Exception exception) {
            LOG.error("findByPartnerId failed", exception);
            return Collections.emptyList();
        }
    }

    public List<JobOfferMeeting> findAll() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + " ORDER BY m.scheduledAt ASC, m.createdAt DESC", JobOfferMeeting.class)
                    .list();
        } catch (Exception exception) {
            LOG.error("findAll failed", exception);
            return Collections.emptyList();
        }
    }

    public JobOfferMeeting save(JobOfferMeeting meeting) {
        if (meeting == null) {
            throw new IllegalArgumentException("Meeting is required.");
        }

        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            JobOfferMeeting saved = session.merge(meeting);
            session.flush();
            tx.commit();
            return saved;
        } catch (Exception exception) {
            if (tx != null) {
                tx.rollback();
            }
            LOG.error("save failed", exception);
            throw new RuntimeException("Failed to save job offer meeting", exception);
        }
    }
}
