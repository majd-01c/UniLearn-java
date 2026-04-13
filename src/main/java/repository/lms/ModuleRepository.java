package repository.lms;

import entities.Module;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ModuleRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleRepository.class);

    public List<Module> findAll() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Module ORDER BY createdAt DESC", Module.class).list();
        } catch (Exception e) { LOG.error("findAll failed", e); return Collections.emptyList(); }
    }

    public Optional<Module> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(Module.class, id));
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public Module save(Module m) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(m); tx.commit(); return m;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException("Failed to save module", e); }
    }

    public Module update(Module m) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Module merged = session.merge(m); tx.commit(); return merged;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("update failed", e); throw new RuntimeException("Failed to update module", e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); Module m = session.get(Module.class, id); if (m != null) session.remove(m); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException("Failed to delete module", e); }
    }

    public long count() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("SELECT COUNT(m) FROM Module m", Long.class).uniqueResult();
        } catch (Exception e) { LOG.error("count failed", e); return 0; }
    }
}
