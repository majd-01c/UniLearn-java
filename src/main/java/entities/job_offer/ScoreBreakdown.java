package entities.job_offer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Full ATS scoring result for one application.
 * Serialized as JSON and stored in job_application.score_breakdown.
 */
public class ScoreBreakdown {

    private List<ScoreCriteria> criteria = new ArrayList<>();
    private int totalScore;
    private boolean disqualified;
    private String disqualifyReason;
    private Instant computedAt;

    public ScoreBreakdown() {}

    // ── helpers ───────────────────────────────────────────────────────────────

    public void addCriteria(ScoreCriteria c) {
        criteria.add(c);
    }

    /** Human-readable summary for display in the review panel. */
    public String toDisplayText() {
        if (criteria == null || criteria.isEmpty()) {
            return "No breakdown available.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ATS Score: ").append(totalScore).append("/100\n");
        if (disqualified) {
            sb.append("⚠ DISQUALIFIED: ").append(disqualifyReason).append("\n");
        }
        sb.append("─".repeat(40)).append("\n");
        for (ScoreCriteria c : criteria) {
            sb.append(c.toString()).append("\n");
        }
        return sb.toString().trim();
    }

    // ── getters / setters ──────────────────────────────────────────────────────

    public List<ScoreCriteria> getCriteria() { return criteria; }
    public void setCriteria(List<ScoreCriteria> criteria) { this.criteria = criteria; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    public boolean isDisqualified() { return disqualified; }
    public void setDisqualified(boolean disqualified) { this.disqualified = disqualified; }

    public String getDisqualifyReason() { return disqualifyReason; }
    public void setDisqualifyReason(String disqualifyReason) { this.disqualifyReason = disqualifyReason; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
}
