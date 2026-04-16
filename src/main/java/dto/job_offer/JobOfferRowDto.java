package dto.job_offer;

import java.sql.Timestamp;

/**
 * DTO for table row display of job offers (list view).
 * Used in JobOfferListController to bind table columns.
 */
public class JobOfferRowDto {
    private final Integer offerId;
    private final String title;
    private final String type;
    private final String status;
    private final String location;
    private final String partnerEmail;
    private final Integer applicationCount;
    private final Timestamp createdAt;

    public JobOfferRowDto(Integer offerId, String title, String type, String status, 
                         String location, String partnerEmail, Integer applicationCount, 
                         Timestamp createdAt) {
        this.offerId = offerId;
        this.title = title;
        this.type = type;
        this.status = status;
        this.location = location;
        this.partnerEmail = partnerEmail;
        this.applicationCount = applicationCount;
        this.createdAt = createdAt;
    }

    public Integer getOfferId() { return offerId; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getLocation() { return location; }
    public String getPartnerEmail() { return partnerEmail; }
    public Integer getApplicationCount() { return applicationCount; }
    public Timestamp getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return title;
    }
}
