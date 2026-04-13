package repository.lms;

import entities.Program;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ProgramRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramRepository.class);

    public List<Program> findAll() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Program ORDER BY createdAt DESC", Program.class).list();
        } catch (Exception e) {
            LOG.error("findAll failed", e);
            return Collections.emptyList();
        }
    }

    public Optional<Program> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(Program.class, id));
        } catch (Exception e) {
            LOG.error("findById failed", e);
            return Optional.empty();
        }
    }

    public List<Program> findByPublished(boolean published) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            Query<Program> q = session.createQuery("FROM Program WHERE published = :p ORDER BY createdAt DESC", Program.class);
            q.setParameter("p", published ? (byte) 1 : (byte) 0);
            return q.list();
        } catch (Exception e) {
            LOG.error("findByPublished failed", e);
            return Collections.emptyList();
        }
    }

    public List<Program> searchByName(String name) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            Query<Program> q = session.createQuery("FROM Program WHERE LOWER(name) LIKE :n ORDER BY createdAt DESC", Program.class);
            q.setParameter("n", "%" + name.toLowerCase() + "%");
            return q.list();
        } catch (Exception e) {
            LOG.error("searchByName failed", e);
            return Collections.emptyList();
        }
    }

    public Program save(Program p) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            session.persist(p);
            tx.commit();
            return p;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("save failed", e);
            throw new RuntimeException("Failed to save program", e);
        }
    }

    public Program update(Program p) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            Program merged = session.merge(p);
            tx.commit();
            return merged;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("update failed", e);
            throw new RuntimeException("Failed to update program", e);
        }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            Program p = session.get(Program.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            LOG.error("delete failed", e);
            throw new RuntimeException("Failed to delete program", e);
        }
    }

    public long count() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("SELECT COUNT(p) FROM Program p", Long.class).uniqueResult();
        } catch (Exception e) {
            LOG.error("count failed", e);
            return 0;
        }
    }
}
