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
@Table(name = "room_conflict", indexes = {
        @Index(name = "IDX_3B1F66C9431D5234", columnList = "timetable_upload_id"),
        @Index(name = "IDX_3B1F66C9EF1EFAAA", columnList = "room_id"),
        @Index(name = "room_conflict_date_idx", columnList = "booking_date")
})
public class RoomConflict implements Serializable {

    private Integer id;
    private TimetableUpload timetableUpload;
    private Room room;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String description;
    private String bookingAGroupName;
    private String bookingACourseName;
    private int bookingASourcePage;
    private String bookingBGroupName;
    private String bookingBCourseName;
    private int bookingBSourcePage;
    private LocalTime bookingAStartTime;
    private LocalTime bookingAEndTime;
    private LocalTime bookingBStartTime;
    private LocalTime bookingBEndTime;
    private LocalDateTime createdAt;

    public RoomConflict() {
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
    @JoinColumn(name = "timetable_upload_id", nullable = false, foreignKey = @ForeignKey(name = "FK_3B1F66C9431D5234"))
    public TimetableUpload getTimetableUpload() {
        return timetableUpload;
    }

    public void setTimetableUpload(TimetableUpload timetableUpload) {
        this.timetableUpload = timetableUpload;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "FK_3B1F66C9EF1EFAAA"))
    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    @Column(name = "booking_date", nullable = false)
    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
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

    @Column(name = "description", nullable = false, columnDefinition = "LONGTEXT")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Column(name = "booking_a_group_name", nullable = false, length = 100)
    public String getBookingAGroupName() {
        return bookingAGroupName;
    }

    public void setBookingAGroupName(String bookingAGroupName) {
        this.bookingAGroupName = bookingAGroupName;
    }

    @Column(name = "booking_a_course_name", nullable = false, length = 255)
    public String getBookingACourseName() {
        return bookingACourseName;
    }

    public void setBookingACourseName(String bookingACourseName) {
        this.bookingACourseName = bookingACourseName;
    }

    @Column(name = "booking_a_source_page", nullable = false)
    public int getBookingASourcePage() {
        return bookingASourcePage;
    }

    public void setBookingASourcePage(int bookingASourcePage) {
        this.bookingASourcePage = bookingASourcePage;
    }

    @Column(name = "booking_b_group_name", nullable = false, length = 100)
    public String getBookingBGroupName() {
        return bookingBGroupName;
    }

    public void setBookingBGroupName(String bookingBGroupName) {
        this.bookingBGroupName = bookingBGroupName;
    }

    @Column(name = "booking_b_course_name", nullable = false, length = 255)
    public String getBookingBCourseName() {
        return bookingBCourseName;
    }

    public void setBookingBCourseName(String bookingBCourseName) {
        this.bookingBCourseName = bookingBCourseName;
    }

    @Column(name = "booking_b_source_page", nullable = false)
    public int getBookingBSourcePage() {
        return bookingBSourcePage;
    }

    public void setBookingBSourcePage(int bookingBSourcePage) {
        this.bookingBSourcePage = bookingBSourcePage;
    }

    @Column(name = "booking_a_start_time")
    public LocalTime getBookingAStartTime() {
        return bookingAStartTime;
    }

    public void setBookingAStartTime(LocalTime bookingAStartTime) {
        this.bookingAStartTime = bookingAStartTime;
    }

    @Column(name = "booking_a_end_time")
    public LocalTime getBookingAEndTime() {
        return bookingAEndTime;
    }

    public void setBookingAEndTime(LocalTime bookingAEndTime) {
        this.bookingAEndTime = bookingAEndTime;
    }

    @Column(name = "booking_b_start_time")
    public LocalTime getBookingBStartTime() {
        return bookingBStartTime;
    }

    public void setBookingBStartTime(LocalTime bookingBStartTime) {
        this.bookingBStartTime = bookingBStartTime;
    }

    @Column(name = "booking_b_end_time")
    public LocalTime getBookingBEndTime() {
        return bookingBEndTime;
    }

    public void setBookingBEndTime(LocalTime bookingBEndTime) {
        this.bookingBEndTime = bookingBEndTime;
    }

    @Column(name = "created_at", nullable = false)
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
