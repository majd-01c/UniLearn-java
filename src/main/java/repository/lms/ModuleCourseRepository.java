package repository.lms;

import entities.ModuleCourse;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ModuleCourseRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleCourseRepository.class);

    public List<ModuleCourse> findByModuleId(Integer moduleId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ModuleCourse mc WHERE mc.module.id = :mid", ModuleCourse.class)
                    .setParameter("mid", moduleId).list();
        } catch (Exception e) { LOG.error("findByModuleId failed", e); return Collections.emptyList(); }
    }

    public Optional<ModuleCourse> findByModuleAndCourse(Integer moduleId, Integer courseId) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM ModuleCourse mc WHERE mc.module.id = :mid AND mc.course.id = :cid", ModuleCourse.class)
                    .setParameter("mid", moduleId).setParameter("cid", courseId).uniqueResultOptional();
        } catch (Exception e) { LOG.error("findByModuleAndCourse failed", e); return Optional.empty(); }
    }

    public ModuleCourse save(ModuleCourse mc) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction(); session.persist(mc); tx.commit(); return mc;
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("save failed", e); throw new RuntimeException(e); }
    }

    public void delete(Integer id) {
        Transaction tx = null;
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            tx = session.beginTransaction();
            ModuleCourse mc = session.get(ModuleCourse.class, id);
            if (mc != null) session.remove(mc);
            tx.commit();
        } catch (Exception e) { if (tx != null) tx.rollback(); LOG.error("delete failed", e); throw new RuntimeException(e); }
    }
}
