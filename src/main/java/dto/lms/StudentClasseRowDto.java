package dto.lms;

public class StudentClasseRowDto {
    private final Integer classeId;
    private final String classeName;
    private final String programName;
    private final String level;
    private final String specialty;
    private final String status;

    public StudentClasseRowDto(Integer classeId, String classeName, String programName, 
                               String level, String specialty, String status) {
        this.classeId = classeId;
        this.classeName = classeName;
        this.programName = programName;
        this.level = level;
        this.specialty = specialty;
        this.status = status;
    }

    public Integer getClasseId() { return classeId; }
    public String getClasseName() { return classeName; }
    public String getProgramName() { return programName; }
    public String getLevel() { return level; }
    public String getSpecialty() { return specialty; }
    public String getStatus() { return status; }
}
