package repository.lms;

import entities.ProgramModule;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ProgramModuleRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramModuleRepository.class);

    public List<ProgramModule> findByProgramId(Integer programId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ProgramModule pm JOIN FETCH pm.module WHERE pm.program.id = :pid", ProgramModule.class)
                    .setParameter("pid", programId).list();
        } catch (Exception e) { LOG.error("findByProgramId failed", e); return Collections.emptyList(); }
    }

    public Optional<ProgramModule> findByProgramAndModule(Integer programId, Integer moduleId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ProgramModule pm WHERE pm.program.id = :pid AND pm.module.id = :mid", ProgramModule.class)
                    .setParameter("pid", programId).setParameter("mid", moduleId).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findByProgramAndModule failed", e); return Optional.empty(); }
    }

    public ProgramModule save(ProgramModule pm) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(pm); tx.commit(); return pm;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException("Failed to save program-module link", e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            ProgramModule pm = session.get(ProgramModule.class, id);
            if (pm != null) session.remove(pm);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException("Failed to delete program-module link", e); }
    }
}
