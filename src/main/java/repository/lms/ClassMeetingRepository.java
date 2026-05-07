package repository.lms;

import entities.ClassMeeting;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClassMeetingRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClassMeetingRepository.class);
    private static final String FETCH_GRAPH = """
            SELECT m FROM ClassMeeting m
            JOIN FETCH m.teacherClasse tc
            JOIN FETCH tc.user
            JOIN FETCH tc.classe cl
            LEFT JOIN FETCH cl.program
            """;

    public Optional<ClassMeeting> findById(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + " WHERE m.id = :id", ClassMeeting.class)
                    .setParameter("id", id)
                    .uniqueResultOptional();
        } catch (Exception e) {
            LOG.error("findById failed", e);
            return Optional.empty();
        }
    }

    public List<ClassMeeting> findByTeacherClasse(Integer teacherClasseId) {
        if (teacherClasseId == null) {
            return Collections.emptyList();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + """
                            WHERE tc.id = :teacherClasseId
                            ORDER BY m.createdAt DESC
                            """, ClassMeeting.class)
                    .setParameter("teacherClasseId", teacherClasseId)
                    .list();
        } catch (Exception e) {
            LOG.error("findByTeacherClasse failed", e);
            return Collections.emptyList();
        }
    }

    public List<ClassMeeting> findLiveMeetingsForClasse(Integer classeId) {
        if (classeId == null) {
            return Collections.emptyList();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + """
                            WHERE cl.id = :classeId
                            AND m.status = :status
                            ORDER BY m.startedAt DESC
                            """, ClassMeeting.class)
                    .setParameter("classeId", classeId)
                    .setParameter("status", ClassMeeting.STATUS_LIVE)
                    .list();
        } catch (Exception e) {
            LOG.error("findLiveMeetingsForClasse failed", e);
            return Collections.emptyList();
        }
    }

    public List<ClassMeeting> findUpcomingMeetingsForClasse(Integer classeId) {
        if (classeId == null) {
            return Collections.emptyList();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + """
                            WHERE cl.id = :classeId
                            AND (m.status = :scheduled OR m.status = :live)
                            ORDER BY m.scheduledAt ASC, m.createdAt DESC
                            """, ClassMeeting.class)
                    .setParameter("classeId", classeId)
                    .setParameter("scheduled", ClassMeeting.STATUS_SCHEDULED)
                    .setParameter("live", ClassMeeting.STATUS_LIVE)
                    .list();
        } catch (Exception e) {
            LOG.error("findUpcomingMeetingsForClasse failed", e);
            return Collections.emptyList();
        }
    }

    public Optional<ClassMeeting> findByRoomCode(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return Optional.empty();
        }
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery(FETCH_GRAPH + " WHERE m.roomCode = :roomCode", ClassMeeting.class)
                    .setParameter("roomCode", roomCode)
                    .uniqueResultOptional();
        } catch (Exception e) {
            LOG.error("findByRoomCode failed", e);
            return Optional.empty();
        }
    }

    public ClassMeeting save(ClassMeeting meeting) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            session.persist(meeting);
            tx.commit();
            return meeting;
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            LOG.error("save failed", e);
            throw new RuntimeException("Failed to save meeting", e);
        }
    }

    public ClassMeeting update(ClassMeeting meeting) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            ClassMeeting merged = session.merge(meeting);
            tx.commit();
            return merged;
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            LOG.error("update failed", e);
            throw new RuntimeException("Failed to update meeting", e);
        }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            ClassMeeting meeting = session.get(ClassMeeting.class, id);
            if (meeting != null) {
                session.remove(meeting);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            LOG.error("delete failed", e);
            throw new RuntimeException("Failed to delete meeting", e);
        }
    }
}
