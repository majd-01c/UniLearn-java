package entities.job_offer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single scoring criterion in an ATS evaluation.
 * Serialized as part of ScoreBreakdown JSON stored in job_application.score_breakdown.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreCriteria {

    private String name;
    private int weight;
    private double rawMatch;
    private int pointsAwarded;
    private List<String> matched = new ArrayList<>();
    private List<String> missing = new ArrayList<>();
    private Integer total;
    private String candidateLevel;
    private String requiredLevel;
    private Integer candidateYears;
    private Integer requiredYears;
    private Boolean meetsRequirement;

    public ScoreCriteria() {}

    public ScoreCriteria(String name, int weight, double rawMatch, int pointsAwarded) {
        this.name = name;
        this.weight = weight;
        this.rawMatch = rawMatch;
        this.pointsAwarded = pointsAwarded;
    }

    // ── getters / setters ──────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public double getRawMatch() { return rawMatch; }
    public void setRawMatch(double rawMatch) { this.rawMatch = rawMatch; }

    public int getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(int pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public List<String> getMatched() { return matched; }
    public void setMatched(List<String> matched) { this.matched = matched; }

    public List<String> getMissing() { return missing; }
    public void setMissing(List<String> missing) { this.missing = missing; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public String getCandidateLevel() { return candidateLevel; }
    public void setCandidateLevel(String candidateLevel) { this.candidateLevel = candidateLevel; }

    public String getRequiredLevel() { return requiredLevel; }
    public void setRequiredLevel(String requiredLevel) { this.requiredLevel = requiredLevel; }

    public Integer getCandidateYears() { return candidateYears; }
    public void setCandidateYears(Integer candidateYears) { this.candidateYears = candidateYears; }

    public Integer getRequiredYears() { return requiredYears; }
    public void setRequiredYears(Integer requiredYears) { this.requiredYears = requiredYears; }

    public Boolean getMeetsRequirement() { return meetsRequirement; }
    public void setMeetsRequirement(Boolean meetsRequirement) { this.meetsRequirement = meetsRequirement; }

    public String getExplanation() {
        if ("Education".equals(name)) {
            return String.format("Candidate: %s | Required: %s | Meets: %s",
                    safe(candidateLevel), safe(requiredLevel), booleanLabel(meetsRequirement));
        }
        if ("Experience".equals(name)) {
            return String.format("Candidate: %s years | Required: %s years | Meets: %s",
                    numberLabel(candidateYears), numberLabel(requiredYears), booleanLabel(meetsRequirement));
        }
        return String.format("Matched: %s | Missing: %s",
                matched.isEmpty() ? "none" : String.join(", ", matched),
                missing.isEmpty() ? "none" : String.join(", ", missing));
    }

    @Override
    public String toString() {
        return String.format("[%s] %d/%d pts (%.0f%%) — %s", name, pointsAwarded, weight,
                rawMatch * 100, getExplanation());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String numberLabel(Integer value) {
        return value == null ? "0" : Integer.toString(value);
    }

    private String booleanLabel(Boolean value) {
        if (value == null) {
            return "—";
        }
        return value ? "yes" : "no";
    }
}
