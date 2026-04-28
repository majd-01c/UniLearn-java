package repository.iarooms;

import entities.iarooms.Room;
import entities.iarooms.RoomBooking;
import entities.iarooms.TimetableUpload;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HibernateSessionFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class RoomBookingRepository {

    private static final Logger LOG = LoggerFactory.getLogger(RoomBookingRepository.class);

    public List<RoomBooking> findByUploadOrdered(TimetableUpload upload) {
        if (upload == null || upload.getId() == null) {
            return Collections.emptyList();
        }

        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("""
                    SELECT b
                    FROM RoomBooking b
                    JOIN FETCH b.room r
                    WHERE b.timetableUpload.id = :uploadId
                    ORDER BY b.bookingDate ASC, b.startTime ASC, r.name ASC
                    """, RoomBooking.class)
                    .setParameter("uploadId", upload.getId())
                    .list();
        } catch (Exception exception) {
            LOG.error("findByUploadOrdered failed for upload {}", upload.getId(), exception);
            return Collections.emptyList();
        }
    }

    public List<RoomBooking> findByRoomAndUploadOrdered(Room room, TimetableUpload upload) {
        if (room == null || room.getId() == null || upload == null || upload.getId() == null) {
            return Collections.emptyList();
        }

        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("""
                    SELECT b
                    FROM RoomBooking b
                    JOIN FETCH b.room r
                    WHERE b.room.id = :roomId AND b.timetableUpload.id = :uploadId
                    ORDER BY b.bookingDate ASC, b.startTime ASC
                    """, RoomBooking.class)
                    .setParameter("roomId", room.getId())
                    .setParameter("uploadId", upload.getId())
                    .list();
        } catch (Exception exception) {
            LOG.error("findByRoomAndUploadOrdered failed for room {} upload {}", room.getId(), upload.getId(), exception);
            return Collections.emptyList();
        }
    }

    public List<RoomBooking> findByUploadAndDate(TimetableUpload upload, LocalDate date) {
        if (upload == null || upload.getId() == null || date == null) {
            return Collections.emptyList();
        }

        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("""
                    SELECT b
                    FROM RoomBooking b
                    JOIN FETCH b.room r
                    WHERE b.timetableUpload.id = :uploadId AND b.bookingDate = :date
                    ORDER BY b.startTime ASC, r.name ASC
                    """, RoomBooking.class)
                    .setParameter("uploadId", upload.getId())
                    .setParameter("date", date)
                    .list();
        } catch (Exception exception) {
            LOG.error("findByUploadAndDate failed for upload {} date {}", upload.getId(), date, exception);
            return Collections.emptyList();
        }
    }

    public List<RoomBooking> findOverlappingBookings(TimetableUpload upload, LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (upload == null || upload.getId() == null || date == null || startTime == null || endTime == null) {
            return Collections.emptyList();
        }

        try (Session session = HibernateSessionFactory.getInstance().openSession()) {
            return session.createQuery("""
                    SELECT b
                    FROM RoomBooking b
                    JOIN FETCH b.room r
                    WHERE b.timetableUpload.id = :uploadId
                      AND b.bookingDate = :date
                      AND b.startTime < :endTime
                      AND b.endTime > :startTime
                    ORDER BY r.name ASC, b.startTime ASC
                    """, RoomBooking.class)
                    .setParameter("uploadId", upload.getId())
                    .setParameter("date", date)
                    .setParameter("startTime", startTime)
                    .setParameter("endTime", endTime)
                    .list();
        } catch (Exception exception) {
            LOG.error("findOverlappingBookings failed for upload {} date {}", upload.getId(), date, exception);
            return Collections.emptyList();
        }
    }
}
