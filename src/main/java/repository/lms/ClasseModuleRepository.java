package repository.lms;

import entities.ClasseModule;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ClasseModuleRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ClasseModuleRepository.class);

    public List<ClasseModule> findByClasseId(Integer classeId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ClasseModule cm LEFT JOIN FETCH cm.module WHERE cm.classe.id = :cid", ClasseModule.class)
                    .setParameter("cid", classeId).list();
        } catch (Exception e) { LOG.error("findByClasseId failed", e); return Collections.emptyList(); }
    }

    public Optional<ClasseModule> findByClasseAndModule(Integer classeId, Integer moduleId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ClasseModule cm LEFT JOIN FETCH cm.module WHERE cm.classe.id = :cid AND cm.module.id = :mid", ClasseModule.class)
                    .setParameter("cid", classeId).setParameter("mid", moduleId).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findByClasseAndModule failed", e); return Optional.empty(); }
    }

    public Optional<ClasseModule> findById(Integer id) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ClasseModule cm LEFT JOIN FETCH cm.module WHERE cm.id = :id", ClasseModule.class)
                    .setParameter("id", id).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findById failed", e); return Optional.empty(); }
    }

    public ClasseModule save(ClasseModule cm) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(cm); tx.commit(); return cm;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException(e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); ClasseModule cm = session.get(ClasseModule.class, id); if (cm != null) session.remove(cm); tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException(e); }
    }
}
