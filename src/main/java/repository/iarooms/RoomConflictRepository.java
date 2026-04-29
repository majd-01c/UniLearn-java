package repository.iarooms;

import entities.iarooms.RoomConflict;
import entities.iarooms.TimetableUpload;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.util.Collections;
import java.util.List;

public class RoomConflictRepository {

    private static final Logger LOG = LoggerFactory.getLogger(RoomConflictRepository.class);

    public List<RoomConflict> findOrdered(TimetableUpload upload) {
        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            if (upload != null && upload.getId() != null) {
                return session.createQuery("""
                        SELECT c
                        FROM RoomConflict c
                        JOIN FETCH c.room r
                        WHERE c.timetableUpload.id = :uploadId
                        ORDER BY c.bookingDate DESC, r.name ASC, c.startTime ASC
                        """, RoomConflict.class)
                        .setParameter("uploadId", upload.getId())
                        .list();
            }

            return session.createQuery("""
                    SELECT c
                    FROM RoomConflict c
                    JOIN FETCH c.room r
                    ORDER BY c.bookingDate DESC, r.name ASC, c.startTime ASC
                    """, RoomConflict.class).list();
        } catch (Exception exception) {
            LOG.error("findOrdered failed", exception);
            return Collections.emptyList();
        }
    }
}
