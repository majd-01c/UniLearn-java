package service.job_offer;

import entities.User;
import entities.job_offer.JobOffer;
import entities.job_offer.JobOfferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.job_offer.IJobOfferRepository;
import repository.job_offer.JobOfferRepository;
import util.job_offer.JobOfferBusinessRules;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Core Job Offer workflow service.
 *
 * Scope is intentionally limited to:
 * - createForPartner
 * - update
 * - changeStatus
 * - delete
 * - getPartnerOffersPaginated
 *
 * No ATS/AI integrations are performed here.
 */
public class JobOfferService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobOfferService.class);

    private final IJobOfferRepository jobOfferRepository;

    public JobOfferService() {
        this(new JobOfferRepository());
    }

    public JobOfferService(IJobOfferRepository jobOfferRepository) {
        this.jobOfferRepository = Objects.requireNonNull(jobOfferRepository, "jobOfferRepository is required");
    }

    public JobOffer createForPartner(User partner, JobOffer offer) {
        if (partner == null || partner.getId() == null || partner.getId() <= 0) {
            throw new IllegalArgumentException("Valid partner is required");
        }
        if (offer == null) {
            throw new IllegalArgumentException("Offer payload is required");
        }

        String inputError = JobOfferBusinessRules.validateJobOfferInput(
                offer.getTitle(),
                offer.getDescription(),
                offer.getType()
        );
        if (inputError != null) {
            throw new IllegalArgumentException(inputError);
        }

        Timestamp now = Timestamp.from(Instant.now());
        offer.setUser(partner);
        offer.setStatus(JobOfferStatus.PENDING.name());

        if (offer.getCreatedAt() == null) {
            offer.setCreatedAt(now);
        }
        offer.setUpdatedAt(now);

        LOGGER.info("Creating job offer for partner {} with status PENDING", partner.getId());
        return jobOfferRepository.save(offer);
    }

    public JobOffer update(Integer actorPartnerId, JobOffer updatedOffer) {
        if (actorPartnerId == null || actorPartnerId <= 0) {
            throw new IllegalArgumentException("Valid actor partner id is required");
        }
        if (updatedOffer == null || updatedOffer.getId() <= 0) {
            throw new IllegalArgumentException("Offer id is required for update");
        }

        JobOffer existing = jobOfferRepository.findById(updatedOffer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + updatedOffer.getId()));

        Integer ownerId = existing.getUser() != null ? existing.getUser().getId() : null;
        if (!JobOfferBusinessRules.canPartnerManageOffer(actorPartnerId, ownerId)) {
            throw new SecurityException("Unauthorized: partner cannot update this offer");
        }

        // Rule: ownership cannot be changed by update payload.
        Integer payloadOwnerId = updatedOffer.getUser() != null ? updatedOffer.getUser().getId() : null;
        if (payloadOwnerId != null && !payloadOwnerId.equals(ownerId)) {
            throw new SecurityException("Unauthorized ownership change attempt");
        }

        String inputError = JobOfferBusinessRules.validateJobOfferInput(
                updatedOffer.getTitle(),
                updatedOffer.getDescription(),
                updatedOffer.getType()
        );
        if (inputError != null) {
            throw new IllegalArgumentException(inputError);
        }

        // Preserve immutable ownership and timeline fields.
        updatedOffer.setUser(existing.getUser());
        updatedOffer.setCreatedAt(existing.getCreatedAt());

        if (JobOfferStatus.ACTIVE.name().equalsIgnoreCase(updatedOffer.getStatus())
                && existing.getPublishedAt() == null
                && updatedOffer.getPublishedAt() == null) {
            updatedOffer.setPublishedAt(Timestamp.from(Instant.now()));
        }

        updatedOffer.setUpdatedAt(Timestamp.from(Instant.now()));
        return jobOfferRepository.save(updatedOffer);
    }

    public JobOffer changeStatus(Integer actorPartnerId, Integer offerId, JobOfferStatus targetStatus) {
        if (actorPartnerId == null || actorPartnerId <= 0) {
            throw new IllegalArgumentException("Valid actor partner id is required");
        }
        if (offerId == null || offerId <= 0) {
            throw new IllegalArgumentException("Valid offer id is required");
        }
        if (targetStatus == null) {
            throw new IllegalArgumentException("Target status is required");
        }

        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

        Integer ownerId = offer.getUser() != null ? offer.getUser().getId() : null;
        if (!JobOfferBusinessRules.canPartnerManageOffer(actorPartnerId, ownerId)) {
            throw new SecurityException("Unauthorized: partner cannot change status of this offer");
        }

        JobOfferStatus currentStatus = JobOfferStatus.fromString(offer.getStatus());

        // Business rule: reopening closed offer always routes to PENDING.
        JobOfferStatus effectiveTarget = currentStatus == JobOfferStatus.CLOSED
                ? JobOfferStatus.PENDING
                : targetStatus;

        if (!JobOfferBusinessRules.canTransitionOfferStatus(currentStatus, effectiveTarget)) {
            throw new IllegalStateException(JobOfferBusinessRules.offerTransitionErrorMessage(currentStatus, effectiveTarget));
        }

        offer.setStatus(effectiveTarget.name());
        if (effectiveTarget == JobOfferStatus.ACTIVE && offer.getPublishedAt() == null) {
            offer.setPublishedAt(Timestamp.from(Instant.now()));
        }
        offer.setUpdatedAt(Timestamp.from(Instant.now()));

        return jobOfferRepository.save(offer);
    }

    public void delete(Integer actorPartnerId, Integer offerId) {
        if (actorPartnerId == null || actorPartnerId <= 0) {
            throw new IllegalArgumentException("Valid actor partner id is required");
        }
        if (offerId == null || offerId <= 0) {
            throw new IllegalArgumentException("Valid offer id is required");
        }

        JobOffer existing = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

        Integer ownerId = existing.getUser() != null ? existing.getUser().getId() : null;
        if (!JobOfferBusinessRules.canPartnerManageOffer(actorPartnerId, ownerId)) {
            throw new SecurityException("Unauthorized: partner cannot delete this offer");
        }

        jobOfferRepository.delete(offerId);
    }

    public List<JobOffer> getPartnerOffersPaginated(Integer partnerId, int page, int pageSize) {
        if (partnerId == null || partnerId <= 0) {
            throw new IllegalArgumentException("Valid partner id is required");
        }

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;

        return jobOfferRepository.findByPartnerId(partnerId, offset, safePageSize);
    }
}
