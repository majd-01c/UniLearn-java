package service.lms;

import entities.*;
import repository.lms.*;

import java.util.List;
import java.util.Optional;

public class ClasseService {

    private final ClasseRepository classeRepo = new ClasseRepository();
    private final ProgramRepository programRepo = new ProgramRepository();
    private final TeacherAssignmentService teacherService = new TeacherAssignmentService();

    public List<Classe> listAll() { return classeRepo.findAll(); }
    public Optional<Classe> findById(Integer id) { return classeRepo.findById(id); }
    public List<Classe> findByStatus(String status) { return classeRepo.findByStatus(status); }
    public long count() { return classeRepo.count(); }

    public Classe createClasse(String name, Integer programId, String level, String specialty,
                                int capacity, java.sql.Date startDate, java.sql.Date endDate, String imageUrl) {
        util.RoleGuard.requireCurrentAdmin();
        Program program = programRepo.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + programId));
        Classe c = new Classe();
        c.setName(name.trim());
        c.setProgram(program);
        c.setLevel(level);
        c.setSpecialty(specialty);
        c.setCapacity(capacity);
        c.setStartDate(startDate);
        c.setEndDate(endDate);
        c.setImageUrl(imageUrl);
        c.setStatus("active");
        return classeRepo.save(c);
    }

    public Classe updateClasse(Integer id, String name, String level, String specialty,
                                int capacity, java.sql.Date startDate, java.sql.Date endDate, String status, String imageUrl) {
        util.RoleGuard.requireCurrentAdmin();
        Classe c = classeRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Class not found: " + id));
        c.setName(name.trim());
        c.setLevel(level);
        c.setSpecialty(specialty);
        c.setCapacity(capacity);
        c.setStartDate(startDate);
        c.setEndDate(endDate);
        if (status != null) c.setStatus(status);
        if (imageUrl != null) c.setImageUrl(imageUrl);
        return classeRepo.update(c);
    }

    public void deleteClasse(Integer id) { util.RoleGuard.requireCurrentAdmin(); classeRepo.delete(id); }

    public long countActiveStudents(Integer classeId) {
        return classeRepo.countActiveStudents(classeId);
    }

    public void assignProgram(Integer classeId, Integer programId) {
        util.RoleGuard.requireCurrentAdmin();
        if (!teacherService.canAssignProgram(classeId)) {
            String reason = teacherService.getProgramAssignmentBlockReason(classeId);
            throw new IllegalStateException("Cannot assign program: " + reason);
        }
        Program program = programRepo.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found"));
        Classe classe = classeRepo.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found"));
        classe.setProgram(program);
        classeRepo.update(classe);
    }
}
