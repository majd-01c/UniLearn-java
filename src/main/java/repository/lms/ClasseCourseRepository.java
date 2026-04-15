package repository.lms;

import entities.ClasseCourse;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClasseCourseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClasseCourseRepository.class);

    public List<ClasseCourse> findByClasseModuleId(Integer classeModuleId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ClasseCourse cc LEFT JOIN FETCH cc.course WHERE cc.classeModule.id = :cmid", ClasseCourse.class)
                    .setParameter("cmid", classeModuleId).list();
        } catch (Exception e) { LOG.error("findByClasseModuleId failed", e); return Collections.emptyList(); }
    }

    public List<ClasseCourse> findVisibleByClasseModuleId(Integer classeModuleId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ClasseCourse cc LEFT JOIN FETCH cc.course WHERE cc.classeModule.id = :cmid AND cc.isHidden = 0", ClasseCourse.class)
                    .setParameter("cmid", classeModuleId).list();
        } catch (Exception e) { LOG.error("findVisibleByClasseModuleId failed", e); return Collections.emptyList(); }
    }

    public Optional<ClasseCourse> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(ClasseCourse.class, id));
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public ClasseCourse save(ClasseCourse cc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(cc); tx.commit(); return cc;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException(e); }
    }

    public ClasseCourse update(ClasseCourse cc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); ClasseCourse merged = session.merge(cc); tx.commit(); return merged;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException(e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); ClasseCourse cc = session.get(ClasseCourse.class, id); if (cc != null) session.remove(cc); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException(e); }
    }
}
