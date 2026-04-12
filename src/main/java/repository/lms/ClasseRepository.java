package repository.lms;

import entities.Classe;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClasseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClasseRepository.class);

    public List<Classe> findAll() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Classe c LEFT JOIN FETCH c.program ORDER BY c.startDate DESC", Classe.class).list();
        } catch (Exception e) { LOG.error("findAll failed", e); return Collections.emptyList(); }
    }

    public Optional<Classe> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Classe c LEFT JOIN FETCH c.program WHERE c.id = :id", Classe.class)
                    .setParameter("id", id).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public List<Classe> findByStatus(String status) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Classe c LEFT JOIN FETCH c.program WHERE c.status = :s ORDER BY c.startDate DESC", Classe.class)
                    .setParameter("s", status).list();
        } catch (Exception e) { LOG.error("findByStatus failed", e); return Collections.emptyList(); }
    }

    public Classe save(Classe c) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(c); tx.commit(); return c;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException("Failed to save classe", e); }
    }

    public Classe update(Classe c) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Classe merged = session.merge(c); tx.commit(); return merged;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException("Failed to update classe", e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Classe c = session.get(Classe.class, id); if (c != null) session.remove(c); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException("Failed to delete classe", e); }
    }

    public long countActiveStudents(Integer classeId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            Long result = session.createQuery(
                    "SELECT COUNT(sc) FROM StudentClasse sc WHERE sc.classe.id = :cid AND sc.isActive = 1", Long.class)
                    .setParameter("cid", classeId).uniqueResult();
            return result != null ? result : 0;
        } catch (Exception e) { LOG.error("countActiveStudents failed", e); return 0; }
    }

    public long count() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("SELECT COUNT(c) FROM Classe c", Long.class).uniqueResult();
        } catch (Exception e) { LOG.error("count failed", e); return 0; }
    }
}
