package repository.iarooms;

import entities.iarooms.Room;
import entities.iarooms.TimetableUpload;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RoomRepository {

    private static final Logger LOG = LoggerFactory.getLogger(RoomRepository.class);

    public Optional<Room> findById(Integer id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return Optional.ofNullable(session.get(Room.class, id));
        } catch (Exception exception) {
            LOG.error("findById failed for room {}", id, exception);
            return Optional.empty();
        }
    }

    public List<Room> findAll() {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("FROM Room r ORDER BY r.name ASC", Room.class).list();
        } catch (Exception exception) {
            LOG.error("findAll failed", exception);
            return Collections.emptyList();
        }
    }

    public List<Room> findObservedForUpload(TimetableUpload upload) {
        if (upload == null || upload.getId() == null) {
            return Collections.emptyList();
        }

        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("""
                    SELECT DISTINCT r
                    FROM Room r
                    JOIN r.roomBookings b
                    WHERE b.timetableUpload.id = :uploadId
                    ORDER BY r.name ASC
                    """, Room.class)
                    .setParameter("uploadId", upload.getId())
                    .list();
        } catch (Exception exception) {
            LOG.error("findObservedForUpload failed for upload {}", upload.getId(), exception);
            return Collections.emptyList();
        }
    }
}
