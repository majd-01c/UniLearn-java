package dto.job_offer;

import java.sql.Timestamp;

/**
 * DTO for partner review queue (applications to review).
 * Used in ApplicationReviewController to display applications awaiting partner review.
 */
public class ApplicationReviewRowDto {
    private final Integer applicationId;
    private final Integer offerId;
    private final String offerTitle;
    private final String studentEmail;
    private final String status;
    private final Integer score;
    private final Timestamp createdAt;

    public ApplicationReviewRowDto(Integer applicationId, Integer offerId, String offerTitle,
                                  String studentEmail, String status, Integer score,
                                  Timestamp createdAt) {
        this.applicationId = applicationId;
        this.offerId = offerId;
        this.offerTitle = offerTitle;
        this.studentEmail = studentEmail;
        this.status = status;
        this.score = score;
        this.createdAt = createdAt;
    }

    public Integer getApplicationId() { return applicationId; }
    public Integer getOfferId() { return offerId; }
    public String getOfferTitle() { return offerTitle; }
    public String getStudentEmail() { return studentEmail; }
    public String getStatus() { return status; }
    public Integer getScore() { return score; }
    public Timestamp getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return studentEmail + " - " + offerTitle;
    }
}
