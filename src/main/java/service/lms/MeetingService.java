package service.lms;

import entities.ClassMeeting;
import entities.StudentClasse;
import entities.TeacherClasse;
import repository.lms.ClassMeetingRepository;
import repository.lms.StudentClasseRepository;
import repository.lms.TeacherClasseRepository;
import security.UserSession;
import util.ConfigurationProvider;
import util.RoleGuard;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

public class MeetingService {

    private static final String DEFAULT_JITSI_HOST = "meet.jit.si";

    private final ClassMeetingRepository meetingRepo = new ClassMeetingRepository();
    private final TeacherClasseRepository teacherClasseRepo = new TeacherClasseRepository();
    private final StudentClasseRepository studentClasseRepo = new StudentClasseRepository();

    public ClassMeeting createMeetingForTeacher(Integer teacherClasseId,
                                                String title,
                                                String description,
                                                Timestamp scheduledAt,
                                                boolean startNow) {
        TeacherClasse teacherClasse = requireOwnedTeacherClasse(teacherClasseId);
        String cleanTitle = normalizeRequiredTitle(title);

        ClassMeeting meeting = new ClassMeeting();
        meeting.setTeacherClasse(teacherClasse);
        meeting.setTitle(cleanTitle);
        meeting.setDescription(normalizeOptional(description));
        meeting.setScheduledAt(scheduledAt);
        if (startNow) {
            meeting.start();
        }

        return meetingRepo.save(meeting);
    }

    public List<ClassMeeting> getMeetingsForTeacher(Integer teacherClasseId) {
        requireOwnedTeacherClasse(teacherClasseId);
        return meetingRepo.findByTeacherClasse(teacherClasseId);
    }

    public ClassMeeting startMeeting(Integer teacherClasseId, Integer meetingId) {
        requireOwnedTeacherClasse(teacherClasseId);
        ClassMeeting meeting = requireTeacherMeeting(teacherClasseId, meetingId);
        meeting.start();
        ClassMeeting updated = meetingRepo.update(meeting);
        return meetingRepo.findById(updated.getId()).orElse(updated);
    }

    public ClassMeeting joinTeacherMeeting(Integer teacherClasseId, Integer meetingId) {
        requireOwnedTeacherClasse(teacherClasseId);
        ClassMeeting meeting = requireTeacherMeeting(teacherClasseId, meetingId);
        if (!meeting.isLive()) {
            meeting.start();
            ClassMeeting updated = meetingRepo.update(meeting);
            meeting = meetingRepo.findById(updated.getId()).orElse(updated);
        }
        return meeting;
    }

    public void endMeeting(Integer teacherClasseId, Integer meetingId) {
        requireOwnedTeacherClasse(teacherClasseId);
        ClassMeeting meeting = requireTeacherMeeting(teacherClasseId, meetingId);
        meeting.end();
        meetingRepo.update(meeting);
    }

    public void deleteMeeting(Integer teacherClasseId, Integer meetingId) {
        requireOwnedTeacherClasse(teacherClasseId);
        requireTeacherMeeting(teacherClasseId, meetingId);
        meetingRepo.delete(meetingId);
    }

    public List<ClassMeeting> getUpcomingMeetingsForStudentClasse(Integer classeId) {
        requireActiveStudentEnrollment(classeId);
        return meetingRepo.findUpcomingMeetingsForClasse(classeId);
    }

    public List<ClassMeeting> getLiveMeetingsForStudentClasse(Integer classeId) {
        requireActiveStudentEnrollment(classeId);
        return meetingRepo.findLiveMeetingsForClasse(classeId);
    }

    public ClassMeeting joinStudentMeeting(Integer meetingId) {
        RoleGuard.requireStudent(UserSession.getCurrentUser());
        ClassMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));

        Integer classeId = meeting.getTeacherClasse() != null && meeting.getTeacherClasse().getClasse() != null
                ? meeting.getTeacherClasse().getClasse().getId()
                : null;
        requireActiveStudentEnrollment(classeId);

        if (!meeting.isLive()) {
            throw new IllegalStateException("This meeting is not currently active.");
        }
        return meeting;
    }

    public String getJitsiHost() {
        return extractHost(getJitsiBaseUrl());
    }

    public String getJitsiBaseUrl() {
        String configuredHost = normalizeOptional(ConfigurationProvider.getProperty("JITSI_HOST", DEFAULT_JITSI_HOST));
        if (configuredHost == null) {
            configuredHost = DEFAULT_JITSI_HOST;
        }

        String baseUrl = hasHttpScheme(configuredHost) ? configuredHost : "https://" + configuredHost;
        int queryStart = baseUrl.indexOf('?');
        if (queryStart >= 0) {
            baseUrl = baseUrl.substring(0, queryStart);
        }
        int fragmentStart = baseUrl.indexOf('#');
        if (fragmentStart >= 0) {
            baseUrl = baseUrl.substring(0, fragmentStart);
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    public String buildJitsiExternalApiUrl() {
        return getJitsiBaseUrl() + "/external_api.js";
    }

    public String buildDirectMeetingUrl(ClassMeeting meeting) {
        String roomCode = meeting != null ? meeting.getRoomCode() : "";
        return getJitsiBaseUrl() + "/" + roomCode;
    }

    public boolean isEmbeddedMeetingsEnabled() {
        String clientMode = getJitsiClientMode();
        return "webview".equals(clientMode) || configBoolean("JITSI_EMBEDDED_ENABLED", false);
    }

    public boolean isJcefMeetingsEnabled() {
        String clientMode = getJitsiClientMode();
        return clientMode.startsWith("jcef") || configBoolean("JITSI_JCEF_ENABLED", false);
    }

    public boolean isEmbeddedJcefMeetingsEnabled() {
        String clientMode = getJitsiClientMode();
        return "jcef_embed".equals(clientMode)
                || "jcef-embed".equals(clientMode)
                || configBoolean("JITSI_JCEF_EMBEDDED", false);
    }

    public boolean isAutoOpenBrowserEnabled() {
        return configBoolean("JITSI_AUTO_OPEN_BROWSER", true);
    }

    public String getJitsiClientMode() {
        String configuredMode = normalizeOptional(ConfigurationProvider.getProperty("JITSI_CLIENT_MODE", "browser"));
        if (configuredMode == null) {
            return "browser";
        }
        return configuredMode.toLowerCase(Locale.ROOT);
    }

    private TeacherClasse requireOwnedTeacherClasse(Integer teacherClasseId) {
        RoleGuard.requireTeacher(UserSession.getCurrentUser());
        Integer currentUserId = UserSession.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new SecurityException("Access denied: not authenticated.");
        }

        TeacherClasse teacherClasse = teacherClasseRepo.findById(teacherClasseId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher class not found."));
        if (teacherClasse.getUser() == null || !currentUserId.equals(teacherClasse.getUser().getId())) {
            throw new SecurityException("Unauthorized action.");
        }
        if (teacherClasse.getIsActive() != 1) {
            throw new SecurityException("This teacher assignment is not active.");
        }
        return teacherClasse;
    }

    private StudentClasse requireActiveStudentEnrollment(Integer classeId) {
        RoleGuard.requireStudent(UserSession.getCurrentUser());
        Integer currentUserId = UserSession.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new SecurityException("Access denied: not authenticated.");
        }
        if (classeId == null) {
            throw new IllegalArgumentException("Class not found.");
        }

        StudentClasse enrollment = studentClasseRepo.findByStudentAndClasse(currentUserId, classeId)
                .orElseThrow(() -> new SecurityException("You are not enrolled in this class."));
        if (enrollment.getIsActive() != 1) {
            throw new SecurityException("You are not enrolled in this class.");
        }
        return enrollment;
    }

    private ClassMeeting requireTeacherMeeting(Integer teacherClasseId, Integer meetingId) {
        ClassMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found."));
        Integer ownerId = meeting.getTeacherClasse() != null ? meeting.getTeacherClasse().getId() : null;
        if (!teacherClasseId.equals(ownerId)) {
            throw new IllegalArgumentException("Meeting not found.");
        }
        return meeting;
    }

    private String normalizeRequiredTitle(String title) {
        String value = normalizeOptional(title);
        if (value == null) {
            throw new IllegalArgumentException("Meeting title is required.");
        }
        if (value.length() < 3) {
            throw new IllegalArgumentException("Meeting title must be at least 3 characters.");
        }
        return value;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasHttpScheme(String value) {
        return value != null
                && (value.regionMatches(true, 0, "https://", 0, "https://".length())
                || value.regionMatches(true, 0, "http://", 0, "http://".length()));
    }

    private String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_JITSI_HOST;
        }
        String host = baseUrl.trim();
        int schemeStart = host.indexOf("://");
        if (schemeStart >= 0) {
            host = host.substring(schemeStart + 3);
        }
        int slashStart = host.indexOf('/');
        if (slashStart >= 0) {
            host = host.substring(0, slashStart);
        }
        return host.isBlank() ? DEFAULT_JITSI_HOST : host;
    }

    private boolean configBoolean(String key, boolean defaultValue) {
        String configuredValue = normalizeOptional(ConfigurationProvider.getProperty(key, Boolean.toString(defaultValue)));
        if (configuredValue == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(configuredValue)
                || "1".equals(configuredValue)
                || "yes".equalsIgnoreCase(configuredValue)
                || "on".equalsIgnoreCase(configuredValue);
    }
}
