package repository.lms;

import entities.CourseContenu;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CourseContenuRepository {

    private static final Logger LOG = LoggerFactory.getLogger(CourseContenuRepository.class);

    public List<CourseContenu> findByCourseIdOrdered(Integer courseId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM CourseContenu cc WHERE cc.course.id = :cid ORDER BY cc.position ASC", CourseContenu.class)
                    .setParameter("cid", courseId).list();
        } catch (Exception e) { LOG.error("findByCourseIdOrdered failed", e); return Collections.emptyList(); }
    }

    public Optional<CourseContenu> findByCourseAndContenu(Integer courseId, Integer contenuId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM CourseContenu cc WHERE cc.course.id = :cid AND cc.contenu.id = :xid", CourseContenu.class)
                    .setParameter("cid", courseId).setParameter("xid", contenuId).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findByCourseAndContenu failed", e); return Optional.empty(); }
    }

    public int getNextPosition(Integer courseId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            Integer max = session.createQuery("SELECT MAX(cc.position) FROM CourseContenu cc WHERE cc.course.id = :cid", Integer.class)
                    .setParameter("cid", courseId).uniqueResult();
            return (max != null ? max : 0) + 1;
        } catch (Exception e) { LOG.error("getNextPosition failed", e); return 1; }
    }

    public CourseContenu save(CourseContenu cc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(cc); tx.commit(); return cc;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException(e); }
    }

    public void updatePositions(List<CourseContenu> items) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            for (CourseContenu cc : items) { session.merge(cc); }
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("updatePositions failed", e); throw new RuntimeException(e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            CourseContenu cc = session.get(CourseContenu.class, id);
            if (cc != null) session.remove(cc);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException(e); }
    }
}
