package service.lms;

import entities.*;
import repository.lms.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class CourseService {

    private final CourseRepository courseRepo = new CourseRepository();
    private final CourseContenuRepository ccRepo = new CourseContenuRepository();
    private final ContenuRepository contenuRepo = new ContenuRepository();

    public List<Course> listAll() { return courseRepo.findAll(); }
    public List<dto.lms.CourseOptionDto> listAllOptionsDto() {
        return courseRepo.findAll().stream()
                .map(c -> new dto.lms.CourseOptionDto(c.getId(), c.getTitle()))
                .collect(java.util.stream.Collectors.toList());
    }
    public Optional<Course> findById(Integer id) { return courseRepo.findById(id); }
    public long count() { return courseRepo.count(); }

    public Course createCourse(String title) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        Course c = new Course();
        c.setTitle(title.trim());
        Timestamp now = new Timestamp(System.currentTimeMillis());
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return courseRepo.save(c);
    }

    public Course updateCourse(Integer id, String title) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        Course c = courseRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        c.setTitle(title.trim());
        c.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return courseRepo.update(c);
    }

    public void deleteCourse(Integer id) { util.RoleGuard.requireCurrentAdminOrTeacher(); courseRepo.delete(id); }

    public List<CourseContenu> getContenuForCourse(Integer courseId) {
        return ccRepo.findByCourseIdOrdered(courseId);
    }

    public CourseContenu assignContenu(Integer courseId, Integer contenuId) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        if (ccRepo.findByCourseAndContenu(courseId, contenuId).isPresent()) {
            throw new IllegalStateException("Content is already assigned to this course.");
        }
        Course c = courseRepo.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        Contenu x = contenuRepo.findById(contenuId).orElseThrow(() -> new IllegalArgumentException("Content not found"));
        int pos = ccRepo.getNextPosition(courseId);
        CourseContenu cc = new CourseContenu();
        cc.setCourse(c);
        cc.setContenu(x);
        cc.setPosition(pos);
        return ccRepo.save(cc);
    }

    public void removeContenu(Integer courseId, Integer contenuId) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        CourseContenu cc = ccRepo.findByCourseAndContenu(courseId, contenuId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        ccRepo.delete(cc.getId());
    }

    public void reorderContenu(Integer courseId, List<Integer> orderedContenuIds) {
        util.RoleGuard.requireCurrentAdminOrTeacher();
        List<CourseContenu> items = ccRepo.findByCourseIdOrdered(courseId);
        for (int i = 0; i < orderedContenuIds.size(); i++) {
            Integer contenuId = orderedContenuIds.get(i);
            for (CourseContenu cc : items) {
                if (cc.getContenu().getId().equals(contenuId)) {
                    cc.setPosition(i + 1);
                    break;
                }
            }
        }
        ccRepo.updatePositions(items);
    }
}
