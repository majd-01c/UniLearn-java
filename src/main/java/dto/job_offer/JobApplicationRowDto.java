package dto.job_offer;

import java.sql.Timestamp;

/**
 * DTO for table row display of student job applications (list view).
 * Used in MyApplicationsController to show student's submitted applications.
 */
public class JobApplicationRowDto {
    private final Integer applicationId;
    private final Integer offerId;
    private final String offerTitle;
    private final String offerType;
    private final String status;
    private final Integer score;
    private final Timestamp createdAt;
    private final Timestamp updatedAt;

    public JobApplicationRowDto(Integer applicationId, Integer offerId, String offerTitle,
                               String offerType, String status, Integer score,
                               Timestamp createdAt, Timestamp updatedAt) {
        this.applicationId = applicationId;
        this.offerId = offerId;
        this.offerTitle = offerTitle;
        this.offerType = offerType;
        this.status = status;
        this.score = score;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Integer getApplicationId() { return applicationId; }
    public Integer getOfferId() { return offerId; }
    public String getOfferTitle() { return offerTitle; }
    public String getOfferType() { return offerType; }
    public String getStatus() { return status; }
    public Integer getScore() { return score; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return offerTitle;
    }
}
