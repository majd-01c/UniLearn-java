package dto.lms;

import java.util.Date;

public class StudentEnrollmentRowDto {
    private final Integer id;
    private final Integer studentId;
    private final String email;
    private final String active;
    private final String enrolledAt;

    public StudentEnrollmentRowDto(Integer id, Integer studentId, String email, String active, String enrolledAt) {
        this.id = id;
        this.studentId = studentId;
        this.email = email;
        this.active = active;
        this.enrolledAt = enrolledAt;
    }

    public Integer getId() { return id; }
    public Integer getStudentId() { return studentId; }
    public String getEmail() { return email; }
    public String getActive() { return active; }
    public String getEnrolledAt() { return enrolledAt; }
}
