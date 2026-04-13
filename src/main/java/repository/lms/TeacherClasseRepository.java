package repository.lms;

import entities.TeacherClasse;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TeacherClasseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(TeacherClasseRepository.class);

    public List<TeacherClasse> findByClasseId(Integer classeId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM TeacherClasse tc LEFT JOIN FETCH tc.user LEFT JOIN FETCH tc.module LEFT JOIN FETCH tc.classe cl LEFT JOIN FETCH cl.program WHERE tc.classe.id = :cid", TeacherClasse.class)
                    .setParameter("cid", classeId).list();
        } catch (Exception e) { LOG.error("findByClasseId failed", e); return Collections.emptyList(); }
    }

    public List<TeacherClasse> findActiveByClasseId(Integer classeId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM TeacherClasse tc LEFT JOIN FETCH tc.user LEFT JOIN FETCH tc.module LEFT JOIN FETCH tc.classe cl LEFT JOIN FETCH cl.program WHERE tc.classe.id = :cid AND tc.isActive = 1", TeacherClasse.class)
                    .setParameter("cid", classeId).list();
        } catch (Exception e) { LOG.error("findActiveByClasseId failed", e); return Collections.emptyList(); }
    }

    public List<TeacherClasse> findActiveByTeacherId(Integer teacherId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM TeacherClasse tc LEFT JOIN FETCH tc.user LEFT JOIN FETCH tc.module LEFT JOIN FETCH tc.classe cl LEFT JOIN FETCH cl.program WHERE tc.user.id = :tid AND tc.isActive = 1", TeacherClasse.class)
                    .setParameter("tid", teacherId).list();
        } catch (Exception e) { LOG.error("findActiveByTeacherId failed", e); return Collections.emptyList(); }
    }

    public Optional<TeacherClasse> findByTeacherAndClasse(Integer teacherId, Integer classeId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM TeacherClasse tc LEFT JOIN FETCH tc.user LEFT JOIN FETCH tc.module LEFT JOIN FETCH tc.classe cl LEFT JOIN FETCH cl.program WHERE tc.user.id = :tid AND tc.classe.id = :cid", TeacherClasse.class)
                    .setParameter("tid", teacherId).setParameter("cid", classeId).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findByTeacherAndClasse failed", e); return Optional.empty(); }
    }

    public Optional<TeacherClasse> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM TeacherClasse tc LEFT JOIN FETCH tc.user LEFT JOIN FETCH tc.module LEFT JOIN FETCH tc.classe cl LEFT JOIN FETCH cl.program WHERE tc.id = :id", TeacherClasse.class)
                    .setParameter("id", id).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public boolean allActiveTeachersCreatedModule(Integer classeId) {
        List<TeacherClasse> active = findActiveByClasseId(classeId);
        return !active.isEmpty() && active.stream().allMatch(tc -> tc.getHasCreatedModule() == 1);
    }

    public TeacherClasse save(TeacherClasse tc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(tc); tx.commit(); return tc;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException(e); }
    }

    public TeacherClasse update(TeacherClasse tc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); TeacherClasse m = session.merge(tc); tx.commit(); return m;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException(e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); TeacherClasse tc = session.get(TeacherClasse.class, id); if (tc != null) session.remove(tc); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException(e); }
    }
}
