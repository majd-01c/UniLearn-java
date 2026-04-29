package entities.iarooms;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import static jakarta.persistence.GenerationType.IDENTITY;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "timetable_upload", uniqueConstraints = @UniqueConstraint(name = "UNIQ_4E0AF0B9A7E3E1E2", columnNames = "stored_filename"))
public class TimetableUpload implements Serializable {

    private Integer id;
    private String originalFilename;
    private String storedFilename;
    private LocalDateTime uploadedAt;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private int totalPages;
    private int totalBookings;
    private int totalRooms;
    private int ignoredOnlineSessions;
    private byte usesMasterRoomList;
    private Set<RoomBooking> roomBookings = new HashSet<>(0);
    private Set<RoomConflict> roomConflicts = new HashSet<>(0);

    public TimetableUpload() {
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

    @Column(name = "original_filename", nullable = false, length = 255)
    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    @Column(name = "stored_filename", unique = true, nullable = false, length = 255)
    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    @Column(name = "uploaded_at", nullable = false)
    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    @Column(name = "week_start")
    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    @Column(name = "week_end")
    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public void setWeekEnd(LocalDate weekEnd) {
        this.weekEnd = weekEnd;
    }

    @Column(name = "total_pages", nullable = false)
    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    @Column(name = "total_bookings", nullable = false)
    public int getTotalBookings() {
        return totalBookings;
    }

    public void setTotalBookings(int totalBookings) {
        this.totalBookings = totalBookings;
    }

    @Column(name = "total_rooms", nullable = false)
    public int getTotalRooms() {
        return totalRooms;
    }

    public void setTotalRooms(int totalRooms) {
        this.totalRooms = totalRooms;
    }

    @Column(name = "ignored_online_sessions", nullable = false)
    public int getIgnoredOnlineSessions() {
        return ignoredOnlineSessions;
    }

    public void setIgnoredOnlineSessions(int ignoredOnlineSessions) {
        this.ignoredOnlineSessions = ignoredOnlineSessions;
    }

    @Column(name = "uses_master_room_list", nullable = false)
    public byte getUsesMasterRoomList() {
        return usesMasterRoomList;
    }

    public void setUsesMasterRoomList(byte usesMasterRoomList) {
        this.usesMasterRoomList = usesMasterRoomList;
    }

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "timetableUpload", cascade = CascadeType.PERSIST, orphanRemoval = true)
    public Set<RoomBooking> getRoomBookings() {
        return roomBookings;
    }

    public void setRoomBookings(Set<RoomBooking> roomBookings) {
        this.roomBookings = roomBookings;
    }

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "timetableUpload", cascade = CascadeType.PERSIST, orphanRemoval = true)
    public Set<RoomConflict> getRoomConflicts() {
        return roomConflicts;
    }

    public void setRoomConflicts(Set<RoomConflict> roomConflicts) {
        this.roomConflicts = roomConflicts;
    }
}
