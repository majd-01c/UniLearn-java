package dto.job_offer;

/**
 * DTO for ComboBox display of job offers (dropdown selection).
 * Minimal data: just offer ID and title.
 */
public class JobOfferOptionDto {
    private final Integer offerId;
    private final String title;

    public JobOfferOptionDto(Integer offerId, String title) {
        this.offerId = offerId;
        this.title = title;
    }

    public Integer getOfferId() { return offerId; }
    public String getTitle() { return title; }

    @Override
    public String toString() {
        return title != null ? title : ("#" + offerId);
    }
}
