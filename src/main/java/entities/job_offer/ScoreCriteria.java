package entities.job_offer;

/**
 * Represents a single scoring criterion in an ATS evaluation.
 * Serialized as part of ScoreBreakdown JSON stored in job_application.score_breakdown.
 */
public class ScoreCriteria {

    private String name;
    private int weight;
    private double rawMatch;      // 0.0 – 1.0
    private int pointsAwarded;
    private String explanation;

    public ScoreCriteria() {}

    public ScoreCriteria(String name, int weight, double rawMatch, int pointsAwarded, String explanation) {
        this.name = name;
        this.weight = weight;
        this.rawMatch = rawMatch;
        this.pointsAwarded = pointsAwarded;
        this.explanation = explanation;
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

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    @Override
    public String toString() {
        return String.format("[%s] %d/%d pts (%.0f%%) — %s", name, pointsAwarded, weight,
                rawMatch * 100, explanation);
    }
}
