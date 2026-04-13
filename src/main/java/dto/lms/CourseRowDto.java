package dto.lms;

public class CourseRowDto {
    private final Integer classeCourseId;
    private final Integer courseId;
    private final String title;
    private final String hiddenLabel;
    private final boolean hidden;

    public CourseRowDto(Integer classeCourseId, Integer courseId, String title, String hiddenLabel, boolean hidden) {
        this.classeCourseId = classeCourseId;
        this.courseId = courseId;
        this.title = title;
        this.hiddenLabel = hiddenLabel;
        this.hidden = hidden;
    }

    public Integer getClasseCourseId() { return classeCourseId; }
    public Integer getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public String getHiddenLabel() { return hiddenLabel; }
    public boolean isHidden() { return hidden; }
}
