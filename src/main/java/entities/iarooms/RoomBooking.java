package entities.iarooms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import static jakarta.persistence.GenerationType.IDENTITY;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "room_booking", indexes = {
        @Index(name = "IDX_A1D1F09D431D5234", columnList = "timetable_upload_id"),
        @Index(name = "IDX_A1D1F09DEF1EFAAA", columnList = "room_id"),
        @Index(name = "room_booking_date_idx", columnList = "booking_date")
})
public class RoomBooking implements Serializable {

    private Integer id;
    private TimetableUpload timetableUpload;
    private Room room;
    private String groupName;
    private String courseName;
    private LocalDate bookingDate;
    private String dayName;
    private LocalTime startTime;
    private LocalTime endTime;
    private int sourcePage;
    private LocalDateTime createdAt;

    public RoomBooking() {
    }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_upload_id", nullable = false, foreignKey = @ForeignKey(name = "FK_A1D1F09D431D5234"))
    public TimetableUpload getTimetableUpload() {
        return timetableUpload;
    }

    public void setTimetableUpload(TimetableUpload timetableUpload) {
        this.timetableUpload = timetableUpload;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "FK_A1D1F09DEF1EFAAA"))
    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    @Column(name = "group_name", nullable = false, length = 100)
    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Column(name = "course_name", nullable = false, length = 255)
    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    @Column(name = "booking_date", nullable = false)
    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    @Column(name = "day_name", nullable = false, length = 20)
    public String getDayName() {
        return dayName;
    }

    public void setDayName(String dayName) {
        this.dayName = dayName;
    }

    @Column(name = "start_time", nullable = false)
    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    @Column(name = "end_time", nullable = false)
    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    @Column(name = "source_page", nullable = false)
    public int getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(int sourcePage) {
        this.sourcePage = sourcePage;
    }

    @Column(name = "created_at", nullable = false)
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
