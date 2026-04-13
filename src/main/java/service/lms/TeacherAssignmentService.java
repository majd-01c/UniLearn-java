package service.lms;

import entities.*;
import entities.Module;
import repository.lms.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class TeacherAssignmentService {

    private final TeacherClasseRepository tcRepo = new TeacherClasseRepository();
    private final ClasseRepository classeRepo = new ClasseRepository();
    private final ModuleRepository moduleRepo = new ModuleRepository();
    private final ClasseModuleRepository cmRepo = new ClasseModuleRepository();

    public TeacherClasse assignTeacher(Integer teacherId, Integer classeId) {
        util.RoleGuard.requireCurrentAdmin();
        org.hibernate.Session session = util.HibernateSessionFactory.getSession();
        User teacher;
        try {
            teacher = session.get(User.class, teacherId);
        } finally {
            util.HibernateSessionFactory.closeSession();
        }
        if (teacher == null) {
            throw new IllegalArgumentException("Teacher not found.");
        }
        String role = normalizeRole(teacher);
        if (!"TEACHER".equals(role)) {
            throw new IllegalArgumentException("Only users with TEACHER role can be assigned.");
        }
        if (tcRepo.findByTeacherAndClasse(teacherId, classeId).isPresent()) {
            throw new IllegalStateException("Teacher is already assigned to this class.");
        }
        Classe classe = classeRepo.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found."));

        TeacherClasse tc = new TeacherClasse();
        tc.setUser(teacher);
        tc.setClasse(classe);
        tc.setAssignedAt(new Timestamp(System.currentTimeMillis()));
        tc.setIsActive((byte) 1);
        tc.setHasCreatedModule((byte) 0);
        return tcRepo.save(tc);
    }

    public void removeTeacher(Integer teacherClasseId) {
        util.RoleGuard.requireCurrentAdmin();
        tcRepo.delete(teacherClasseId);
    }

    public void toggleTeacherActive(Integer teacherClasseId) {
        util.RoleGuard.requireCurrentAdmin();
        TeacherClasse tc = tcRepo.findById(teacherClasseId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found."));
        tc.setIsActive(tc.getIsActive() == 1 ? (byte) 0 : (byte) 1);
        tcRepo.update(tc);
    }

    public TeacherClasse createModuleForAssignment(Integer teacherClasseId, String moduleName, String periodUnit, int duration) {
        // Only teachers can create their own module
        util.RoleGuard.requireCurrentTeacher();
        TeacherClasse tc = tcRepo.findById(teacherClasseId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found."));
        // Ownership check: logged-in teacher must own this assignment
        Integer currentUserId = security.UserSession.getCurrentUserId().orElse(null);
        if (tc.getUser() == null || !tc.getUser().getId().equals(currentUserId)) {
            throw new SecurityException("Access denied: you can only create a module for your own assignment.");
        }
        // Active check
        if (tc.getIsActive() != 1) {
            throw new SecurityException("Access denied: your assignment to this class is not active.");
        }
        if (tc.getHasCreatedModule() == 1) {
            throw new IllegalStateException("Teacher has already created a module for this assignment.");
        }

        // Create the module
        Module m = new Module();
        m.setName(moduleName.trim());
        m.setPeriodUnit(periodUnit);
        m.setDuration(duration);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        m = moduleRepo.save(m);

        // Link module to teacher assignment
        tc.setModule(m);
        tc.setHasCreatedModule((byte) 1);
        tc = tcRepo.update(tc);

        // Create ClasseModule link
        ClasseModule cm = new ClasseModule();
        cm.setClasse(tc.getClasse());
        cm.setModule(m);
        cmRepo.save(cm);

        return tc;
    }

    public boolean canAssignProgram(Integer classeId) {
        List<TeacherClasse> active = tcRepo.findActiveByClasseId(classeId);
        return !active.isEmpty() && active.stream().allMatch(tc -> tc.getHasCreatedModule() == 1);
    }

    public String getProgramAssignmentBlockReason(Integer classeId) {
        List<TeacherClasse> active = tcRepo.findActiveByClasseId(classeId);
        if (active.isEmpty()) return "No active teachers assigned.";
        long pending = active.stream().filter(tc -> tc.getHasCreatedModule() == 0).count();
        if (pending > 0) return pending + " active teacher(s) have not created their module yet.";
        return null;
    }

    public List<TeacherClasse> getTeachersForClasse(Integer classeId) {
        return tcRepo.findByClasseId(classeId);
    }

    public List<dto.lms.TeacherAssignmentRowDto> getTeachersForClasseDto(Integer classeId) {
        return tcRepo.findByClasseId(classeId).stream().map(tc -> new dto.lms.TeacherAssignmentRowDto(
                tc.getId(),
                tc.getUser() != null ? tc.getUser().getId() : null,
                tc.getUser() != null ? tc.getUser().getEmail() : "?",
                tc.getIsActive() == 1 ? "Yes" : "No",
                tc.getHasCreatedModule() == 1 ? "Yes" : "No",
                tc.getModule() != null ? tc.getModule().getName() : "—",
                tc.getClasse() != null ? tc.getClasse().getId() : null,
                tc.getClasse() != null ? tc.getClasse().getName() : "?",
                tc.getClasse() != null && tc.getClasse().getProgram() != null ? tc.getClasse().getProgram().getName() : "—",
                tc.getClasse() != null ? tc.getClasse().getLevel() : "?",
                tc.getClasse() != null ? tc.getClasse().getSpecialty() : "?",
                tc.getModule() != null ? tc.getModule().getId() : null,
                tc.getModule() != null ? tc.getModule().getDuration() + " " + tc.getModule().getPeriodUnit() : ""
        )).collect(java.util.stream.Collectors.toList());
    }

    public List<TeacherClasse> getActiveClassesForTeacher(Integer teacherId) {
        return tcRepo.findActiveByTeacherId(teacherId);
    }

    public List<dto.lms.TeacherAssignmentRowDto> getActiveClassesForTeacherDto(Integer teacherId) {
        return tcRepo.findActiveByTeacherId(teacherId).stream().map(tc -> new dto.lms.TeacherAssignmentRowDto(
                tc.getId(),
                tc.getUser() != null ? tc.getUser().getId() : null,
                tc.getUser() != null ? tc.getUser().getEmail() : "?",
                tc.getIsActive() == 1 ? "Yes" : "No",
                tc.getHasCreatedModule() == 1 ? "Yes" : "No",
                tc.getModule() != null ? tc.getModule().getName() : "—",
                tc.getClasse() != null ? tc.getClasse().getId() : null,
                tc.getClasse() != null ? tc.getClasse().getName() : "?",
                tc.getClasse() != null && tc.getClasse().getProgram() != null ? tc.getClasse().getProgram().getName() : "—",
                tc.getClasse() != null ? tc.getClasse().getLevel() : "?",
                tc.getClasse() != null ? tc.getClasse().getSpecialty() : "?",
                tc.getModule() != null ? tc.getModule().getId() : null,
                tc.getModule() != null ? tc.getModule().getDuration() + " " + tc.getModule().getPeriodUnit() : ""
        )).collect(java.util.stream.Collectors.toList());
    }

    public Optional<TeacherClasse> findById(Integer id) {
        return tcRepo.findById(id);
    }

    private String normalizeRole(User u) {
        if (u == null || u.getRole() == null) return "";
        String r = u.getRole().trim().toUpperCase();
        return r.startsWith("ROLE_") ? r.substring(5) : r;
    }
}
