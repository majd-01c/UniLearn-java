package dto.job_offer;

import java.sql.Timestamp;

/**
 * Data Transfer Object for JobApplication entity.
 * Used for transferring application data between layers.
 * Immutable design.
 */
public class JobApplicationDto {
    private final Integer applicationId;
    private final Integer offerId;
    private final String offerTitle;
    private final Integer studentId;
    private final String studentEmail;
    private final String message;
    private final String cvFileName;
    private final String status;            // Enum value as String
    private final Integer score;
    private final String scoreBreakdown;
    private final Timestamp createdAt;
    private final Timestamp updatedAt;
    private final Timestamp scoredAt;
    private final String extractedData;
    private final Byte statusNotified;
    private final Timestamp statusNotifiedAt;
    private final String statusMessage;

    public JobApplicationDto(Integer applicationId, Integer offerId, String offerTitle,
                            Integer studentId, String studentEmail, String message,
                            String cvFileName, String status, Integer score,
                            String scoreBreakdown, Timestamp createdAt, Timestamp updatedAt,
                            Timestamp scoredAt, String extractedData, Byte statusNotified,
                            Timestamp statusNotifiedAt, String statusMessage) {
        this.applicationId = applicationId;
        this.offerId = offerId;
        this.offerTitle = offerTitle;
        this.studentId = studentId;
        this.studentEmail = studentEmail;
        this.message = message;
        this.cvFileName = cvFileName;
        this.status = status;
        this.score = score;
        this.scoreBreakdown = scoreBreakdown;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.scoredAt = scoredAt;
        this.extractedData = extractedData;
        this.statusNotified = statusNotified;
        this.statusNotifiedAt = statusNotifiedAt;
        this.statusMessage = statusMessage;
    }

    // Getters
    public Integer getApplicationId() { return applicationId; }
    public Integer getOfferId() { return offerId; }
    public String getOfferTitle() { return offerTitle; }
    public Integer getStudentId() { return studentId; }
    public String getStudentEmail() { return studentEmail; }
    public String getMessage() { return message; }
    public String getCvFileName() { return cvFileName; }
    public String getStatus() { return status; }
    public Integer getScore() { return score; }
    public String getScoreBreakdown() { return scoreBreakdown; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public Timestamp getScoredAt() { return scoredAt; }
    public String getExtractedData() { return extractedData; }
    public Byte getStatusNotified() { return statusNotified; }
    public Timestamp getStatusNotifiedAt() { return statusNotifiedAt; }
    public String getStatusMessage() { return statusMessage; }

    @Override
    public String toString() {
        return studentEmail != null && offerTitle != null 
            ? studentEmail + " → " + offerTitle 
            : "Application #" + applicationId;
    }
}
