package repository.lms;

import entities.ClasseContenu;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClasseContenuRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClasseContenuRepository.class);

    public List<ClasseContenu> findByClasseCourseId(Integer classeCourseId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ClasseContenu cc LEFT JOIN FETCH cc.contenu WHERE cc.classeCourse.id = :ccid", ClasseContenu.class)
                    .setParameter("ccid", classeCourseId).list();
        } catch (Exception e) { LOG.error("findByClasseCourseId failed", e); return Collections.emptyList(); }
    }

    public List<ClasseContenu> findVisibleByClasseCourseId(Integer classeCourseId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ClasseContenu cc LEFT JOIN FETCH cc.contenu WHERE cc.classeCourse.id = :ccid AND cc.isHidden = 0", ClasseContenu.class)
                    .setParameter("ccid", classeCourseId).list();
        } catch (Exception e) { LOG.error("findVisibleByClasseCourseId failed", e); return Collections.emptyList(); }
    }

    public Optional<ClasseContenu> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(ClasseContenu.class, id));
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public ClasseContenu save(ClasseContenu cc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(cc); tx.commit(); return cc;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException(e); }
    }

    public ClasseContenu update(ClasseContenu cc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); ClasseContenu m = session.merge(cc); tx.commit(); return m;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException(e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); ClasseContenu cc = session.get(ClasseContenu.class, id); if (cc != null) session.remove(cc); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException(e); }
    }
}
