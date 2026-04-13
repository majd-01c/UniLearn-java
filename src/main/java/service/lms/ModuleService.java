package service.lms;

import entities.Module;
import entities.*;
import repository.lms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class ModuleService {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleService.class);
    private final ModuleRepository moduleRepo = new ModuleRepository();
    private final ModuleCourseRepository mcRepo = new ModuleCourseRepository();
    private final CourseRepository courseRepo = new CourseRepository();

    public List<Module> listAll() { return moduleRepo.findAll(); }
    public Optional<Module> findById(Integer id) { return moduleRepo.findById(id); }
    public long count() { return moduleRepo.count(); }

    public Module createModule(String name, String periodUnit, int duration) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        Module m = new Module();
        m.setName(name.trim());
        m.setPeriodUnit(periodUnit);
        m.setDuration(duration);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        return moduleRepo.save(m);
    }

    public Module updateModule(Integer id, String name, String periodUnit, int duration) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        Module m = moduleRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Module not found: " + id));
        m.setName(name.trim());
        m.setPeriodUnit(periodUnit);
        m.setDuration(duration);
        m.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return moduleRepo.update(m);
    }

    public void deleteModule(Integer id) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        moduleRepo.delete(id);
    }

    public List<ModuleCourse> getCoursesForModule(Integer moduleId) {
        return mcRepo.findByModuleId(moduleId);
    }

    public ModuleCourse assignCourse(Integer moduleId, Integer courseId) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        if (mcRepo.findByModuleAndCourse(moduleId, courseId).isPresent()) {
            throw new IllegalStateException("Course is already assigned to this module.");
        }
        Module m = moduleRepo.findById(moduleId).orElseThrow(() -> new IllegalArgumentException("Module not found"));
        Course c = courseRepo.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        ModuleCourse mc = new ModuleCourse();
        mc.setModule(m);
        mc.setCourse(c);
        return mcRepo.save(mc);
    }

    public void removeCourse(Integer moduleId, Integer courseId) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        ModuleCourse mc = mcRepo.findByModuleAndCourse(moduleId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        mcRepo.delete(mc.getId());
    }
}
