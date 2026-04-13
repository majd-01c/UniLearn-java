package dto.lms;

public class ContenuOptionDto {
    private final Integer contenuId;
    private final String title;

    public ContenuOptionDto(Integer contenuId, String title) {
        this.contenuId = contenuId;
        this.title = title;
    }

    public Integer getContenuId() { return contenuId; }
    public String getTitle() { return title; }

    @Override
    public String toString() { return title == null ? "" : title; }
}
