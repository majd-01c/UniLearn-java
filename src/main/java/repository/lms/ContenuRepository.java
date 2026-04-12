package repository.lms;

import entities.Contenu;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ContenuRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ContenuRepository.class);

    public List<Contenu> findAll() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Contenu ORDER BY createdAt DESC", Contenu.class).list();
        } catch (Exception e) { LOG.error("findAll failed", e); return Collections.emptyList(); }
    }

    public Optional<Contenu> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(Contenu.class, id));
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public List<Contenu> findByType(String type) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Contenu WHERE type = :t ORDER BY createdAt DESC", Contenu.class)
                    .setParameter("t", type).list();
        } catch (Exception e) { LOG.error("findByType failed", e); return Collections.emptyList(); }
    }

    public Contenu save(Contenu c) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(c); tx.commit(); return c;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException("Failed to save contenu", e); }
    }

    public Contenu update(Contenu c) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Contenu merged = session.merge(c); tx.commit(); return merged;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException("Failed to update contenu", e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Contenu c = session.get(Contenu.class, id); if (c != null) session.remove(c); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException("Failed to delete contenu", e); }
    }

    public long count() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("SELECT COUNT(c) FROM Contenu c", Long.class).uniqueResult();
        } catch (Exception e) { LOG.error("count failed", e); return 0; }
    }
}
