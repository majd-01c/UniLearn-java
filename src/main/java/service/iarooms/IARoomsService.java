package service.iarooms;

import entities.iarooms.Room;
import entities.iarooms.RoomBooking;
import entities.iarooms.RoomConflict;
import entities.iarooms.TimetableUpload;
import repository.iarooms.RoomBookingRepository;
import repository.iarooms.RoomConflictRepository;
import repository.iarooms.RoomRepository;
import repository.iarooms.TimetableUploadRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class IARoomsService {

    private static final List<Slot> STANDARD_SLOTS = List.of(
            new Slot("09:00 - 10:30", LocalTime.of(9, 0), LocalTime.of(10, 30)),
            new Slot("10:45 - 12:15", LocalTime.of(10, 45), LocalTime.of(12, 15)),
            new Slot("13:30 - 15:00", LocalTime.of(13, 30), LocalTime.of(15, 0)),
            new Slot("15:15 - 16:45", LocalTime.of(15, 15), LocalTime.of(16, 45))
    );

    private final TimetableUploadRepository uploadRepository = new TimetableUploadRepository();
    private final RoomRepository roomRepository = new RoomRepository();
    private final RoomBookingRepository bookingRepository = new RoomBookingRepository();
    private final RoomConflictRepository conflictRepository = new RoomConflictRepository();

    public Optional<TimetableUpload> findLatestUpload() {
        return uploadRepository.findLatest();
    }

    public List<TimetableUpload> findUploads() {
        return uploadRepository.findOrdered();
    }

    public List<Slot> standardSlots() {
        return STANDARD_SLOTS;
    }

    public LocalDate defaultSearchDate(TimetableUpload upload) {
        if (upload != null && upload.getWeekStart() != null) {
            return upload.getWeekStart();
        }
        return LocalDate.now();
    }

    public List<Room> findObservedRooms(TimetableUpload upload, String query) {
        List<Room> rooms = roomRepository.findObservedForUpload(upload);
        String normalizedQuery = normalize(query);
        if (normalizedQuery == null) {
            return rooms;
        }

        return rooms.stream()
                .filter(room -> contains(room.getName(), normalizedQuery)
                        || contains(room.getBuilding(), normalizedQuery)
                        || contains(room.getType(), normalizedQuery))
                .toList();
    }

    public List<RoomBooking> findBookings(Room room, TimetableUpload upload) {
        return bookingRepository.findByRoomAndUploadOrdered(room, upload);
    }

    public Map<Integer, Long> countBookingsByRoom(TimetableUpload upload) {
        return bookingRepository.findByUploadOrdered(upload).stream()
                .filter(booking -> booking.getRoom() != null && booking.getRoom().getId() != null)
                .collect(Collectors.groupingBy(
                        booking -> booking.getRoom().getId(),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    public AvailabilityResult findAvailability(
            TimetableUpload upload,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String roomFilter,
            String buildingFilter
    ) {
        if (upload == null || date == null || startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            return AvailabilityResult.empty(date, startTime, endTime);
        }

        List<Room> rooms = roomRepository.findObservedForUpload(upload).stream()
                .filter(room -> roomMatchesFilters(room, roomFilter, buildingFilter))
                .sorted(Comparator.comparing(room -> valueOrDefault(room.getName(), "")))
                .toList();

        List<RoomBooking> overlapping = bookingRepository.findOverlappingBookings(upload, date, startTime, endTime).stream()
                .filter(booking -> booking.getRoom() != null && roomMatchesFilters(booking.getRoom(), roomFilter, buildingFilter))
                .toList();

        Set<Integer> occupiedIds = new LinkedHashSet<>();
        List<OccupiedRoom> occupiedRooms = new ArrayList<>();
        for (RoomBooking booking : overlapping) {
            Room room = booking.getRoom();
            if (room == null || room.getId() == null || occupiedIds.contains(room.getId())) {
                continue;
            }
            occupiedIds.add(room.getId());
            occupiedRooms.add(new OccupiedRoom(
                    valueOrDefault(room.getName(), "N/A"),
                    valueOrDefault(booking.getGroupName(), "N/A"),
                    valueOrDefault(booking.getCourseName(), "N/A"),
                    booking.getBookingDate(),
                    booking.getStartTime(),
                    booking.getEndTime(),
                    booking.getSourcePage()));
        }

        List<Room> emptyRooms = rooms.stream()
                .filter(room -> room.getId() == null || !occupiedIds.contains(room.getId()))
                .toList();

        return new AvailabilityResult(date, startTime, endTime, emptyRooms, occupiedRooms);
    }

    public List<RoomConflict> findConflicts(TimetableUpload upload) {
        return conflictRepository.findOrdered(upload);
    }

    private boolean roomMatchesFilters(Room room, String roomFilter, String buildingFilter) {
        String normalizedRoomFilter = normalize(roomFilter);
        String normalizedBuildingFilter = normalize(buildingFilter);

        String roomName = valueOrDefault(room.getName(), "");
        String building = valueOrDefault(room.getBuilding(), "");

        if (normalizedRoomFilter != null && !roomName.toUpperCase(Locale.ROOT).contains(normalizedRoomFilter)) {
            return false;
        }

        if (normalizedBuildingFilter != null) {
            String upperRoomName = roomName.toUpperCase(Locale.ROOT);
            String upperBuilding = building.toUpperCase(Locale.ROOT);
            return upperRoomName.startsWith(normalizedBuildingFilter) || upperBuilding.startsWith(normalizedBuildingFilter);
        }

        return true;
    }

    private boolean contains(String value, String normalizedNeedle) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(normalizedNeedle);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public record Slot(String label, LocalTime startTime, LocalTime endTime) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record OccupiedRoom(
            String room,
            String groupName,
            String courseName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int sourcePage
    ) {
    }

    public static final class AvailabilityResult {
        private final LocalDate date;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final List<Room> emptyRooms;
        private final List<OccupiedRoom> occupiedRooms;

        public AvailabilityResult(LocalDate date, LocalTime startTime, LocalTime endTime, List<Room> emptyRooms, List<OccupiedRoom> occupiedRooms) {
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.emptyRooms = emptyRooms == null ? List.of() : List.copyOf(emptyRooms);
            this.occupiedRooms = occupiedRooms == null ? List.of() : List.copyOf(occupiedRooms);
        }

        public static AvailabilityResult empty(LocalDate date, LocalTime startTime, LocalTime endTime) {
            return new AvailabilityResult(date, startTime, endTime, List.of(), List.of());
        }

        public LocalDate getDate() {
            return date;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }

        public List<Room> getEmptyRooms() {
            return emptyRooms;
        }

        public List<OccupiedRoom> getOccupiedRooms() {
            return occupiedRooms;
        }
    }
}
