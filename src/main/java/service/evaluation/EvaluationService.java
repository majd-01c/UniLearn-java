package service.evaluation;

import entities.Assessment;
import entities.Classe;
import entities.Contenu;
import entities.Course;
import entities.DocumentRequest;
import entities.Grade;
import entities.Reclamation;
import entities.Schedule;
import entities.User;
import evaluation.AssessmentType;
import services.ServiceAssessment;
import services.ServiceContenu;
import services.ServiceCourse;
import services.ServiceDocumentRequest;
import services.ServiceGrade;
import services.ServiceReclamation;
import services.ServiceSchedule;
import services.ServiceUser;
import service.lms.ClasseService;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EvaluationService {

    private final ServiceAssessment assessmentService = new ServiceAssessment();
    private final ServiceGrade gradeService = new ServiceGrade();
    private final ServiceCourse courseService = new ServiceCourse();
    private final ServiceReclamation reclamationService = new ServiceReclamation();
    private final ServiceDocumentRequest documentRequestService = new ServiceDocumentRequest();
    private final ServiceSchedule scheduleService = new ServiceSchedule();
    private final ServiceContenu contenuService = new ServiceContenu();
    private final ServiceUser userService = new ServiceUser();
    private final ClasseService classeService = new ClasseService();

    public List<Grade> getGradesByStudent(int studentId) {
        return gradeService.getALL().stream()
                .filter(g -> g.getUserByStudentId() != null && g.getUserByStudentId().getId() != null)
                .filter(g -> g.getUserByStudentId().getId() == studentId)
                .sorted(Comparator
                        .comparing((Grade g) -> g.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Grade::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public StudentSummary computeStudentSummary(int studentId) {
        List<Grade> grades = getGradesByStudent(studentId);
        int total = grades.size();
        int passed = 0;
        int failed = 0;
        double sum = 0.0;

        for (Grade g : grades) {
            double score = g.getScore();
            sum += score;
            if (score >= 10.0) {
                passed++;
            } else {
                failed++;
            }
        }

        double average = total == 0 ? 0.0 : sum / total;
        return new StudentSummary(total, passed, failed, average);
    }

    public List<RecommendationRow> buildRecommendations(int studentId) {
        List<Grade> grades = getGradesByStudent(studentId);
        Map<String, List<Grade>> byCourse = grades.stream()
                .collect(Collectors.groupingBy(g -> {
                    if (g.getAssessment() != null && g.getAssessment().getCourse() != null) {
                        Integer courseId = g.getAssessment().getCourse().getId();
                        String courseTitle = resolveCourseTitle(courseId);
                        if (courseTitle != null) {
                            return courseTitle;
                        }
                    }
                    if (g.getAssessment() != null && g.getAssessment().getTitle() != null) {
                        return g.getAssessment().getTitle();
                    }
                    return "Unknown course";
                }));

        List<RecommendationRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Grade>> entry : byCourse.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Grade::getScore).average().orElse(0.0);
            String priority;
            String action;
            if (avg < 8.0) {
                priority = "HIGH";
                action = "Urgent remediation and teacher follow-up";
            } else if (avg < 10.0) {
                priority = "MEDIUM";
                action = "Focused revision and weekly practice";
            } else {
                priority = "LOW";
                action = "Keep momentum and deepen understanding";
            }
            rows.add(new RecommendationRow(entry.getKey(), priority, action, avg));
        }

        rows.sort(Comparator.comparing(RecommendationRow::priorityWeight)
            .thenComparing(RecommendationRow::getAverage)
            .thenComparing(RecommendationRow::getCourseName));
        return rows;
    }

    public List<Schedule> getScheduleByClasse(int classeId) {
        return scheduleService.getALL().stream()
                .filter(s -> s.getClasse() != null && s.getClasse().getId() != null)
                .filter(s -> s.getClasse().getId() == classeId)
            .sorted(Comparator
                .comparing((Schedule s) -> dayOrder(s.getDayOfWeek()))
                .thenComparing(Schedule::getStartTime, Comparator.nullsLast(Time::compareTo))
                .thenComparing(Schedule::getId, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    public List<Schedule> getScheduleByTeacher(int teacherId) {
        return scheduleService.getALL().stream()
                .filter(s -> s.getUser() != null && s.getUser().getId() != null)
                .filter(s -> s.getUser().getId() == teacherId)
            .sorted(Comparator
                .comparing((Schedule s) -> dayOrder(s.getDayOfWeek()))
                .thenComparing(Schedule::getStartTime, Comparator.nullsLast(Time::compareTo))
                .thenComparing(Schedule::getId, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    public List<Reclamation> getReclamationsByStudent(int studentId) {
        return reclamationService.getALL().stream()
                .filter(r -> r.getUser() != null && r.getUser().getId() != null)
                .filter(r -> r.getUser().getId() == studentId)
                .sorted(Comparator
                        .comparing((Reclamation r) -> statusWeight(r.getStatus()))
                        .thenComparing(Reclamation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Reclamation::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public List<Reclamation> getAllReclamations() {
        return reclamationService.getALL().stream()
                .sorted(Comparator
                        .comparing((Reclamation r) -> statusWeight(r.getStatus()))
                        .thenComparing(Reclamation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Reclamation::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public void createReclamation(int studentId, String courseName, String subject, String description) {
        Reclamation reclamation = new Reclamation();
        reclamation.setUser(userRef(studentId));
        Integer courseId = findCourseIdByName(courseName);
        reclamation.setCourse(courseId == null ? null : courseRef(courseId));
        reclamation.setSubject(subject);
        reclamation.setDescription(description);
        reclamation.setStatus("pending");
        reclamation.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        reclamation.setResolvedAt(null);
        reclamationService.add(reclamation);
    }

    public String resolveCourseTitle(Integer courseId) {
        if (courseId == null) {
            return null;
        }
        return courseService.getALL().stream()
                .filter(c -> c.getId() != null && c.getId().equals(courseId))
                .map(Course::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .findFirst()
                .orElse(null);
    }

    public String resolveClasseName(Integer classeId) {
        if (classeId == null) {
            return null;
        }
        return classeService.listAll().stream()
                .filter(c -> c.getId() != null && c.getId().equals(classeId))
                .map(Classe::getName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    public String resolveContenuTitle(Integer contenuId) {
        if (contenuId == null) {
            return null;
        }
        return contenuService.getALL().stream()
                .filter(c -> c.getId() != null && c.getId().equals(contenuId))
                .map(Contenu::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .findFirst()
                .orElse(null);
    }

    public String resolveUserDisplayName(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userService.getALL().stream()
                .filter(u -> u.getId() != null && u.getId().equals(userId))
                .map(u -> {
                    String name = u.getName();
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                    return u.getEmail();
                })
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    public Integer findCourseIdByName(String courseName) {
        if (courseName == null || courseName.isBlank()) {
            return null;
        }
        String normalized = courseName.trim().toLowerCase(Locale.ROOT);
        return courseService.getALL().stream()
                .filter(c -> c.getTitle() != null && c.getId() != null)
                .filter(c -> c.getTitle().trim().toLowerCase(Locale.ROOT).equals(normalized))
                .map(Course::getId)
                .findFirst()
                .orElse(null);
    }

    public Integer findClasseIdByName(String classeName) {
        if (classeName == null || classeName.isBlank()) {
            return null;
        }
        String normalized = classeName.trim().toLowerCase(Locale.ROOT);
        return classeService.listAll().stream()
                .filter(c -> c.getName() != null && c.getId() != null)
                .filter(c -> c.getName().trim().toLowerCase(Locale.ROOT).equals(normalized))
                .map(Classe::getId)
                .findFirst()
                .orElse(null);
    }

    public Integer findContenuIdByTitle(String contenuTitle) {
        if (contenuTitle == null || contenuTitle.isBlank()) {
            return null;
        }
        String normalized = contenuTitle.trim().toLowerCase(Locale.ROOT);
        return contenuService.getALL().stream()
                .filter(c -> c.getTitle() != null && c.getId() != null)
                .filter(c -> c.getTitle().trim().toLowerCase(Locale.ROOT).equals(normalized))
                .map(Contenu::getId)
                .findFirst()
                .orElse(null);
    }

    public Integer findUserIdByName(String userName) {
        if (userName == null || userName.isBlank()) {
            return null;
        }
        String normalized = userName.trim().toLowerCase(Locale.ROOT);
        return userService.getALL().stream()
                .filter(u -> u.getId() != null)
                .filter(u -> {
                    String name = u.getName();
                    String email = u.getEmail();
                    return (name != null && name.trim().toLowerCase(Locale.ROOT).equals(normalized))
                            || (email != null && email.trim().toLowerCase(Locale.ROOT).equals(normalized));
                })
                .map(User::getId)
                .findFirst()
                .orElse(null);
    }

    public void updateReclamationStatus(int reclamationId, String status, String adminResponse) {
        Reclamation target = reclamationService.getALL().stream()
                .filter(r -> r.getId() != null && r.getId() == reclamationId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reclamation not found: " + reclamationId));

        target.setStatus(status);
        target.setAdminResponse(adminResponse);
        if ("resolved".equalsIgnoreCase(status) || "rejected".equalsIgnoreCase(status)) {
            target.setResolvedAt(Timestamp.valueOf(LocalDateTime.now()));
        }
        reclamationService.update(target);
    }

    public List<DocumentRequest> getDocumentRequestsByStudent(int studentId) {
        return documentRequestService.getALL().stream()
                .filter(d -> d.getUser() != null && d.getUser().getId() != null)
                .filter(d -> d.getUser().getId() == studentId)
                .sorted(Comparator
                        .comparing((DocumentRequest d) -> statusWeight(d.getStatus()))
                        .thenComparing(DocumentRequest::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentRequest::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public List<DocumentRequest> getAllDocumentRequests() {
        return documentRequestService.getALL().stream()
                .sorted(Comparator
                        .comparing((DocumentRequest d) -> statusWeight(d.getStatus()))
                        .thenComparing(DocumentRequest::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentRequest::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public void createDocumentRequest(int studentId, String type, String info) {
        DocumentRequest request = new DocumentRequest();
        request.setId(nextDocumentRequestId());
        request.setUser(userRef(studentId));
        request.setDocumentType(type);
        request.setAdditionalInfo(info);
        request.setStatus("pending");
        request.setRequestedAt(Timestamp.valueOf(LocalDateTime.now()));
        request.setDeliveredAt(null);
        request.setDocumentPath(null);
        documentRequestService.add(request);
    }

    public void updateDocumentRequest(int requestId, String status, String path) {
        DocumentRequest target = documentRequestService.getALL().stream()
                .filter(d -> d.getId() == requestId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document request not found: " + requestId));

        target.setStatus(status);
        target.setDocumentPath(path);
        if ("delivered".equalsIgnoreCase(status)) {
            target.setDeliveredAt(Timestamp.valueOf(LocalDateTime.now()));
        }
        documentRequestService.update(target);
    }

    public List<Assessment> getAssessmentsByTeacher(int teacherId) {
        return assessmentService.getALL().stream()
                .filter(a -> a.getUser() != null && a.getUser().getId() != null)
                .filter(a -> a.getUser().getId() == teacherId)
                .sorted(Comparator
                        .comparing(Assessment::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Assessment::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public void createAssessment(int teacherId,
                                 Integer courseId,
                                 Integer classeId,
                                 Integer contenuId,
                                 AssessmentType type,
                                 String title,
                                 String description,
                                 LocalDate assessmentDate,
                                 double maxScore) {
        Assessment assessment = new Assessment();
        assessment.setUser(userRef(teacherId));
        assessment.setCourse(courseRef(courseId));
        assessment.setClasse(classeId == null ? null : classeRef(classeId));
        assessment.setContenu(contenuId == null ? null : contenuRef(contenuId));
        assessment.setType(type.name());
        assessment.setTitle(title);
        assessment.setDescription(description);
        assessment.setDate(assessmentDate == null ? null : Timestamp.valueOf(assessmentDate.atStartOfDay()));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        assessment.setCreatedAt(now);
        assessment.setUpdatedAt(now);
        assessment.setMaxScore(maxScore);
        assessmentService.add(assessment);
    }

    public void deleteAssessment(int assessmentId) {
        Assessment target = new Assessment();
        target.setId(assessmentId);
        assessmentService.delete(target);
    }

    public List<Grade> getGradesByAssessment(int assessmentId) {
        return gradeService.getALL().stream()
                .filter(g -> g.getAssessment() != null && g.getAssessment().getId() != null)
                .filter(g -> g.getAssessment().getId() == assessmentId)
            .sorted(Comparator
                .comparing(Grade::getScore, Comparator.nullsLast(Double::compareTo)).reversed()
                .thenComparing(Grade::getId, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    public void saveGrade(int assessmentId, int studentId, int teacherId, double score, String comment) {
        Grade existing = gradeService.getALL().stream()
                .filter(g -> g.getAssessment() != null && g.getAssessment().getId() != null && g.getAssessment().getId() == assessmentId)
                .filter(g -> g.getUserByStudentId() != null && g.getUserByStudentId().getId() != null && g.getUserByStudentId().getId() == studentId)
                .findFirst()
                .orElse(null);

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        if (existing != null) {
            existing.setUserByTeacherId(userRef(teacherId));
            existing.setScore(score);
            existing.setComment(comment);
            existing.setUpdatedAt(now);
            gradeService.update(existing);
            return;
        }

        Grade created = new Grade();
        created.setId(nextGradeId());
        created.setAssessment(assessmentRef(assessmentId));
        created.setUserByStudentId(userRef(studentId));
        created.setUserByTeacherId(userRef(teacherId));
        created.setScore(score);
        created.setComment(comment);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        gradeService.add(created);
    }

    public List<Schedule> getAllSchedules() {
        return scheduleService.getALL().stream()
                .sorted(Comparator
                        .comparing((Schedule s) -> dayOrder(s.getDayOfWeek()))
                        .thenComparing(Schedule::getStartTime, Comparator.nullsLast(Time::compareTo))
                        .thenComparing(Schedule::getId, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    public void createSchedule(Integer teacherId,
                               int courseId,
                               int classeId,
                               String dayOfWeek,
                               LocalDate startDate,
                               LocalDate endDate,
                               Time startTime,
                               Time endTime,
                               String room) {
        Schedule schedule = new Schedule();
        schedule.setUser(teacherId == null ? null : userRef(teacherId));
        schedule.setCourse(courseRef(courseId));
        schedule.setClasse(classeRef(classeId));
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setStartDate(Date.valueOf(startDate));
        schedule.setEndDate(endDate == null ? null : Date.valueOf(endDate));
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setRoom(room);
        scheduleService.add(schedule);
    }

    public void deleteSchedule(int scheduleId) {
        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        scheduleService.delete(schedule);
    }

    private User userRef(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Course courseRef(int id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    private Classe classeRef(int id) {
        Classe classe = new Classe();
        classe.setId(id);
        return classe;
    }

    private Contenu contenuRef(int id) {
        Contenu contenu = new Contenu();
        contenu.setId(id);
        return contenu;
    }

    private Assessment assessmentRef(int id) {
        Assessment assessment = new Assessment();
        assessment.setId(id);
        return assessment;
    }

    private int nextGradeId() {
        return gradeService.getALL().stream()
                .map(Grade::getId)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private int nextDocumentRequestId() {
        return documentRequestService.getALL().stream()
                .map(DocumentRequest::getId)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private int dayOrder(String day) {
        if (day == null) {
            return 99;
        }
        return switch (day.trim().toLowerCase(Locale.ROOT)) {
            case "monday" -> 1;
            case "tuesday" -> 2;
            case "wednesday" -> 3;
            case "thursday" -> 4;
            case "friday" -> 5;
            case "saturday" -> 6;
            case "sunday" -> 7;
            default -> 99;
        };
    }

    private int statusWeight(String status) {
        if (status == null || status.isBlank()) {
            return 3;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if ("pending".equals(normalized) || "processing".equals(normalized)) {
            return 1;
        }
        if ("approved".equals(normalized) || "resolved".equals(normalized) || "delivered".equals(normalized)) {
            return 2;
        }
        return 3;
    }

    public static class StudentSummary {
        private final int totalGrades;
        private final int passed;
        private final int failed;
        private final double average;

        public StudentSummary(int totalGrades, int passed, int failed, double average) {
            this.totalGrades = totalGrades;
            this.passed = passed;
            this.failed = failed;
            this.average = average;
        }

        public int getTotalGrades() {
            return totalGrades;
        }

        public int getPassed() {
            return passed;
        }

        public int getFailed() {
            return failed;
        }

        public double getAverage() {
            return average;
        }
    }

    public static class RecommendationRow {
        private final String courseName;
        private final String priority;
        private final String action;
        private final double average;

        public RecommendationRow(String courseName, String priority, String action, double average) {
            this.courseName = courseName;
            this.priority = priority;
            this.action = action;
            this.average = average;
        }

        public String getCourseName() {
            return courseName;
        }

        public String getPriority() {
            return priority;
        }

        public String getAction() {
            return action;
        }

        public double getAverage() {
            return average;
        }

        public int priorityWeight() {
            if ("HIGH".equalsIgnoreCase(priority)) {
                return 1;
            }
            if ("MEDIUM".equalsIgnoreCase(priority)) {
                return 2;
            }
            return 3;
        }
    }
}
