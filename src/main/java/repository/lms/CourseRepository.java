package repository.lms;

import entities.Course;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CourseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(CourseRepository.class);

    public List<Course> findAll() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Course ORDER BY createdAt DESC", Course.class).list();
        } catch (Exception e) { LOG.error("findAll failed", e); return Collections.emptyList(); }
    }

    public Optional<Course> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(Course.class, id));
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public Course save(Course c) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(c); tx.commit(); return c;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException("Failed to save course", e); }
    }

    public Course update(Course c) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Course merged = session.merge(c); tx.commit(); return merged;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException("Failed to update course", e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Course c = session.get(Course.class, id); if (c != null) session.remove(c); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException("Failed to delete course", e); }
    }

    public long count() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("SELECT COUNT(c) FROM Course c", Long.class).uniqueResult();
        } catch (Exception e) { LOG.error("count failed", e); return 0; }
    }
}
