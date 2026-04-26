package service.evaluation;

import entities.Assessment;
import entities.Classe;
import entities.Contenu;
import entities.Course;
import entities.DocumentRequest;
import entities.Grade;
import entities.Reclamation;
import entities.Schedule;
import entities.StudentClasse;
import entities.User;
import evaluation.AssessmentType;
import repository.lms.StudentClasseRepository;
import services.ServiceAssessment;
import services.ServiceContenu;
import services.ServiceCourse;
import services.ServiceDocumentRequest;
import services.ServiceGrade;
import services.ServiceReclamation;
import services.ServiceSchedule;
import services.ServiceUser;
import service.lms.ClasseService;
import service.evaluation.ai.GroqAiService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final StudentClasseRepository studentClasseRepository = new StudentClasseRepository();
    private final GroqAiService groqAiService = new GroqAiService();

    public List<Grade> getGradesByStudent(int studentId) {
        List<Grade> rawGrades = gradeService.getALL().stream()
                .filter(g -> g.getUserByStudentId() != null && g.getUserByStudentId().getId() != null)
                .filter(g -> g.getUserByStudentId().getId() == studentId)
                .toList();

        for (Grade g : rawGrades) {
            if (g.getAssessment() != null && g.getAssessment().getId() != null) {
                g.setAssessment(getAssessmentById(g.getAssessment().getId()));
            }
        }

        return rawGrades.stream()
                .sorted(Comparator
                        .comparing((Grade g) -> g.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Grade::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public Assessment getAssessmentById(int id) {
        return assessmentService.getALL().stream()
                .filter(a -> a.getId() != null && a.getId() == id)
                .findFirst()
                .orElse(null);
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

    public Integer resolvePrimaryClassIdForStudent(int studentId) {
        return studentClasseRepository.findActiveByStudentId(studentId).stream()
                .filter(sc -> sc.getClasse() != null && sc.getClasse().getId() != null)
                .sorted(Comparator.comparing(StudentClasse::getEnrolledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(sc -> sc.getClasse().getId())
                .findFirst()
                .orElse(null);
    }

    public List<String> getCourseNamesForStudent(int studentId) {
        Set<String> names = new LinkedHashSet<>();

        getGradesByStudent(studentId).stream()
                .map(grade -> grade.getAssessment() == null || grade.getAssessment().getCourse() == null
                        ? null
                        : resolveCourseTitle(grade.getAssessment().getCourse().getId()))
                .filter(title -> title != null && !title.isBlank())
                .forEach(names::add);

        studentClasseRepository.findActiveByStudentId(studentId).stream()
                .map(StudentClasse::getClasse)
                .filter(classe -> classe != null && classe.getId() != null)
                .forEach(classe -> getScheduleByClasse(classe.getId()).stream()
                        .map(schedule -> schedule.getCourse() == null ? null : resolveCourseTitle(schedule.getCourse().getId()))
                        .filter(title -> title != null && !title.isBlank())
                        .forEach(names::add));

        if (names.isEmpty()) {
            courseService.getALL().stream()
                    .map(Course::getTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .forEach(names::add);
        }

        return names.stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    }

    public List<String> getTeacherCourseNames(int teacherId) {
        Set<String> names = new LinkedHashSet<>();

        getAssessmentsByTeacher(teacherId).stream()
                .map(assessment -> assessment.getCourse() == null ? null : resolveCourseTitle(assessment.getCourse().getId()))
                .filter(title -> title != null && !title.isBlank())
                .forEach(names::add);

        getScheduleByTeacher(teacherId).stream()
                .map(schedule -> schedule.getCourse() == null ? null : resolveCourseTitle(schedule.getCourse().getId()))
                .filter(title -> title != null && !title.isBlank())
                .forEach(names::add);

        if (names.isEmpty()) {
            courseService.getALL().stream()
                .map(Course::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .forEach(names::add);
        }

        return names.stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    }

    public List<String> getTeacherClassNames(int teacherId) {
        Set<String> names = new LinkedHashSet<>();

        getAssessmentsByTeacher(teacherId).stream()
                .map(assessment -> assessment.getClasse() == null ? null : resolveClasseName(assessment.getClasse().getId()))
                .filter(name -> name != null && !name.isBlank())
                .forEach(names::add);

        getScheduleByTeacher(teacherId).stream()
                .map(schedule -> schedule.getClasse() == null ? null : resolveClasseName(schedule.getClasse().getId()))
                .filter(name -> name != null && !name.isBlank())
                .forEach(names::add);

        if (names.isEmpty()) {
            classeService.listAll().stream()
                .map(Classe::getName)
                .filter(name -> name != null && !name.isBlank())
                .forEach(names::add);
        }

        return names.stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    }

    public List<String> getContenuTitles() {
        return contenuService.getALL().stream()
                .map(Contenu::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());
    }

    public List<String> getStudentNamesForTeacher(int teacherId, String classNameFilter) {
        Set<String> names = new LinkedHashSet<>();

        List<Integer> classIds = new ArrayList<>();
        if (classNameFilter != null && !classNameFilter.isBlank()) {
            Integer classId = findClasseIdByName(classNameFilter);
            if (classId != null) {
                classIds.add(classId);
            }
        } else {
            getTeacherClassNames(teacherId).forEach(className -> {
                Integer classId = findClasseIdByName(className);
                if (classId != null) {
                    classIds.add(classId);
                }
            });
        }

        classIds.forEach(classId -> studentClasseRepository.findByClasseId(classId).stream()
                .filter(sc -> sc.getIsActive() == 1)
                .map(StudentClasse::getUser)
                .filter(user -> user != null)
                .map(this::userLabel)
                .filter(label -> label != null && !label.isBlank())
                .forEach(names::add));

        if (names.isEmpty()) {
            gradeService.getALL().stream()
                    .filter(grade -> grade.getAssessment() != null && grade.getAssessment().getUser() != null)
                    .filter(grade -> grade.getAssessment().getUser().getId() != null && grade.getAssessment().getUser().getId() == teacherId)
                    .map(Grade::getUserByStudentId)
                    .filter(user -> user != null)
                    .map(this::userLabel)
                    .filter(label -> label != null && !label.isBlank())
                    .forEach(names::add);
        }

        return names.stream().sorted(String::compareToIgnoreCase).collect(Collectors.toList());
    }

    public String generateAiStudentRecommendations(int studentId) {
        List<RecommendationRow> rows = buildRecommendations(studentId);
        StudentSummary summary = computeStudentSummary(studentId);
        
        if (!groqAiService.isConfigured()) {
            return buildLocalStudentRecommendations(summary, rows);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Student performance summary:\n");
        prompt.append("- Total grades: ").append(summary.getTotalGrades()).append("\n");
        prompt.append("- Passed: ").append(summary.getPassed()).append("\n");
        prompt.append("- Failed: ").append(summary.getFailed()).append("\n");
        prompt.append("- Average: ").append(String.format(Locale.ROOT, "%.2f", summary.getAverage())).append("\n\n");
        prompt.append("Course-level data:\n");
        for (RecommendationRow row : rows) {
            prompt.append("- ").append(row.getCourseName())
                    .append(" | priority=").append(row.getPriority())
                    .append(" | average=").append(String.format(Locale.ROOT, "%.2f", row.getAverage()))
                    .append("\n");
        }

        return groqAiService.ask(
                "You are an Elite Academic Performance Coach. Create a high-impact, professional Study Roadmap. " +
                "Group your advice into 3-4 clear, actionable 'Strategic Focus' blocks. Use powerful, professional language. " +
                "Format each block with a clear heading. Keep it clean and concise.",
                prompt.toString(),
                800
        );
    }

    public String generateAiTeacherMessageFromStudent(int studentId) {
        List<RecommendationRow> rows = buildRecommendations(studentId);
        String studentName = resolveUserDisplayName(studentId);
        
        if (!groqAiService.isConfigured()) {
            return buildLocalTeacherMessage(studentName, studentId, rows);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Write a professional and proactive message from a student to their teacher.\n");
        prompt.append("Student Name: ").append(studentName == null ? ("#" + studentId) : studentName).append("\n");
        prompt.append("Performance context:\n");
        for (RecommendationRow row : rows) {
            prompt.append("- ").append(row.getCourseName())
                    .append(" | priority=").append(row.getPriority())
                    .append(" | average=").append(String.format(Locale.ROOT, "%.2f", row.getAverage()))
                    .append("\n");
        }
        prompt.append("Tone: Respectful, ambitious, and solution-oriented. Request specific guidance on these topics.");

        return groqAiService.ask(
                "You are a professional communication assistant. Write a high-quality email draft from a student to a professor.",
                prompt.toString(),
                500
        );
    }

    public String generateAiTeacherInsights(int teacherId) {
        List<Assessment> assessments = getAssessmentsByTeacher(teacherId);
        if (assessments.isEmpty()) {
            throw new IllegalArgumentException("No assessments found for this teacher.");
        }

        List<Integer> assessmentIds = assessments.stream()
                .map(Assessment::getId)
                .filter(id -> id != null)
                .toList();

        Map<String, List<Grade>> byStudent = gradeService.getALL().stream()
                .filter(grade -> grade.getAssessment() != null && grade.getAssessment().getId() != null)
                .filter(grade -> assessmentIds.contains(grade.getAssessment().getId()))
                .filter(grade -> grade.getUserByStudentId() != null)
                .collect(Collectors.groupingBy(grade -> {
                    String label = userLabel(grade.getUserByStudentId());
                    return label == null || label.isBlank() ? "Student" : label;
                }));

        if (byStudent.isEmpty()) {
            throw new IllegalArgumentException("No grades found to generate teacher insights.");
        }
        
        if (!groqAiService.isConfigured()) {
            return buildLocalTeacherInsights(byStudent);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Teacher dashboard data:\n");
        byStudent.forEach((student, grades) -> {
            double avg = grades.stream().mapToDouble(Grade::getScore).average().orElse(0.0);
            long lowScores = grades.stream().filter(g -> g.getScore() < 10.0).count();
            prompt.append("- ").append(student)
                    .append(" | avg=").append(String.format(Locale.ROOT, "%.2f", avg))
                    .append(" | low_scores=").append(lowScores)
                    .append(" | count=").append(grades.size())
                    .append("\n");
        });
        prompt.append("Provide actionable recommendations for the teacher: class-level actions, targeted student support, and next-week priorities.");

        return groqAiService.ask(
                "You are an Elite Teaching Consultant. Analyze the class performance and provide high-level instructional strategy advice. Group into 'Urgent Interventions' and 'Long-term Growth'.",
                prompt.toString(),
                900
        );
    }

    public String translatePdfDocument(String documentPath, String targetLanguage) {
        String text = extractPdfText(documentPath);
        if (text.isBlank()) {
            throw new IllegalArgumentException("PDF appears to be empty or not extractable.");
        }
        
        if (!groqAiService.isConfigured()) {
            return "AI translation is unavailable because GROQ_API_KEY is not configured. "
                    + "The source text is shown below:\n\n" + clipText(text, 12000);
        }

        String clipped = text.length() > 12000 ? text.substring(0, 12000) : text;
        String prompt = "Translate the following PDF text to " + targetLanguage +
                ". Preserve structure, headings, bullets, and academic terminology when relevant.\n\n" + clipped;

        return groqAiService.ask(
                "You are a professional academic translator. Keep the translation faithful and clear.",
                prompt,
                1200
        );
    }

    public void saveTextAsPdf(String text, File outputFile) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.setLeading(14.5f);
                contentStream.newLineAtOffset(50, 750);

                String[] lines = text.split("\\n");
                for (String line : lines) {
                    // Basic character escaping and multi-page logic would go here if text is very long
                    // For now, we do a basic single-page write
                    String safeLine = line.replace("\r", "").replace("\t", "    ");
                    contentStream.showText(safeLine);
                    contentStream.newLine();
                }
                contentStream.endText();
            }
            document.save(outputFile);
        }
    }

    private String extractPdfText(String documentPath) {
        if (documentPath == null || documentPath.isBlank()) {
            throw new IllegalArgumentException("Document path is required.");
        }
        if (documentPath.startsWith("http://") || documentPath.startsWith("https://")) {
            throw new IllegalArgumentException("Remote URLs are not supported for PDF translation. Use a local PDF file path.");
        }

        try {
            Path path = Path.of(documentPath.trim());
            if (!path.isAbsolute()) {
                path = Path.of(System.getProperty("user.dir")).resolve(path).normalize();
            }
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("PDF file not found: " + path);
            }

            try (PDDocument document = Loader.loadPDF(path.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read PDF content: " + e.getMessage(), e);
        }
    }

    private String buildLocalStudentRecommendations(StudentSummary summary, List<RecommendationRow> rows) {
        StringBuilder result = new StringBuilder();
        result.append("STRATEGIC ACADEMIC ROADMAP\n\n");
        result.append("Performance Baseline:\n");
        result.append("• Overall Average: ").append(String.format(Locale.ROOT, "%.2f", summary.getAverage())).append("\n");
        result.append("• Track Record: ").append(summary.getPassed()).append(" Successes / ").append(summary.getFailed()).append(" Challenges\n\n");

        if (rows.isEmpty()) {
            result.append("Current Focus: Foundational Assessment\n");
            result.append("Your performance profile is initializing. Complete upcoming assessments to unlock strategic insights.\n");
            return result.toString();
        }

        result.append("KEY STRATEGIC OBJECTIVES:\n");
        for (RecommendationRow row : rows) {
            result.append("• [").append(row.getPriority()).append("] ").append(row.getCourseName().toUpperCase())
                  .append(": ").append(row.getAction()).append("\n");
        }

        return result.toString();
    }

    private String buildLocalTeacherMessage(String studentName, int studentId, List<RecommendationRow> rows) {
        StringBuilder result = new StringBuilder();
        result.append("Dear Professor,\n\n");
        result.append("I am reaching out to discuss my current performance and proactive steps for improvement. ");
        result.append("Based on my recent metrics, I have identified specific areas where your guidance would be invaluable:\n\n");

        if (rows.isEmpty()) {
            result.append("- Strategic study methodologies and performance optimization.\n");
        } else {
            for (RecommendationRow row : rows) {
                result.append("- ").append(row.getCourseName()).append(": Optimization of '").append(row.getAction()).append("'.\n");
            }
        }

        result.append("\nI am committed to achieving academic excellence and appreciate your support.\n\nBest regards,\n").append(studentName != null ? studentName : "Student");
        return result.toString();
    }

    private String buildLocalTeacherInsights(Map<String, List<Grade>> byStudent) {
        StringBuilder result = new StringBuilder();
        result.append("### CLASS PERFORMANCE INSIGHTS\n\n");
        result.append("Academic Metrics:\n");

        byStudent.forEach((student, grades) -> {
            double average = grades.stream().mapToDouble(Grade::getScore).average().orElse(0.0);
            long lowScores = grades.stream().filter(grade -> grade.getScore() < 10.0).count();
            result.append("• ").append(student)
                    .append(" | score_avg=").append(String.format(Locale.ROOT, "%.2f", average))
                    .append(" | risk_level=").append(lowScores > 0 ? "HIGH" : "LOW")
                    .append("\n");
        });

        result.append("\nINSTRUCTIONAL PRIORITIES:\n");
        result.append("1. Intervention: Targeted support for students flagged with high risk levels.\n");
        result.append("2. Pedagogy: Refinement of modules with average scores below institutional baselines.\n");
        return result.toString();
    }

    private String clipText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String buildYoutubeSearchUrl(String topic) {
        return "https://www.youtube.com/results?search_query=" + encodeQuery(topic + " tutorial exercises");
    }

    private String buildUdemySearchUrl(String topic) {
        return "https://www.udemy.com/courses/search/?q=" + encodeQuery(topic);
    }

    private String buildCourseraSearchUrl(String topic) {
        return "https://www.coursera.org/search?query=" + encodeQuery(topic);
    }

    private String encodeQuery(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String userLabel(User user) {
        if (user == null) {
            return null;
        }
        String name = user.getName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String email = user.getEmail();
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return user.getId() == null ? null : ("User #" + user.getId());
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
                action = "Intensive remediation";
            } else if (avg < 10.0) {
                priority = "MEDIUM";
                action = "Focused academic review";
            } else {
                priority = "LOW";
                action = "Performance maintenance";
            }
            rows.add(new RecommendationRow(entry.getKey(), priority, action, avg));
        }

        rows.sort(Comparator.comparing(RecommendationRow::priorityWeight)
            .thenComparing(RecommendationRow::getAverage)
            .thenComparing(RecommendationRow::getCourseName));
        return rows;
    }

    public List<LearningResourceRow> buildLearningResources(int studentId) {
        List<RecommendationRow> recommendations = buildRecommendations(studentId);
        List<LearningResourceRow> resources = new ArrayList<>();

        for (RecommendationRow recommendation : recommendations) {
            String topic = recommendation.getCourseName() == null || recommendation.getCourseName().isBlank()
                    ? "study skills"
                    : recommendation.getCourseName().trim();
            String guidance = switch (recommendation.getPriority().toUpperCase(Locale.ROOT)) {
                case "HIGH" -> "Review foundational concepts using introductory guides.";
                case "MEDIUM" -> "Strengthen understanding with intermediate exercise sets.";
                default -> "Maintain excellence by exploring advanced applications.";
            };

            resources.add(new LearningResourceRow(
                    topic,
                    recommendation.getPriority(),
                    guidance,
                    buildYoutubeSearchUrl(topic),
                    buildUdemySearchUrl(topic),
                    buildCourseraSearchUrl(topic)
            ));
        }

        return resources;
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

    public static class LearningResourceRow {
        private final String courseName;
        private final String priority;
        private final String guidance;
        private final String youtubeUrl;
        private final String udemyUrl;
        private final String courseraUrl;

        public LearningResourceRow(String courseName, String priority, String guidance, String youtubeUrl, String udemyUrl, String courseraUrl) {
            this.courseName = courseName;
            this.priority = priority;
            this.guidance = guidance;
            this.youtubeUrl = youtubeUrl;
            this.udemyUrl = udemyUrl;
            this.courseraUrl = courseraUrl;
        }

        public String getCourseName() {
            return courseName;
        }

        public String getPriority() {
            return priority;
        }

        public String getGuidance() {
            return guidance;
        }

        public String getYoutubeUrl() {
            return youtubeUrl;
        }

        public String getUdemyUrl() {
            return udemyUrl;
        }

        public String getCourseraUrl() {
            return courseraUrl;
        }
    }
}
