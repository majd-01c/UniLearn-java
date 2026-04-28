package entities.iarooms;

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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "room", uniqueConstraints = @UniqueConstraint(name = "UNIQ_FD8E0B0E5E237E06", columnNames = "name"))
public class Room implements Serializable {

    private Integer id;
    private String name;
    private String building;
    private Integer capacity;
    private String type;
    private LocalDateTime createdAt;
    private Set<RoomBooking> roomBookings = new HashSet<>(0);
    private Set<RoomConflict> roomConflicts = new HashSet<>(0);

    public Room() {
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

    @Column(name = "name", unique = true, nullable = false, length = 100)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column(name = "building", length = 100)
    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    @Column(name = "capacity")
    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    @Column(name = "type", length = 100)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Column(name = "created_at", nullable = false)
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "room")
    public Set<RoomBooking> getRoomBookings() {
        return roomBookings;
    }

    public void setRoomBookings(Set<RoomBooking> roomBookings) {
        this.roomBookings = roomBookings;
    }

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "room")
    public Set<RoomConflict> getRoomConflicts() {
        return roomConflicts;
    }

    public void setRoomConflicts(Set<RoomConflict> roomConflicts) {
        this.roomConflicts = roomConflicts;
    }
}
