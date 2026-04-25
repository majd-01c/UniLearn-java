package entities.job_offer;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured candidate profile extracted from a CV by Gemini (or entered manually).
 * Serialized as JSON and stored in job_application.extracted_data.
 */
public class CandidateProfile {

    private List<String> skills = new ArrayList<>();
    private int yearsOfExperience;
    private String educationLevel;   // HIGH_SCHOOL | BACHELOR | MASTER | PHD
    private List<String> languages = new ArrayList<>();
    private String currentTitle;
    private String summary;
    private List<String> keywords = new ArrayList<>();

    public CandidateProfile() {}

    // ── Education level constants ─────────────────────────────────────────────

    public static final String EDU_HIGH_SCHOOL = "HIGH_SCHOOL";
    public static final String EDU_BACHELOR    = "BACHELOR";
    public static final String EDU_MASTER      = "MASTER";
    public static final String EDU_PHD         = "PHD";

    /**
     * Numeric rank for education comparison (higher = more qualified).
     */
    public static int educationRank(String level) {
        if (level == null) return 0;
        return switch (level.toUpperCase().trim()) {
            case "PHD"         -> 4;
            case "MASTER"      -> 3;
            case "BACHELOR"    -> 2;
            case "HIGH_SCHOOL" -> 1;
            default            -> 0;
        };
    }

    // ── getters / setters ──────────────────────────────────────────────────────

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public int getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(int yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }

    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }

    public String getCurrentTitle() { return currentTitle; }
    public void setCurrentTitle(String currentTitle) { this.currentTitle = currentTitle; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
}
