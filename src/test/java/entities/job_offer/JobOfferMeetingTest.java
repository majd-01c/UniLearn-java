package entities.job_offer;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobOfferMeetingTest {

    @Test
    void canJoinAt_allowsOnlyTheScheduledWindow() {
        JobOfferMeeting meeting = new JobOfferMeeting();
        LocalDateTime start = LocalDateTime.of(2026, 5, 24, 7, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 24, 7, 30);
        meeting.reschedule(Timestamp.valueOf(start), Timestamp.valueOf(end));

        assertFalse(meeting.canJoinAt(start.minusSeconds(1)));
        assertTrue(meeting.canJoinAt(start));
        assertTrue(meeting.canJoinAt(start.plusMinutes(15)));
        assertTrue(meeting.canJoinAt(end));
        assertFalse(meeting.canJoinAt(end.plusSeconds(1)));
    }

    @Test
    void canJoinAt_blocksEndedAndCancelledMeetings() {
        JobOfferMeeting meeting = new JobOfferMeeting();
        LocalDateTime start = LocalDateTime.of(2026, 5, 24, 7, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 24, 7, 30);
        meeting.reschedule(Timestamp.valueOf(start), Timestamp.valueOf(end));

        meeting.end();
        assertFalse(meeting.canJoinAt(start.plusMinutes(15)));

        meeting.reschedule(Timestamp.valueOf(start), Timestamp.valueOf(end));
        meeting.setStatus(JobOfferMeeting.STATUS_CANCELLED);
        assertFalse(meeting.canJoinAt(start.plusMinutes(15)));
    }

    @Test
    void reschedule_requiresEndAfterStart() {
        JobOfferMeeting meeting = new JobOfferMeeting();
        LocalDateTime start = LocalDateTime.of(2026, 5, 24, 7, 0);

        assertThrows(IllegalArgumentException.class,
                () -> meeting.reschedule(Timestamp.valueOf(start), Timestamp.valueOf(start)));
    }
}
