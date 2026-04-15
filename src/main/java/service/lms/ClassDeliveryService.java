package service.lms;

import entities.*;
import repository.lms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import security.UserSession;
import util.RoleGuard;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing class-level delivery (modules, courses, content).
 *
 * Authorization rules:
 * - ADMIN: full access to all operations
 * - TEACHER: can only modify items within a ClasseModule that belongs to
 *            the teacher's own active assignment (ownership trace required)
 * - STUDENT: read-only (no write operations permitted)
 */
public class ClassDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(ClassDeliveryService.class);
    private final ClasseModuleRepository cmRepo = new ClasseModuleRepository();
    private final ClasseCourseRepository ccRepo = new ClasseCourseRepository();
    private final ClasseContenuRepository cxRepo = new ClasseContenuRepository();
    private final CourseRepository courseRepo = new CourseRepository();
    private final ContenuRepository contenuRepo = new ContenuRepository();
    private final TeacherClasseRepository tcRepo = new TeacherClasseRepository();

    // ==================== Authorization Helpers ====================

    /**
     * Verifies the current user has write access to the given ClasseModule.
     * - ADMIN: always allowed
     * - TEACHER: must have an active assignment whose module matches this ClasseModule's module
     * - Anyone else: denied
     */
    private void requireWriteAccessToClasseModule(Integer classeModuleId) {
        if (RoleGuard.isCurrentAdmin()) return;
        RoleGuard.requireCurrentAdminOrTeacher();

        ClasseModule cm = cmRepo.findById(classeModuleId)
                .orElseThrow(() -> new IllegalArgumentException("ClasseModule not found"));
        Integer currentUserId = UserSession.getCurrentUserId().orElse(null);
        if (currentUserId == null) {
            throw new SecurityException("Access denied: not authenticated.");
        }

        // Find teacher assignment for this class + module
        Integer classeId = cm.getClasse().getId();
        Integer moduleId = cm.getModule().getId();
        List<TeacherClasse> assignments = tcRepo.findActiveByClasseId(classeId);
        boolean ownsModule = assignments.stream().anyMatch(tc ->
                tc.getUser().getId().equals(currentUserId)
                && tc.getIsActive() == 1
                && tc.getModule() != null
                && tc.getModule().getId().equals(moduleId)
        );
        if (!ownsModule) {
            throw new SecurityException("Access denied: you can only manage courses and content within your own assigned module.");
        }
    }

    /**
     * Verifies the current user has write access to the given ClasseCourse.
     * Traces: ClasseCourse -> ClasseModule -> ownership check.
     */
    private void requireWriteAccessToClasseCourse(Integer classeCourseId) {
        if (RoleGuard.isCurrentAdmin()) return;
        RoleGuard.requireCurrentAdminOrTeacher();

        ClasseCourse cc = ccRepo.findById(classeCourseId)
                .orElseThrow(() -> new IllegalArgumentException("ClasseCourse not found"));
        requireWriteAccessToClasseModule(cc.getClasseModule().getId());
    }

    /**
     * Verifies the current user has write access to the given ClasseContenu.
     * Traces: ClasseContenu -> ClasseCourse -> ClasseModule -> ownership check.
     */
    private void requireWriteAccessToClasseContenu(Integer classeContenuId) {
        if (RoleGuard.isCurrentAdmin()) return;
        RoleGuard.requireCurrentAdminOrTeacher();

        ClasseContenu cx = cxRepo.findById(classeContenuId)
                .orElseThrow(() -> new IllegalArgumentException("ClasseContenu not found"));
        requireWriteAccessToClasseCourse(cx.getClasseCourse().getId());
    }

    // ==================== Classe Module ====================

    public List<ClasseModule> getModulesForClasse(Integer classeId) {
        return cmRepo.findByClasseId(classeId);
    }

    public List<dto.lms.ModuleRowDto> getModulesForClasseDto(Integer classeId) {
        return cmRepo.findByClasseId(classeId).stream().map(cm -> new dto.lms.ModuleRowDto(
                cm.getId(),
                cm.getModule() != null ? cm.getModule().getId() : null,
                cm.getModule() != null ? cm.getModule().getName() : "?",
                cm.getModule() != null ? cm.getModule().getDuration() + " " + cm.getModule().getPeriodUnit() : ""
        )).collect(java.util.stream.Collectors.toList());
    }

    // ==================== Classe Course ====================

    public ClasseCourse addCourseToClasseModule(Integer classeModuleId, Integer courseId) {
        requireWriteAccessToClasseModule(classeModuleId);
        ClasseModule cm = cmRepo.findById(classeModuleId)
                .orElseThrow(() -> new IllegalArgumentException("ClasseModule not found"));
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        ClasseCourse cc = new ClasseCourse();
        cc.setClasseModule(cm);
        cc.setCourse(course);
        cc.setIsHidden((byte) 0);
        return ccRepo.save(cc);
    }

    public void toggleCourseVisibility(Integer classeCourseId) {
        requireWriteAccessToClasseCourse(classeCourseId);
        ClasseCourse cc = ccRepo.findById(classeCourseId)
                .orElseThrow(() -> new IllegalArgumentException("ClasseCourse not found"));
        cc.setIsHidden(cc.getIsHidden() == 0 ? (byte) 1 : (byte) 0);
        ccRepo.update(cc);
    }

    public List<ClasseCourse> getCoursesForClasseModule(Integer classeModuleId) {
        return ccRepo.findByClasseModuleId(classeModuleId);
    }

    public List<dto.lms.CourseRowDto> getCoursesForClasseModuleDto(Integer classeModuleId) {
        return ccRepo.findByClasseModuleId(classeModuleId).stream().map(cc -> new dto.lms.CourseRowDto(
                cc.getId(),
                cc.getCourse() != null ? cc.getCourse().getId() : null,
                cc.getCourse() != null ? cc.getCourse().getTitle() : "?",
                cc.getIsHidden() == 0 ? "Visible" : "Hidden",
                cc.getIsHidden() != 0
        )).collect(java.util.stream.Collectors.toList());
    }

    public List<ClasseCourse> getVisibleCoursesForClasseModule(Integer classeModuleId) {
        return ccRepo.findVisibleByClasseModuleId(classeModuleId);
    }

    public List<dto.lms.CourseRowDto> getVisibleCoursesForClasseModuleDto(Integer classeModuleId) {
        return ccRepo.findVisibleByClasseModuleId(classeModuleId).stream().map(cc -> new dto.lms.CourseRowDto(
                cc.getId(),
                cc.getCourse() != null ? cc.getCourse().getId() : null,
                cc.getCourse() != null ? cc.getCourse().getTitle() : "?",
                "Visible",
                false
        )).collect(java.util.stream.Collectors.toList());
    }

    // ==================== Classe Contenu ====================

    public ClasseContenu addContenuToClasseCourse(Integer classeCourseId, Integer contenuId) {
        requireWriteAccessToClasseCourse(classeCourseId);
        ClasseCourse cc = ccRepo.findById(classeCourseId)
                .orElseThrow(() -> new IllegalArgumentException("ClasseCourse not found"));
        Contenu contenu = contenuRepo.findById(contenuId)
                .orElseThrow(() -> new IllegalArgumentException("Content not found"));
        ClasseContenu cx = new ClasseContenu();
        cx.setClasseCourse(cc);
        cx.setContenu(contenu);
        cx.setIsHidden((byte) 0);
        return cxRepo.save(cx);
    }

    public void toggleContenuVisibility(Integer classeContenuId) {
        requireWriteAccessToClasseContenu(classeContenuId);
        ClasseContenu cx = cxRepo.findById(classeContenuId)
                .orElseThrow(() -> new IllegalArgumentException("ClasseContenu not found"));
        cx.setIsHidden(cx.getIsHidden() == 0 ? (byte) 1 : (byte) 0);
        cxRepo.update(cx);
    }

    public List<ClasseContenu> getContenuForClasseCourse(Integer classeCourseId) {
        return cxRepo.findByClasseCourseId(classeCourseId);
    }

    public List<dto.lms.ContenuRowDto> getContenuForClasseCourseDto(Integer classeCourseId) {
        return cxRepo.findByClasseCourseId(classeCourseId).stream().map(cx -> new dto.lms.ContenuRowDto(
                cx.getId(),
                cx.getContenu() != null ? cx.getContenu().getId() : null,
                cx.getContenu() != null ? cx.getContenu().getTitle() : "?",
                cx.getContenu() != null ? cx.getContenu().getType() : "?",
                cx.getIsHidden() == 0 ? "Visible" : "Hidden",
                cx.getIsHidden() != 0
        )).collect(java.util.stream.Collectors.toList());
    }

    public List<ClasseContenu> getVisibleContenuForClasseCourse(Integer classeCourseId) {
        return cxRepo.findVisibleByClasseCourseId(classeCourseId);
    }

    public List<dto.lms.ContenuRowDto> getVisibleContenuForClasseCourseDto(Integer classeCourseId) {
        return cxRepo.findVisibleByClasseCourseId(classeCourseId).stream().map(cx -> new dto.lms.ContenuRowDto(
                cx.getId(),
                cx.getContenu() != null ? cx.getContenu().getId() : null,
                cx.getContenu() != null ? cx.getContenu().getTitle() : "?",
                cx.getContenu() != null ? cx.getContenu().getType() : "?",
                "Visible",
                false
        )).collect(java.util.stream.Collectors.toList());
    }

    public void deleteCourseFromClasse(Integer classeCourseId) {
        requireWriteAccessToClasseCourse(classeCourseId);
        ccRepo.delete(classeCourseId);
    }

    public void deleteContenuFromClasse(Integer classeContenuId) {
        requireWriteAccessToClasseContenu(classeContenuId);
        cxRepo.delete(classeContenuId);
    }
}
