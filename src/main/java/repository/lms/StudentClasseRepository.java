package repository.lms;

import entities.StudentClasse;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class StudentClasseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(StudentClasseRepository.class);

    public List<StudentClasse> findByClasseId(Integer classeId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM StudentClasse sc LEFT JOIN FETCH sc.user LEFT JOIN FETCH sc.classe cl LEFT JOIN FETCH cl.program WHERE sc.classe.id = :cid", StudentClasse.class)
                    .setParameter("cid", classeId).list();
        } catch (Exception e) { LOG.error("findByClasseId failed", e); return Collections.emptyList(); }
    }

    public List<StudentClasse> findActiveByStudentId(Integer studentId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM StudentClasse sc LEFT JOIN FETCH sc.user LEFT JOIN FETCH sc.classe cl LEFT JOIN FETCH cl.program WHERE sc.user.id = :sid AND sc.isActive = 1", StudentClasse.class)
                    .setParameter("sid", studentId).list();
        } catch (Exception e) { LOG.error("findActiveByStudentId failed", e); return Collections.emptyList(); }
    }

    public List<StudentClasse> findByStudentId(Integer studentId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM StudentClasse sc LEFT JOIN FETCH sc.user LEFT JOIN FETCH sc.classe cl LEFT JOIN FETCH cl.program WHERE sc.user.id = :sid", StudentClasse.class)
                    .setParameter("sid", studentId).list();
        } catch (Exception e) { LOG.error("findByStudentId failed", e); return Collections.emptyList(); }
    }

    public Optional<StudentClasse> findByStudentAndClasse(Integer studentId, Integer classeId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM StudentClasse sc LEFT JOIN FETCH sc.user LEFT JOIN FETCH sc.classe cl LEFT JOIN FETCH cl.program WHERE sc.user.id = :sid AND sc.classe.id = :cid", StudentClasse.class)
                    .setParameter("sid", studentId).setParameter("cid", classeId).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findByStudentAndClasse failed", e); return Optional.empty(); }
    }

    public Optional<StudentClasse> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM StudentClasse sc LEFT JOIN FETCH sc.user LEFT JOIN FETCH sc.classe cl LEFT JOIN FETCH cl.program WHERE sc.id = :id", StudentClasse.class)
                    .setParameter("id", id).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public boolean hasActiveEnrollment(Integer studentId) {
        List<StudentClasse> active = findActiveByStudentId(studentId);
        return !active.isEmpty();
    }

    public StudentClasse save(StudentClasse sc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(sc); tx.commit(); return sc;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException(e); }
    }

    public StudentClasse update(StudentClasse sc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); StudentClasse m = session.merge(sc); tx.commit(); return m;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException(e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); StudentClasse sc = session.get(StudentClasse.class, id); if (sc != null) session.remove(sc); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException(e); }
    }
}
