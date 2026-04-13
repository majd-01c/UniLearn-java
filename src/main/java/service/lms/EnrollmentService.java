package service.lms;

import entities.*;
import repository.lms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class EnrollmentService {

    private static final Logger LOG = LoggerFactory.getLogger(EnrollmentService.class);
    private final StudentClasseRepository scRepo = new StudentClasseRepository();
    private final ClasseRepository classeRepo = new ClasseRepository();

    public StudentClasse enrollStudent(Integer studentId, Integer classeId) {
        util.RoleGuard.requireCurrentAdmin();
        org.hibernate.Session session = util.HibernateSessionFactory.getSession();
        User student;
        try {
            student = session.get(User.class, studentId);
        } finally {
            util.HibernateSessionFactory.closeSession();
        }
        if (student == null) {
            throw new IllegalArgumentException("Student not found.");
        }
        // Check student role
        if (!"STUDENT".equals(normalizeRole(student))) {
            throw new IllegalArgumentException("Only users with STUDENT role can be enrolled.");
        }

        // Check class exists and not full
        Classe classe = classeRepo.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found."));
        if ("full".equals(classe.getStatus())) {
            throw new IllegalStateException("Cannot enroll: class is full.");
        }

        // Check student not already enrolled in another active class
        if (scRepo.hasActiveEnrollment(studentId)) {
            throw new IllegalStateException("Student already has an active enrollment in another class.");
        }

        // Check not already enrolled in this class
        if (scRepo.findByStudentAndClasse(studentId, classeId).isPresent()) {
            throw new IllegalStateException("Student is already enrolled in this class.");
        }

        // Create enrollment
        StudentClasse sc = new StudentClasse();
        sc.setUser(student);
        sc.setClasse(classe);
        sc.setEnrolledAt(new Timestamp(System.currentTimeMillis()));
        sc.setIsActive((byte) 1);
        sc = scRepo.save(sc);

        // Check if class is now full
        long activeCount = classeRepo.countActiveStudents(classeId);
        if (activeCount >= classe.getCapacity()) {
            classe.setStatus("full");
            classeRepo.update(classe);
        }

        return sc;
    }

    public void unenrollStudent(Integer studentClasseId) {
        util.RoleGuard.requireCurrentAdmin();
        StudentClasse sc = scRepo.findById(studentClasseId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found."));
        sc.setIsActive((byte) 0);
        scRepo.update(sc);

        // Check if class should become active again
        Classe classe = sc.getClasse();
        if ("full".equals(classe.getStatus())) {
            long activeCount = classeRepo.countActiveStudents(classe.getId());
            if (activeCount < classe.getCapacity()) {
                classe.setStatus("active");
                classeRepo.update(classe);
            }
        }
    }

    public void toggleEnrollmentActive(Integer studentClasseId) {
        util.RoleGuard.requireCurrentAdmin();
        StudentClasse sc = scRepo.findById(studentClasseId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found."));
        byte newActive = sc.getIsActive() == 1 ? (byte) 0 : (byte) 1;

        if (newActive == 1) {
            // Reactivating – check not already active elsewhere
            if (scRepo.hasActiveEnrollment(sc.getUser().getId())) {
                throw new IllegalStateException("Student already has an active enrollment.");
            }
            Classe classe = sc.getClasse();
            long activeCount = classeRepo.countActiveStudents(classe.getId());
            if (activeCount >= classe.getCapacity()) {
                throw new IllegalStateException("Cannot reactivate: class is full.");
            }
        }

        sc.setIsActive(newActive);
        scRepo.update(sc);

        // Update class status
        Classe classe = sc.getClasse();
        long activeCount = classeRepo.countActiveStudents(classe.getId());
        if (activeCount >= classe.getCapacity() && !"full".equals(classe.getStatus())) {
            classe.setStatus("full");
            classeRepo.update(classe);
        } else if (activeCount < classe.getCapacity() && "full".equals(classe.getStatus())) {
            classe.setStatus("active");
            classeRepo.update(classe);
        }
    }

    public List<StudentClasse> getStudentsForClasse(Integer classeId) {
        return scRepo.findByClasseId(classeId);
    }

    public List<dto.lms.StudentEnrollmentRowDto> getStudentsForClasseDto(Integer classeId) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        return scRepo.findByClasseId(classeId).stream().map(sc -> new dto.lms.StudentEnrollmentRowDto(
                sc.getId(), 
                sc.getUser() != null ? sc.getUser().getId() : null, 
                sc.getUser() != null ? sc.getUser().getEmail() : "?", 
                sc.getIsActive() == 1 ? "Yes" : "No", 
                sc.getEnrolledAt() != null ? sdf.format(sc.getEnrolledAt()) : ""
        )).collect(java.util.stream.Collectors.toList());
    }

    public List<StudentClasse> getActiveEnrollmentsForStudent(Integer studentId) {
        return scRepo.findActiveByStudentId(studentId);
    }

    public List<dto.lms.StudentClasseRowDto> getActiveEnrollmentsForStudentDto(Integer studentId) {
        return scRepo.findActiveByStudentId(studentId).stream().map(sc -> {
            entities.Classe c = sc.getClasse();
            return new dto.lms.StudentClasseRowDto(
                    c.getId(),
                    c.getName(),
                    c.getProgram() != null ? c.getProgram().getName() : "—",
                    c.getLevel(),
                    c.getSpecialty(),
                    c.getStatus()
            );
        }).collect(java.util.stream.Collectors.toList());
    }

    private String normalizeRole(User u) {
        if (u == null || u.getRole() == null) return "";
        String r = u.getRole().trim().toUpperCase();
        return r.startsWith("ROLE_") ? r.substring(5) : r;
    }
}
