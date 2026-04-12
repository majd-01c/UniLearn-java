package dto.lms;

public class ContenuRowDto {
    private final Integer classeContenuId;
    private final Integer contenuId;
    private final String title;
    private final String type;
    private final String hiddenLabel;
    private final boolean hidden;

    public ContenuRowDto(Integer classeContenuId, Integer contenuId, String title, String type, String hiddenLabel, boolean hidden) {
        this.classeContenuId = classeContenuId;
        this.contenuId = contenuId;
        this.title = title;
        this.type = type;
        this.hiddenLabel = hiddenLabel;
        this.hidden = hidden;
    }

    public Integer getClasseContenuId() { return classeContenuId; }
    public Integer getContenuId() { return contenuId; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getHiddenLabel() { return hiddenLabel; }
    public boolean isHidden() { return hidden; }
}
