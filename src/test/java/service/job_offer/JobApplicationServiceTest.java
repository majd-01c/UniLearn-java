package service.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import entities.job_offer.JobOfferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.job_offer.IJobApplicationRepository;
import repository.job_offer.IJobOfferRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private IJobApplicationRepository jobApplicationRepository;

    @Mock
    private IJobOfferRepository jobOfferRepository;

    private JobApplicationService jobApplicationService;

    @BeforeEach
    void setUp() {
        jobApplicationService = new JobApplicationService(jobApplicationRepository, jobOfferRepository);
    }

    @Test
    void hasAlreadyApplied_delegatesToRepository() {
        when(jobApplicationRepository.hasStudentApplied(10, 20)).thenReturn(true);

        boolean result = jobApplicationService.hasAlreadyApplied(10, 20);

        assertTrue(result);
        verify(jobApplicationRepository, times(1)).hasStudentApplied(10, 20);
    }

    @Test
    void apply_allowedOnlyWhenOfferIsActive() {
        JobOffer offer = offer(20, JobOfferStatus.PENDING.name());
        when(jobOfferRepository.findById(20)).thenReturn(Optional.of(offer));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.apply(10, 20, "msg", "cv.pdf"));

        assertTrue(ex.getMessage().contains("only for ACTIVE offers"));
        verify(jobApplicationRepository, never()).save(any(JobApplication.class));
    }

    @Test
    void apply_preventsDuplicateApplication() {
        JobOffer offer = offer(20, JobOfferStatus.ACTIVE.name());
        when(jobOfferRepository.findById(20)).thenReturn(Optional.of(offer));
        when(jobApplicationRepository.hasStudentApplied(10, 20)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.apply(10, 20, "msg", "cv.pdf"));

        assertTrue(ex.getMessage().contains("already applied"));
        verify(jobApplicationRepository, never()).save(any(JobApplication.class));
    }

    @Test
    void apply_setsSubmittedAndPersists() {
        JobOffer offer = offer(20, JobOfferStatus.ACTIVE.name());
        when(jobOfferRepository.findById(20)).thenReturn(Optional.of(offer));
        when(jobApplicationRepository.hasStudentApplied(10, 20)).thenReturn(false);
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication result = jobApplicationService.apply(10, 20, "message", "cv.pdf");

        assertEquals(JobApplicationStatus.SUBMITTED.name(), result.getStatus());
        assertEquals(10, result.getUser().getId());
        assertEquals(20, result.getJobOffer().getId());
        assertEquals("message", result.getMessage());
        assertEquals("cv.pdf", result.getCvFileName());
        assertEquals((byte) 0, result.getStatusNotified());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void updateStatus_supportsOptionalCustomMessage() {
        JobApplication app = application(101, JobApplicationStatus.SUBMITTED.name());
        when(jobApplicationRepository.findById(101)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication result = jobApplicationService.updateStatus(101, JobApplicationStatus.REVIEWED, "Custom review note");

        assertEquals(JobApplicationStatus.REVIEWED.name(), result.getStatus());
        assertEquals("Custom review note", result.getStatusMessage());
    }

    @Test
    void updateStatus_acceptOrReject_setsNotificationFlagsPending() {
        JobApplication app = application(102, JobApplicationStatus.REVIEWED.name());
        app.setStatusNotified((byte) 1);
        app.setStatusNotifiedAt(Timestamp.valueOf("2026-04-13 12:00:00"));

        when(jobApplicationRepository.findById(102)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication result = jobApplicationService.updateStatus(102, JobApplicationStatus.ACCEPTED, null);

        assertEquals(JobApplicationStatus.ACCEPTED.name(), result.getStatus());
        assertEquals((byte) 0, result.getStatusNotified());
        assertNull(result.getStatusNotifiedAt());
    }

    @Test
    void updateStatus_rejectsInvalidTransitionWithClearMessage() {
        JobApplication app = application(103, JobApplicationStatus.SUBMITTED.name());
        when(jobApplicationRepository.findById(103)).thenReturn(Optional.of(app));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.updateStatus(103, JobApplicationStatus.ACCEPTED, null));

        assertTrue(ex.getMessage().contains("not a valid transition"));
        verify(jobApplicationRepository, never()).save(any(JobApplication.class));
    }

    @Test
    void markStatusAsNotified_setsFlagsAndTimestamp() {
        JobApplication app = application(104, JobApplicationStatus.ACCEPTED.name());
        app.setStatusNotified((byte) 0);
        when(jobApplicationRepository.findById(104)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobApplication result = jobApplicationService.markStatusAsNotified(104);

        assertEquals((byte) 1, result.getStatusNotified());
        assertNotNull(result.getStatusNotifiedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void getApplicationsForOffer_paginatesCorrectly() {
        when(jobApplicationRepository.findByOfferId(20, 20, 20)).thenReturn(List.of(application(105, "SUBMITTED")));

        List<JobApplication> result = jobApplicationService.getApplicationsForOffer(20, 2, 20);

        assertEquals(1, result.size());
        verify(jobApplicationRepository, times(1)).findByOfferId(20, 20, 20);
    }

    @Test
    void getApplicationsForStudent_paginatesCorrectly() {
        when(jobApplicationRepository.findByStudentId(10, 0, 20)).thenReturn(List.of(application(106, "SUBMITTED")));

        List<JobApplication> result = jobApplicationService.getApplicationsForStudent(10, 1, 20);

        assertEquals(1, result.size());
        verify(jobApplicationRepository, times(1)).findByStudentId(10, 0, 20);
    }

    @Test
    void apply_offerNotFound_hasClearMessage() {
        when(jobOfferRepository.findById(999)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.apply(10, 999, null, null));

        assertTrue(ex.getMessage().contains("Offer not found"));
    }

    @Test
    void markStatusAsNotified_applicationNotFound_hasClearMessage() {
        when(jobApplicationRepository.findById(999)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.markStatusAsNotified(999));

        assertTrue(ex.getMessage().contains("Application not found"));
    }

    private JobOffer offer(int id, String status) {
        JobOffer offer = new JobOffer();
        offer.setId(id);
        offer.setStatus(status);
        offer.setUser(user(500));
        return offer;
    }

    private JobApplication application(int id, String status) {
        JobApplication app = new JobApplication();
        app.setId(id);
        app.setStatus(status);
        app.setUser(user(10));
        app.setJobOffer(offer(20, JobOfferStatus.ACTIVE.name()));
        app.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        app.setUpdatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        return app;
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
