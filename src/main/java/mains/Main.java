package mains;

import entities.Course;
import services.ServiceCourse;

import java.sql.Timestamp;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        ServiceCourse serviceCourse = new ServiceCourse();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String baseTitle = "Java CRUD Demo " + now.getTime();

        Course newCourse = new Course();
        newCourse.setTitle(baseTitle);
        newCourse.setCreatedAt(now);
        newCourse.setUpdatedAt(now);

        System.out.println("Adding course...");
        serviceCourse.add(newCourse);

        Course insertedCourse = findCourseByTitle(serviceCourse.getALL(), baseTitle);
        if (insertedCourse == null) {
            System.out.println("Inserted course was not found.");
            return;
        }

        System.out.println("Inserted course:");
        printCourse(insertedCourse);

        insertedCourse.setTitle(baseTitle + " Updated");
        insertedCourse.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        System.out.println("Updating course...");
        serviceCourse.update(insertedCourse);

        Course updatedCourse = findCourseById(serviceCourse.getALL(), insertedCourse.getId());
        System.out.println("Updated course:");
        printCourse(updatedCourse);



         }

    private static Course findCourseByTitle(List<Course> courses, String title) {
        for (Course course : courses) {
            if (title.equals(course.getTitle())) {
                return course;
            }
        }
        return null;
    }

    private static Course findCourseById(List<Course> courses, Integer id) {
        for (Course course : courses) {
            if (id.equals(course.getId())) {
                return course;
            }
        }
        return null;
    }

    private static void printCourse(Course course) {
        if (course == null) {
            System.out.println("Course not found.");
            return;
        }

        System.out.println(
                "Course{id=" + course.getId()
                        + ", title='" + course.getTitle() + '\''
                        + ", createdAt=" + course.getCreatedAt()
                        + ", updatedAt=" + course.getUpdatedAt()
                        + '}'
        );
    }
}
