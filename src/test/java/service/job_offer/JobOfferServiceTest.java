package service.job_offer;

import entities.User;
import entities.job_offer.JobOffer;
import entities.job_offer.JobOfferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.job_offer.IJobOfferRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobOfferServiceTest {

    @Mock
    private IJobOfferRepository jobOfferRepository;

    private JobOfferService jobOfferService;

    @BeforeEach
    void setUp() {
        jobOfferService = new JobOfferService(jobOfferRepository);
    }

    @Test
    void createForPartner_setsPartnerAndPending() {
        User partner = user(10);
        JobOffer offer = baseOffer();

        when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobOffer result = jobOfferService.createForPartner(partner, offer);

        assertEquals(10, result.getUser().getId());
        assertEquals(JobOfferStatus.PENDING.name(), result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        verify(jobOfferRepository, times(1)).save(any(JobOffer.class));
    }

    @Test
    void changeStatus_toActive_setsPublishedAtWhenNull() {
        JobOffer existing = existingOffer(100, 10, JobOfferStatus.PENDING.name());
        existing.setPublishedAt(null);

        when(jobOfferRepository.findById(100)).thenReturn(Optional.of(existing));
        when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobOffer updated = jobOfferService.changeStatus(10, 100, JobOfferStatus.ACTIVE);

        assertEquals(JobOfferStatus.ACTIVE.name(), updated.getStatus());
        assertNotNull(updated.getPublishedAt());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void reopeningClosedOffer_routesToPending_evenIfRequestedActive() {
        JobOffer existing = existingOffer(101, 10, JobOfferStatus.CLOSED.name());

        when(jobOfferRepository.findById(101)).thenReturn(Optional.of(existing));
        when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobOffer updated = jobOfferService.changeStatus(10, 101, JobOfferStatus.ACTIVE);

        assertEquals(JobOfferStatus.PENDING.name(), updated.getStatus());
    }

    @Test
    void update_rejectsUnauthorizedOwnershipChange() {
        JobOffer existing = existingOffer(102, 10, JobOfferStatus.PENDING.name());
        JobOffer payload = baseOffer();
        payload.setId(102);
        payload.setUser(user(99));

        when(jobOfferRepository.findById(102)).thenReturn(Optional.of(existing));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> jobOfferService.update(10, payload));

        assertTrue(ex.getMessage().contains("ownership"));
        verify(jobOfferRepository, never()).save(any(JobOffer.class));
    }

    @Test
    void update_rejectsWhenActorNotOwner() {
        JobOffer existing = existingOffer(103, 10, JobOfferStatus.PENDING.name());
        JobOffer payload = baseOffer();
        payload.setId(103);

        when(jobOfferRepository.findById(103)).thenReturn(Optional.of(existing));

        assertThrows(SecurityException.class, () -> jobOfferService.update(77, payload));
        verify(jobOfferRepository, never()).save(any(JobOffer.class));
    }

    @Test
    void delete_rejectsWhenActorNotOwner() {
        JobOffer existing = existingOffer(104, 10, JobOfferStatus.PENDING.name());
        when(jobOfferRepository.findById(104)).thenReturn(Optional.of(existing));

        assertThrows(SecurityException.class, () -> jobOfferService.delete(77, 104));
        verify(jobOfferRepository, never()).delete(anyInt());
    }

    @Test
    void changeStatus_rejectsInvalidTransition() {
        JobOffer existing = existingOffer(105, 10, JobOfferStatus.PENDING.name());
        when(jobOfferRepository.findById(105)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> jobOfferService.changeStatus(10, 105, JobOfferStatus.CLOSED));
        verify(jobOfferRepository, never()).save(any(JobOffer.class));
    }

    @Test
    void changeStatus_activeToClosed_isAllowed() {
        JobOffer existing = existingOffer(205, 10, JobOfferStatus.ACTIVE.name());
        when(jobOfferRepository.findById(205)).thenReturn(Optional.of(existing));
        when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobOffer updated = jobOfferService.changeStatus(10, 205, JobOfferStatus.CLOSED);

        assertEquals(JobOfferStatus.CLOSED.name(), updated.getStatus());
    }

    @Test
    void changeStatus_rejectedToPending_isRejected() {
        JobOffer existing = existingOffer(206, 10, JobOfferStatus.REJECTED.name());
        when(jobOfferRepository.findById(206)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> jobOfferService.changeStatus(10, 206, JobOfferStatus.PENDING));
        verify(jobOfferRepository, never()).save(any(JobOffer.class));
    }

    @Test
    void getPartnerOffersPaginated_usesCorrectOffsetAndLimit() {
        when(jobOfferRepository.findByPartnerId(10, 20, 20)).thenReturn(List.of(baseOffer()));

        List<JobOffer> result = jobOfferService.getPartnerOffersPaginated(10, 2, 20);

        assertEquals(1, result.size());
        verify(jobOfferRepository, times(1)).findByPartnerId(10, 20, 20);
    }

    @Test
    void delete_ownerCanDelete() {
        JobOffer existing = existingOffer(207, 10, JobOfferStatus.PENDING.name());
        when(jobOfferRepository.findById(207)).thenReturn(Optional.of(existing));

        jobOfferService.delete(10, 207);

        verify(jobOfferRepository, times(1)).delete(207);
    }

    @Test
    void createForPartner_rejectsInvalidPayload() {
        User partner = user(10);
        JobOffer invalid = new JobOffer();
        invalid.setType("INTERNSHIP");
        invalid.setDescription("desc");

        assertThrows(IllegalArgumentException.class,
                () -> jobOfferService.createForPartner(partner, invalid));
        verify(jobOfferRepository, never()).save(any(JobOffer.class));
    }

    @Test
    void update_toActive_setsPublishedAtWhenMissing() {
        JobOffer existing = existingOffer(208, 10, JobOfferStatus.PENDING.name());
        existing.setPublishedAt(null);

        JobOffer payload = baseOffer();
        payload.setId(208);
        payload.setStatus(JobOfferStatus.ACTIVE.name());
        payload.setUser(user(10));
        payload.setPublishedAt(null);

        when(jobOfferRepository.findById(208)).thenReturn(Optional.of(existing));
        when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobOffer updated = jobOfferService.update(10, payload);

        assertEquals(JobOfferStatus.ACTIVE.name(), updated.getStatus());
        assertNotNull(updated.getPublishedAt());
    }

    @Test
    void update_preservesOwnerAndCreatedAt() {
        JobOffer existing = existingOffer(106, 10, JobOfferStatus.PENDING.name());
        Timestamp createdAt = Timestamp.valueOf("2026-01-01 00:00:00");
        existing.setCreatedAt(createdAt);

        JobOffer payload = baseOffer();
        payload.setId(106);
        payload.setCreatedAt(Timestamp.valueOf("2027-01-01 00:00:00"));
        payload.setUser(user(10));

        when(jobOfferRepository.findById(106)).thenReturn(Optional.of(existing));
        when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobOffer updated = jobOfferService.update(10, payload);

        assertEquals(10, updated.getUser().getId());
        assertEquals(createdAt, updated.getCreatedAt());
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private JobOffer baseOffer() {
        JobOffer offer = new JobOffer();
        offer.setTitle("Java Developer Intern");
        offer.setType("INTERNSHIP");
        offer.setDescription("Internship opportunity");
        offer.setStatus(JobOfferStatus.PENDING.name());
        return offer;
    }

    private JobOffer existingOffer(int id, int ownerId, String status) {
        JobOffer offer = baseOffer();
        offer.setId(id);
        offer.setUser(user(ownerId));
        offer.setStatus(status);
        offer.setCreatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        offer.setUpdatedAt(Timestamp.valueOf("2026-01-01 00:00:00"));
        return offer;
    }
}
