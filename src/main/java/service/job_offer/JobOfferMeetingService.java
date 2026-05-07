package service.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import entities.job_offer.JobOfferMeeting;
import repository.job_offer.JobApplicationRepository;
import repository.job_offer.JobOfferMeetingRepository;
import security.UserSession;
import service.lms.MeetingService;
import util.RoleGuard;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JobOfferMeetingService {

    private final JobOfferMeetingRepository meetingRepository = new JobOfferMeetingRepository();
    private final JobApplicationRepository applicationRepository = new JobApplicationRepository();
    private final MeetingService meetingClientConfig = new MeetingService();

    public JobOfferMeeting scheduleMeetingForPartner(Integer applicationId,
                                                     String title,
                                                     String description,
                                                     Timestamp scheduledAt) {
        User currentUser = requireReviewer();
        if (scheduledAt == null) {
            throw new IllegalArgumentException("Meeting date and time are required.");
        }

        JobApplication application = requireApplication(applicationId);
        requireAccepted(application);
        requireReviewerCanManage(application, currentUser);

        JobOffer offer = requireOffer(application);
        User student = requireStudent(application);
        User partner = resolvePartner(offer, currentUser);

        JobOfferMeeting meeting = meetingRepository.findByApplicationId(applicationId)
                .orElseGet(JobOfferMeeting::new);
        meeting.setApplication(application);
        meeting.setJobOffer(offer);
        meeting.setStudent(student);
        meeting.setPartner(partner);
        meeting.setTitle(normalizeTitle(title, offer));
        meeting.setDescription(normalizeOptional(description));
        meeting.reschedule(scheduledAt);
        if (meeting.getCreatedAt() == null) {
            meeting.setCreatedAt(Timestamp.from(Instant.now()));
        }
        if (meeting.getRoomCode() == null || meeting.getRoomCode().isBlank()) {
            meeting.setRoomCode(new JobOfferMeeting().getRoomCode());
        }

        return meetingRepository.save(meeting);
    }

    public JobOfferMeeting joinStudentMeeting(Integer meetingId) {
        RoleGuard.requireStudent(UserSession.getCurrentUser());
        Integer currentUserId = UserSession.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new SecurityException("Access denied: not authenticated.");
        }

        JobOfferMeeting meeting = requireMeeting(meetingId);
        Integer studentId = meeting.getStudent() != null ? meeting.getStudent().getId() : null;
        if (!currentUserId.equals(studentId)) {
            throw new SecurityException("This meeting belongs to another student.");
        }

        return joinCheckedMeeting(meeting);
    }

    public JobOfferMeeting joinPartnerMeeting(Integer meetingId) {
        User currentUser = requireReviewer();
        JobOfferMeeting meeting = requireMeeting(meetingId);
        requireReviewerCanManage(meeting.getApplication(), currentUser);
        return joinCheckedMeeting(meeting);
    }

    public void endMeeting(Integer meetingId) {
        User currentUser = requireReviewer();
        JobOfferMeeting meeting = requireMeeting(meetingId);
        requireReviewerCanManage(meeting.getApplication(), currentUser);
        meeting.end();
        meetingRepository.save(meeting);
    }

    public List<JobOfferMeeting> getMeetingsForStudent(Integer studentId) {
        RoleGuard.requireStudent(UserSession.getCurrentUser());
        Integer currentUserId = UserSession.getCurrentUserId().orElse(null);
        if (currentUserId == null || !currentUserId.equals(studentId)) {
            throw new SecurityException("You can only view your own meetings.");
        }
        return meetingRepository.findByStudentId(studentId);
    }

    public List<JobOfferMeeting> getMeetingsForReviewer(Integer reviewerId) {
        User currentUser = requireReviewer();
        Integer currentUserId = currentUser.getId();
        if (RoleGuard.isAdmin(currentUser)) {
            return meetingRepository.findAll();
        }
        if (currentUserId == null || !currentUserId.equals(reviewerId)) {
            throw new SecurityException("You can only view meetings for your own offers.");
        }
        return meetingRepository.findByPartnerId(reviewerId);
    }

    public Map<Integer, JobOfferMeeting> getMeetingsByApplicationIds(Collection<Integer> applicationIds) {
        return meetingRepository.findByApplicationIds(applicationIds).stream()
                .filter(meeting -> meeting.getApplication() != null)
                .collect(Collectors.toMap(
                        meeting -> meeting.getApplication().getId(),
                        meeting -> meeting,
                        (left, right) -> left
                ));
    }

    public boolean canJoinNow(JobOfferMeeting meeting) {
        return meeting != null && meeting.canJoinAt(LocalDateTime.now());
    }

    public String buildDirectMeetingUrl(JobOfferMeeting meeting) {
        String roomCode = meeting != null ? meeting.getRoomCode() : "";
        return meetingClientConfig.getJitsiBaseUrl() + "/" + roomCode;
    }

    private JobOfferMeeting joinCheckedMeeting(JobOfferMeeting meeting) {
        requireAccepted(meeting.getApplication());
        if (!canJoinNow(meeting)) {
            throw new IllegalStateException("This meeting opens on its scheduled date and time.");
        }
        if (!meeting.isLive()) {
            meeting.markLive();
            return meetingRepository.save(meeting);
        }
        return meeting;
    }

    private JobApplication requireApplication(Integer applicationId) {
        if (applicationId == null || applicationId <= 0) {
            throw new IllegalArgumentException("Valid application id is required.");
        }
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
    }

    private JobOfferMeeting requireMeeting(Integer meetingId) {
        if (meetingId == null || meetingId <= 0) {
            throw new IllegalArgumentException("Valid meeting id is required.");
        }
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
    }

    private User requireReviewer() {
        User currentUser = UserSession.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            throw new SecurityException("Access denied: not authenticated.");
        }
        if (RoleGuard.isStudent(currentUser)) {
            throw new SecurityException("Only partners can create or manage job offer meetings.");
        }
        return currentUser;
    }

    private void requireReviewerCanManage(JobApplication application, User currentUser) {
        JobOffer offer = requireOffer(application);
        if (RoleGuard.isAdmin(currentUser)) {
            return;
        }
        Integer ownerId = offer.getUser() != null ? offer.getUser().getId() : null;
        if (ownerId == null || !ownerId.equals(currentUser.getId())) {
            throw new SecurityException("You can only schedule meetings for your own job offers.");
        }
    }

    private void requireAccepted(JobApplication application) {
        JobApplicationStatus status = JobApplicationStatus.fromString(application == null ? null : application.getStatus());
        if (status != JobApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("A meeting can only be created or joined after the application is accepted.");
        }
    }

    private JobOffer requireOffer(JobApplication application) {
        if (application == null || application.getJobOffer() == null) {
            throw new IllegalArgumentException("Application is not linked to a job offer.");
        }
        return application.getJobOffer();
    }

    private User requireStudent(JobApplication application) {
        if (application == null || application.getUser() == null || application.getUser().getId() == null) {
            throw new IllegalArgumentException("Application is not linked to a student.");
        }
        return application.getUser();
    }

    private User resolvePartner(JobOffer offer, User currentUser) {
        if (offer != null && offer.getUser() != null && offer.getUser().getId() != null) {
            return offer.getUser();
        }
        return currentUser;
    }

    private String normalizeTitle(String title, JobOffer offer) {
        String cleanTitle = normalizeOptional(title);
        if (cleanTitle != null && cleanTitle.length() >= 3) {
            return cleanTitle;
        }
        String offerTitle = offer != null ? normalizeOptional(offer.getTitle()) : null;
        return offerTitle == null ? "Job interview meeting" : "Interview - " + offerTitle;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
