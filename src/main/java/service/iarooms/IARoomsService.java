package service.iarooms;

import entities.iarooms.Room;
import entities.iarooms.RoomBooking;
import entities.iarooms.RoomConflict;
import entities.iarooms.TimetableUpload;
import org.hibernate.Session;
import org.hibernate.Transaction;
import repository.iarooms.RoomBookingRepository;
import repository.iarooms.RoomConflictRepository;
import repository.iarooms.RoomRepository;
import repository.iarooms.TimetableUploadRepository;
import service.iarooms.EspritScrapeClient.BookingPayload;
import service.iarooms.EspritScrapeClient.ScrapePayload;
import util.HibernateSessionFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private final EspritScrapeClient scrapeClient = new EspritScrapeClient();

    public Optional<TimetableUpload> findLatestUpload() {
        return uploadRepository.findLatest();
    }

    public List<TimetableUpload> findUploads() {
        return uploadRepository.findOrdered();
    }

    public boolean deleteUpload(TimetableUpload upload) {
        if (upload == null || upload.getId() == null) {
            return false;
        }
        return uploadRepository.deleteById(upload.getId());
    }

    public String configuredBackendUrl() {
        return scrapeClient.configuredBackendUrl();
    }

    public ScrapeSummary scrapeEsprit(String backendUrl, String studentId, String password, double timeoutSeconds) {
        ScrapePayload payload = scrapeClient.scrape(backendUrl, studentId, password, timeoutSeconds);
        return persistScrapePayload(payload);
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

    private ScrapeSummary persistScrapePayload(ScrapePayload payload) {
        if (payload.bookings() == null || payload.bookings().isEmpty()) {
            String warningMessage = payload.warnings() == null ? "" : String.join(" ", payload.warnings());
            throw new IllegalStateException(("No bookings returned from Esprit scrape. " + warningMessage).trim());
        }

        Session session = null;
        Transaction transaction = null;
        try {
            session = HibernateSessionFactory.getInstance().openSession();
            transaction = session.beginTransaction();

            LocalDateTime now = LocalDateTime.now();
            TimetableUpload upload = new TimetableUpload();
            upload.setOriginalFilename("ESPRIT Emplois direct scrape");
            upload.setStoredFilename("esprit-scrape-" + UUID.randomUUID() + ".json");
            upload.setUploadedAt(now);
            upload.setIgnoredOnlineSessions(payload.ignoredOnlineSessions());
            upload.setTotalPages(payload.totalPagesRead());
            upload.setUsesMasterRoomList((byte) 0);

            session.persist(upload);

            Map<String, Room> roomsByName = new HashMap<>();
            List<RoomBooking> bookings = new ArrayList<>();
            LocalDate weekStart = null;
            LocalDate weekEnd = null;

            for (BookingPayload row : payload.bookings()) {
                String roomName = clean(row.roomName());
                if (roomName == null) {
                    continue;
                }

                LocalDate bookingDate = parseDate(row.date());
                LocalTime startTime = parseTime(row.startTime());
                LocalTime endTime = parseTime(row.endTime());
                if (bookingDate == null || startTime == null || endTime == null) {
                    continue;
                }

                weekStart = weekStart == null || bookingDate.isBefore(weekStart) ? bookingDate : weekStart;
                weekEnd = weekEnd == null || bookingDate.isAfter(weekEnd) ? bookingDate : weekEnd;

                String roomKey = normalize(roomName);
                Room room = roomsByName.get(roomKey);
                if (room == null) {
                    room = findOrCreateRoom(session, roomName, now);
                    roomsByName.put(roomKey, room);
                }

                RoomBooking booking = new RoomBooking();
                booking.setTimetableUpload(upload);
                booking.setRoom(room);
                booking.setGroupName(valueOrDefault(row.groupName(), "Unknown group"));
                booking.setCourseName(valueOrDefault(row.courseName(), "Unknown course"));
                booking.setBookingDate(bookingDate);
                booking.setDayName(valueOrDefault(row.dayName(), ""));
                booking.setStartTime(startTime);
                booking.setEndTime(endTime);
                booking.setSourcePage(Math.max(1, row.sourcePage()));
                booking.setCreatedAt(now);

                session.persist(booking);
                bookings.add(booking);
            }

            if (bookings.isEmpty()) {
                throw new IllegalStateException("No valid physical room bookings were returned from Esprit scrape.");
            }

            upload.setWeekStart(weekStart);
            upload.setWeekEnd(weekEnd);
            upload.setTotalBookings(bookings.size());
            upload.setTotalRooms(roomsByName.size());

            session.flush();

            List<RoomConflict> conflicts = detectConflicts(upload, bookings, now);
            for (RoomConflict conflict : conflicts) {
                session.persist(conflict);
            }

            Integer uploadId = upload.getId();
            transaction.commit();
            return new ScrapeSummary(uploadId, bookings.size(), roomsByName.size(), conflicts.size(),
                    safeWarnings(payload.warnings()), payload.sourceUrl());
        } catch (Exception exception) {
            rollbackQuietly(transaction, exception);
            throw new IllegalStateException(
                    "Failed to store Esprit scrape in the shared database. Cause: " + describeException(exception),
                    exception);
        } finally {
            closeQuietly(session);
        }
    }

    private List<String> safeWarnings(List<String> warnings) {
        return warnings == null ? List.of() : List.copyOf(warnings);
    }

    private void rollbackQuietly(Transaction transaction, Exception original) {
        if (transaction == null) {
            return;
        }

        try {
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } catch (Exception rollbackException) {
            original.addSuppressed(rollbackException);
        }
    }

    private void closeQuietly(Session session) {
        if (session == null) {
            return;
        }

        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // The scrape transaction is already complete or failed; closing noise should not mask that result.
        }
    }

    private String describeException(Exception exception) {
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }

        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                return cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
            cause = cause.getCause();
        }

        return exception.getClass().getSimpleName();
    }

    private Room findOrCreateRoom(Session session, String roomName, LocalDateTime now) {
        Room existing = session.createQuery("FROM Room r WHERE LOWER(r.name) = :name", Room.class)
                .setParameter("name", roomName.toLowerCase(Locale.ROOT))
                .setMaxResults(1)
                .uniqueResult();
        if (existing != null) {
            return existing;
        }

        Room room = new Room();
        room.setName(roomName);
        room.setCreatedAt(now);
        session.persist(room);
        return room;
    }

    private List<RoomConflict> detectConflicts(TimetableUpload upload, List<RoomBooking> bookings, LocalDateTime now) {
        Map<String, List<RoomBooking>> grouped = bookings.stream()
                .filter(booking -> booking.getRoom() != null && booking.getBookingDate() != null)
                .collect(Collectors.groupingBy(
                        booking -> booking.getRoom().getName() + "|" + booking.getBookingDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<RoomConflict> conflicts = new ArrayList<>();
        for (List<RoomBooking> roomBookings : grouped.values()) {
            roomBookings.sort(Comparator.comparing(RoomBooking::getStartTime));
            for (int i = 0; i < roomBookings.size(); i++) {
                for (int j = i + 1; j < roomBookings.size(); j++) {
                    RoomBooking first = roomBookings.get(i);
                    RoomBooking second = roomBookings.get(j);
                    if (!overlaps(first, second)) {
                        continue;
                    }
                    if (Objects.equals(first.getGroupName(), second.getGroupName())
                            && Objects.equals(first.getCourseName(), second.getCourseName())) {
                        continue;
                    }

                    LocalTime overlapStart = maxTime(first.getStartTime(), second.getStartTime());
                    LocalTime overlapEnd = minTime(first.getEndTime(), second.getEndTime());
                    String roomName = first.getRoom() == null ? "N/A" : first.getRoom().getName();
                    String date = first.getBookingDate() == null ? "N/A" : first.getBookingDate().toString();

                    RoomConflict conflict = new RoomConflict();
                    conflict.setTimetableUpload(upload);
                    conflict.setRoom(first.getRoom());
                    conflict.setBookingDate(first.getBookingDate());
                    conflict.setStartTime(overlapStart);
                    conflict.setEndTime(overlapEnd);
                    conflict.setBookingAGroupName(valueOrDefault(first.getGroupName(), "Unknown group"));
                    conflict.setBookingACourseName(valueOrDefault(first.getCourseName(), "Unknown course"));
                    conflict.setBookingASourcePage(first.getSourcePage());
                    conflict.setBookingBGroupName(valueOrDefault(second.getGroupName(), "Unknown group"));
                    conflict.setBookingBCourseName(valueOrDefault(second.getCourseName(), "Unknown course"));
                    conflict.setBookingBSourcePage(second.getSourcePage());
                    conflict.setBookingAStartTime(first.getStartTime());
                    conflict.setBookingAEndTime(first.getEndTime());
                    conflict.setBookingBStartTime(second.getStartTime());
                    conflict.setBookingBEndTime(second.getEndTime());
                    conflict.setCreatedAt(now);
                    conflict.setDescription("Room " + roomName + " is booked twice on " + date
                            + " between " + formatTime(overlapStart) + " and " + formatTime(overlapEnd) + ".");
                    conflicts.add(conflict);
                }
            }
        }
        return conflicts;
    }

    private boolean overlaps(RoomBooking first, RoomBooking second) {
        return first.getStartTime() != null
                && first.getEndTime() != null
                && second.getStartTime() != null
                && second.getEndTime() != null
                && first.getStartTime().isBefore(second.getEndTime())
                && first.getEndTime().isAfter(second.getStartTime());
    }

    private LocalTime maxTime(LocalTime left, LocalTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private LocalTime minTime(LocalTime left, LocalTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private LocalDate parseDate(String value) {
        try {
            return clean(value) == null ? null : LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalTime parseTime(String value) {
        try {
            String cleaned = clean(value);
            if (cleaned == null) {
                return null;
            }
            return LocalTime.parse(cleaned.length() > 5 ? cleaned.substring(0, 5) : cleaned);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatTime(LocalTime time) {
        return time == null ? "--:--" : time.toString();
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

    public record ScrapeSummary(
            Integer uploadId,
            int totalBookings,
            int totalRooms,
            int conflicts,
            List<String> warnings,
            String sourceUrl
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
