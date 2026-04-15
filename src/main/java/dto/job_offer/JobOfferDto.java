package dto.job_offer;

import java.sql.Timestamp;

/**
 * Data Transfer Object for JobOffer entity.
 * Used for transferring job offer data between layers and for display.
 * Immutable design: final fields, no setters, constructor-based initialization.
 */
public class JobOfferDto {
    private final Integer offerId;
    private final Integer partnerId;
    private final String partnerEmail;
    private final String title;
    private final String type;          // Enum value as String
    private final String status;        // Enum value as String
    private final String location;
    private final String description;
    private final String requirements;
    private final String requiredSkills;
    private final String preferredSkills;
    private final Integer minExperienceYears;
    private final String minEducation;
    private final String requiredLanguages;
    private final Timestamp createdAt;
    private final Timestamp updatedAt;
    private final Timestamp publishedAt;
    private final Timestamp expiresAt;
    private final Integer applicationCount;

    public JobOfferDto(Integer offerId, Integer partnerId, String partnerEmail, String title, 
                       String type, String status, String location, String description, 
                       String requirements, String requiredSkills, String preferredSkills,
                       Integer minExperienceYears, String minEducation, String requiredLanguages,
                       Timestamp createdAt, Timestamp updatedAt, Timestamp publishedAt, 
                       Timestamp expiresAt, Integer applicationCount) {
        this.offerId = offerId;
        this.partnerId = partnerId;
        this.partnerEmail = partnerEmail;
        this.title = title;
        this.type = type;
        this.status = status;
        this.location = location;
        this.description = description;
        this.requirements = requirements;
        this.requiredSkills = requiredSkills;
        this.preferredSkills = preferredSkills;
        this.minExperienceYears = minExperienceYears;
        this.minEducation = minEducation;
        this.requiredLanguages = requiredLanguages;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
        this.expiresAt = expiresAt;
        this.applicationCount = applicationCount;
    }

    // Getters
    public Integer getOfferId() { return offerId; }
    public Integer getPartnerId() { return partnerId; }
    public String getPartnerEmail() { return partnerEmail; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public String getRequirements() { return requirements; }
    public String getRequiredSkills() { return requiredSkills; }
    public String getPreferredSkills() { return preferredSkills; }
    public Integer getMinExperienceYears() { return minExperienceYears; }
    public String getMinEducation() { return minEducation; }
    public String getRequiredLanguages() { return requiredLanguages; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public Timestamp getPublishedAt() { return publishedAt; }
    public Timestamp getExpiresAt() { return expiresAt; }
    public Integer getApplicationCount() { return applicationCount; }

    @Override
    public String toString() {
        return title != null ? title : "Job Offer #" + offerId;
    }
}
