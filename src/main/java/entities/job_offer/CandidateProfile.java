package entities.job_offer;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured candidate profile extracted from a CV by Gemini (or entered manually).
 * Serialized as JSON and stored in job_application.extracted_data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidateProfile {

    private List<String> skills = new ArrayList<>();
    @JsonAlias("yearsOfExperience")
    private int experienceYears;
    private String educationLevel;
    private String educationField;
    private List<String> languages = new ArrayList<>();
    private List<String> portfolioUrls = new ArrayList<>();

    public CandidateProfile() {}

    /**
     * Numeric rank for education comparison (higher = more qualified).
     */
    public static int educationRank(String level) {
        if (level == null || level.isBlank()) return 0;
        return switch (level.trim().toLowerCase()) {
            case "bac"        -> 1;
            case "bac+2"      -> 2;
            case "licence"    -> 3;
            case "master"     -> 4;
            case "ingenieur"  -> 4;
            case "doctorat"   -> 5;
            default           -> 0;
        };
    }

    // ── getters / setters ──────────────────────────────────────────────────────

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }

    public int getYearsOfExperience() { return experienceYears; }
    public void setYearsOfExperience(int yearsOfExperience) { this.experienceYears = yearsOfExperience; }

    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }

    public String getEducationField() { return educationField; }
    public void setEducationField(String educationField) { this.educationField = educationField; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }

    public List<String> getPortfolioUrls() { return portfolioUrls; }
    public void setPortfolioUrls(List<String> portfolioUrls) { this.portfolioUrls = portfolioUrls; }
}
