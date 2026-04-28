package repository.iarooms;

import entities.iarooms.TimetableUpload;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TimetableUploadRepository {

    private static final Logger LOG = LoggerFactory.getLogger(TimetableUploadRepository.class);

    public Optional<TimetableUpload> findLatest() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM TimetableUpload u ORDER BY u.uploadedAt DESC", TimetableUpload.class)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        } catch (Exception exception) {
            LOG.error("findLatest failed", exception);
            return Optional.empty();
        }
    }

    public Optional<TimetableUpload> findById(Integer id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(TimetableUpload.class, id));
        } catch (Exception exception) {
            LOG.error("findById failed for timetable upload {}", id, exception);
            return Optional.empty();
        }
    }

    public List<TimetableUpload> findOrdered() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM TimetableUpload u ORDER BY u.uploadedAt DESC", TimetableUpload.class).list();
        } catch (Exception exception) {
            LOG.error("findOrdered failed", exception);
            return Collections.emptyList();
        }
    }
}
