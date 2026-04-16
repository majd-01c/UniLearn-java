package service.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import entities.job_offer.JobOfferStatus;
import repository.job_offer.IJobApplicationRepository;
import repository.job_offer.IJobOfferRepository;
import repository.job_offer.JobApplicationRepository;
import repository.job_offer.JobOfferRepository;
import util.job_offer.JobOfferBusinessRules;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Core Job Application workflow service.
 *
 * Scope is intentionally limited to core Job Application behavior only.
 * No ATS/AI integrations are performed here.
 */
public class JobApplicationService {

    private final IJobApplicationRepository jobApplicationRepository;
    private final IJobOfferRepository jobOfferRepository;

    public JobApplicationService() {
        this(new JobApplicationRepository(), new JobOfferRepository());
    }

    public JobApplicationService(IJobApplicationRepository jobApplicationRepository,
                                 IJobOfferRepository jobOfferRepository) {
        this.jobApplicationRepository = Objects.requireNonNull(jobApplicationRepository, "jobApplicationRepository is required");
        this.jobOfferRepository = Objects.requireNonNull(jobOfferRepository, "jobOfferRepository is required");
    }

    public boolean hasAlreadyApplied(Integer studentId, Integer offerId) {
        validateStudentAndOfferIds(studentId, offerId);
        return jobApplicationRepository.hasStudentApplied(studentId, offerId);
    }

    public JobApplication apply(Integer studentId, Integer offerId, String message, String cvFileName) {
        validateStudentAndOfferIds(studentId, offerId);

        JobOffer offer = jobOfferRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer not found: " + offerId));

        JobOfferStatus offerStatus = JobOfferStatus.fromString(offer.getStatus());
        if (!JobOfferBusinessRules.canStudentApplyToOffer(offerStatus)) {
            throw new IllegalStateException("Application is allowed only for ACTIVE offers. Current status: " + offerStatus.name());
        }

        if (jobApplicationRepository.hasStudentApplied(studentId, offerId)) {
            throw new IllegalStateException("Student has already applied to this offer");
        }

        Timestamp now = Timestamp.from(Instant.now());
        JobApplication application = new JobApplication();

        User studentRef = new User();
        studentRef.setId(studentId);

        application.setUser(studentRef);
        application.setJobOffer(offer);
        application.setMessage(message);
        application.setCvFileName(cvFileName);
        application.setStatus(JobApplicationStatus.SUBMITTED.name());
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        application.setStatusNotified((byte) 0);
        application.setStatusNotifiedAt(null);

        return jobApplicationRepository.save(application);
    }

    public JobApplication updateStatus(Integer applicationId,
                                       JobApplicationStatus newStatus,
                                       String customMessage) {
        if (applicationId == null || applicationId <= 0) {
            throw new IllegalArgumentException("Valid application id is required");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status is required");
        }

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        JobApplicationStatus currentStatus = JobApplicationStatus.fromString(application.getStatus());
        if (!JobOfferBusinessRules.canTransitionApplicationStatus(currentStatus, newStatus)) {
            throw new IllegalStateException(JobOfferBusinessRules.applicationTransitionErrorMessage(currentStatus, newStatus));
        }

        application.setStatus(newStatus.name());
        application.setUpdatedAt(Timestamp.from(Instant.now()));

        if (customMessage != null && !customMessage.isBlank()) {
            application.setStatusMessage(customMessage.trim());
        }

        // When a final decision is made, mark as not-yet-notified.
        if (JobOfferBusinessRules.needsStatusNotification(newStatus)) {
            application.setStatusNotified((byte) 0);
            application.setStatusNotifiedAt(null);
        }

        return jobApplicationRepository.save(application);
    }

    public JobApplication markStatusAsNotified(Integer applicationId) {
        if (applicationId == null || applicationId <= 0) {
            throw new IllegalArgumentException("Valid application id is required");
        }

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        application.setStatusNotified((byte) 1);
        application.setStatusNotifiedAt(Timestamp.from(Instant.now()));
        application.setUpdatedAt(Timestamp.from(Instant.now()));

        return jobApplicationRepository.save(application);
    }

    public List<JobApplication> getApplicationsForOffer(Integer offerId, int page, int pageSize) {
        if (offerId == null || offerId <= 0) {
            throw new IllegalArgumentException("Valid offer id is required");
        }

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;

        return jobApplicationRepository.findByOfferId(offerId, offset, safePageSize);
    }

    public List<JobApplication> getApplicationsForStudent(Integer studentId, int page, int pageSize) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Valid student id is required");
        }

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;

        return jobApplicationRepository.findByStudentId(studentId, offset, safePageSize);
    }

    private void validateStudentAndOfferIds(Integer studentId, Integer offerId) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("Valid student id is required");
        }
        if (offerId == null || offerId <= 0) {
            throw new IllegalArgumentException("Valid offer id is required");
        }
    }
}
