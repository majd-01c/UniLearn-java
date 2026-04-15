package service.lms;

import entities.Module;
import entities.*;
import repository.lms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class ProgramService {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramService.class);
    private final ProgramRepository programRepo = new ProgramRepository();
    private final ProgramModuleRepository pmRepo = new ProgramModuleRepository();
    private final ModuleRepository moduleRepo = new ModuleRepository();

    public List<Program> listAll() { return programRepo.findAll(); }
    public Optional<Program> findById(Integer id) { return programRepo.findById(id); }
    public List<Program> filterByPublished(boolean published) { return programRepo.findByPublished(published); }
    public List<Program> searchByName(String name) { return programRepo.searchByName(name); }
    public long count() { return programRepo.count(); }

    public Program createProgram(String name) {
        util.RoleGuard.requireCurrentAdmin();
        Program p = new Program();
        p.setName(name.trim());
        p.setPublished((byte) 0);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return programRepo.save(p);
    }

    public Program updateProgram(Integer id, String name, boolean published) {
        util.RoleGuard.requireCurrentAdmin();
        Program p = programRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Program not found: " + id));
        p.setName(name.trim());
        p.setPublished(published ? (byte) 1 : (byte) 0);
        p.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return programRepo.update(p);
    }

    public void deleteProgram(Integer id) {
        util.RoleGuard.requireCurrentAdmin();
        programRepo.delete(id);
    }

    public List<ProgramModule> getModulesForProgram(Integer programId) {
        return pmRepo.findByProgramId(programId);
    }

    public ProgramModule assignModule(Integer programId, Integer moduleId) {
        util.RoleGuard.requireCurrentAdmin();
        if (pmRepo.findByProgramAndModule(programId, moduleId).isPresent()) {
            throw new IllegalStateException("Module is already assigned to this program.");
        }
        Program p = programRepo.findById(programId).orElseThrow(() -> new IllegalArgumentException("Program not found"));
        Module m = moduleRepo.findById(moduleId).orElseThrow(() -> new IllegalArgumentException("Module not found"));
        ProgramModule pm = new ProgramModule();
        pm.setProgram(p);
        pm.setModule(m);
        return pmRepo.save(pm);
    }

    public void removeModule(Integer programId, Integer moduleId) {
        util.RoleGuard.requireCurrentAdmin();
        ProgramModule pm = pmRepo.findByProgramAndModule(programId, moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        pmRepo.delete(pm.getId());
    }
}
