package dto.lms;

public class ModuleRowDto {
    private final Integer classeModuleId;
    private final Integer moduleId;
    private final String moduleName;
    private final String durationLabel;

    public ModuleRowDto(Integer classeModuleId, Integer moduleId, String moduleName, String durationLabel) {
        this.classeModuleId = classeModuleId;
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.durationLabel = durationLabel;
    }

    public Integer getClasseModuleId() { return classeModuleId; }
    public Integer getModuleId() { return moduleId; }
    public String getModuleName() { return moduleName; }
    public String getDurationLabel() { return durationLabel; }
}
