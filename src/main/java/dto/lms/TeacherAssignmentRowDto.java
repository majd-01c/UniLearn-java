package dto.lms;

public class TeacherAssignmentRowDto {
    private final Integer id;
    private final Integer teacherId;
    private final String email;
    private final String active;
    private final String hasCreatedModule;
    private final String moduleName;
    private final Integer classeId;
    private final String classeName;
    private final String classeProgram;
    private final String classeLevel;
    private final String classeSpecialty;
    private final Integer moduleId;
    private final String moduleDurationLabel;

    public TeacherAssignmentRowDto(Integer id, Integer teacherId, String email, String active, 
                                   String hasCreatedModule, String moduleName, Integer classeId, 
                                   String classeName, String classeProgram, String classeLevel, 
                                   String classeSpecialty, Integer moduleId, String moduleDurationLabel) {
        this.id = id;
        this.teacherId = teacherId;
        this.email = email;
        this.active = active;
        this.hasCreatedModule = hasCreatedModule;
        this.moduleName = moduleName;
        this.classeId = classeId;
        this.classeName = classeName;
        this.classeProgram = classeProgram;
        this.classeLevel = classeLevel;
        this.classeSpecialty = classeSpecialty;
        this.moduleId = moduleId;
        this.moduleDurationLabel = moduleDurationLabel;
    }

    public Integer getId() { return id; }
    public Integer getTeacherId() { return teacherId; }
    public String getEmail() { return email; }
    public String getActive() { return active; }
    public String getHasCreatedModule() { return hasCreatedModule; }
    public String getModuleName() { return moduleName; }
    public Integer getClasseId() { return classeId; }
    public String getClasseName() { return classeName; }
    public String getClasseProgram() { return classeProgram; }
    public String getClasseLevel() { return classeLevel; }
    public String getClasseSpecialty() { return classeSpecialty; }
    public Integer getModuleId() { return moduleId; }
    public String getModuleDurationLabel() { return moduleDurationLabel; }
}
