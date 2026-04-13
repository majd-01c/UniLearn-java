package dto.lms;

public class CourseOptionDto {
    private final Integer courseId;
    private final String title;

    public CourseOptionDto(Integer courseId, String title) {
        this.courseId = courseId;
        this.title = title;
    }

    public Integer getCourseId() { return courseId; }
    public String getTitle() { return title; }

    @Override
    public String toString() { return title == null ? "" : title; }
}
