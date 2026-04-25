package service.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import entities.job_offer.CandidateProfile;
import entities.job_offer.JobApplication;
import entities.job_offer.JobOffer;
import entities.job_offer.ScoreBreakdown;
import entities.job_offer.ScoreCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rule-based ATS Scoring Engine.
 *
 * Computes a 0-100 score for a {@link JobApplication} against its {@link JobOffer}.
 * Scoring is deterministic and explainable — no ML models involved.
 *
 * <h3>Weight breakdown (sums to 100)</h3>
 * <pre>
 *   Required skills match    35 pts
 *   Preferred skills match   15 pts
 *   Years of experience      20 pts
 *   Education level          15 pts
 *   Language match           10 pts
 *   Keyword presence          5 pts
 * </pre>
 *
 * <h3>Disqualification hard filters</h3>
 * <ul>
 *   <li>Total score &lt; 30 after calculation</li>
 *   <li>Zero required skills matched when the offer has required skills</li>
 * </ul>
 */
public class AtsScoringEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtsScoringEngine.class);

    // Default weights — can be overridden via ScoringConfig stored in the job offer's requirements JSON
    private static final int WEIGHT_REQUIRED_SKILLS   = 35;
    private static final int WEIGHT_PREFERRED_SKILLS  = 15;
    private static final int WEIGHT_EXPERIENCE        = 20;
    private static final int WEIGHT_EDUCATION         = 15;
    private static final int WEIGHT_LANGUAGES         = 10;
    private static final int WEIGHT_KEYWORDS          =  5;

    private static final int DISQUALIFY_THRESHOLD = 30;

    private final ObjectMapper objectMapper;

    public AtsScoringEngine() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Score an application and return the full breakdown.
     * The result is also serialized and stored back on the application object.
     *
     * @param application the application to evaluate (mutated: score + scoreBreakdown set)
     * @return the computed breakdown
     */
    public ScoreBreakdown score(JobApplication application) {
        if (application == null) {
            throw new IllegalArgumentException("Application must not be null");
        }
        if (application.getJobOffer() == null) {
            throw new IllegalArgumentException("Application must have an associated job offer");
        }

        JobOffer offer = application.getJobOffer();
        CandidateProfile profile = extractProfile(application);

        ScoreBreakdown breakdown = new ScoreBreakdown();
        breakdown.setComputedAt(Instant.now());

        int total = 0;

        // 1) Required skills
        ScoreCriteria reqSkills = scoreRequiredSkills(offer, profile);
        breakdown.addCriteria(reqSkills);
        total += reqSkills.getPointsAwarded();

        // 2) Preferred skills
        ScoreCriteria prefSkills = scorePreferredSkills(offer, profile);
        breakdown.addCriteria(prefSkills);
        total += prefSkills.getPointsAwarded();

        // 3) Experience
        ScoreCriteria exp = scoreExperience(offer, profile);
        breakdown.addCriteria(exp);
        total += exp.getPointsAwarded();

        // 4) Education
        ScoreCriteria edu = scoreEducation(offer, profile);
        breakdown.addCriteria(edu);
        total += edu.getPointsAwarded();

        // 5) Languages
        ScoreCriteria lang = scoreLanguages(offer, profile);
        breakdown.addCriteria(lang);
        total += lang.getPointsAwarded();

        // 6) Keywords
        ScoreCriteria kw = scoreKeywords(offer, application, profile);
        breakdown.addCriteria(kw);
        total += kw.getPointsAwarded();

        // Clamp to 0-100
        total = Math.min(100, Math.max(0, total));
        breakdown.setTotalScore(total);

        // Disqualification checks
        applyDisqualificationRules(breakdown, offer, profile, reqSkills);

        // Mutate the application
        application.setScore(total);
        application.setScoredAt(java.sql.Timestamp.from(Instant.now()));
        try {
            application.setScoreBreakdown(objectMapper.writeValueAsString(breakdown));
        } catch (Exception e) {
            LOGGER.warn("Failed to serialize score breakdown for application {}", application.getId(), e);
            application.setScoreBreakdown(breakdown.toDisplayText());
        }

        LOGGER.info("Scored application {} for offer {}: {}/100 (disqualified={})",
                application.getId(), offer.getId(), total, breakdown.isDisqualified());

        return breakdown;
    }

    /**
     * Parse a previously stored breakdown JSON back into an object.
     * Returns an empty breakdown if parsing fails.
     */
    public ScoreBreakdown parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return new ScoreBreakdown();
        }
        try {
            return objectMapper.readValue(json, ScoreBreakdown.class);
        } catch (Exception e) {
            LOGGER.warn("Could not parse stored score breakdown JSON", e);
            return new ScoreBreakdown();
        }
    }

    // ── Scoring criteria ──────────────────────────────────────────────────────

    private ScoreCriteria scoreRequiredSkills(JobOffer offer, CandidateProfile profile) {
        List<String> required = parseCommaSeparated(offer.getRequiredSkills());
        if (required.isEmpty()) {
            return new ScoreCriteria("Required Skills", WEIGHT_REQUIRED_SKILLS, 1.0,
                    WEIGHT_REQUIRED_SKILLS, "No required skills specified — full points awarded");
        }

        List<String> candidateSkills = normalizeList(profile.getSkills());
        List<String> matched = required.stream()
                .filter(s -> containsIgnoreCase(candidateSkills, s))
                .collect(Collectors.toList());
        List<String> missing = required.stream()
                .filter(s -> !containsIgnoreCase(candidateSkills, s))
                .collect(Collectors.toList());

        double ratio = (double) matched.size() / required.size();
        int points = (int) Math.round(ratio * WEIGHT_REQUIRED_SKILLS);

        String explanation = String.format(
                "Matched %d/%d required: [%s]. Missing: [%s]",
                matched.size(), required.size(),
                String.join(", ", matched),
                String.join(", ", missing));

        return new ScoreCriteria("Required Skills", WEIGHT_REQUIRED_SKILLS, ratio, points, explanation);
    }

    private ScoreCriteria scorePreferredSkills(JobOffer offer, CandidateProfile profile) {
        List<String> preferred = parseCommaSeparated(offer.getPreferredSkills());
        if (preferred.isEmpty()) {
            return new ScoreCriteria("Preferred Skills", WEIGHT_PREFERRED_SKILLS, 1.0,
                    WEIGHT_PREFERRED_SKILLS, "No preferred skills specified — full points awarded");
        }

        List<String> candidateSkills = normalizeList(profile.getSkills());
        List<String> matched = preferred.stream()
                .filter(s -> containsIgnoreCase(candidateSkills, s))
                .collect(Collectors.toList());

        double ratio = (double) matched.size() / preferred.size();
        int points = (int) Math.round(ratio * WEIGHT_PREFERRED_SKILLS);

        String explanation = String.format(
                "Matched %d/%d preferred: [%s]",
                matched.size(), preferred.size(),
                String.join(", ", matched));

        return new ScoreCriteria("Preferred Skills", WEIGHT_PREFERRED_SKILLS, ratio, points, explanation);
    }

    private ScoreCriteria scoreExperience(JobOffer offer, CandidateProfile profile) {
        int required = offer.getMinExperienceYears() != null ? offer.getMinExperienceYears() : 0;
        int candidate = profile.getYearsOfExperience();

        if (required <= 0) {
            return new ScoreCriteria("Experience", WEIGHT_EXPERIENCE, 1.0,
                    WEIGHT_EXPERIENCE, "No experience requirement — full points awarded");
        }

        double ratio;
        String explanation;
        if (candidate >= required) {
            ratio = 1.0;
            explanation = String.format("Candidate has %d years (required: %d) ✓", candidate, required);
        } else if (candidate <= 0) {
            ratio = 0.0;
            explanation = String.format("No experience declared (required: %d years)", required);
        } else {
            // Partial credit: scale ratio with slight bonus for being close
            ratio = Math.min(1.0, (double) candidate / required);
            explanation = String.format("Candidate has %d years (required: %d) — partial credit", candidate, required);
        }

        int points = (int) Math.round(ratio * WEIGHT_EXPERIENCE);
        return new ScoreCriteria("Experience", WEIGHT_EXPERIENCE, ratio, points, explanation);
    }

    private ScoreCriteria scoreEducation(JobOffer offer, CandidateProfile profile) {
        String requiredEdu = offer.getMinEducation();
        String candidateEdu = profile.getEducationLevel();

        if (requiredEdu == null || requiredEdu.isBlank()) {
            return new ScoreCriteria("Education", WEIGHT_EDUCATION, 1.0,
                    WEIGHT_EDUCATION, "No education requirement — full points awarded");
        }

        int requiredRank  = CandidateProfile.educationRank(requiredEdu);
        int candidateRank = CandidateProfile.educationRank(candidateEdu);

        double ratio;
        String explanation;
        if (candidateRank >= requiredRank) {
            ratio = 1.0;
            explanation = String.format("Education met: %s (required: %s) ✓", candidateEdu, requiredEdu);
        } else if (candidateRank <= 0) {
            ratio = 0.0;
            explanation = String.format("Education not declared (required: %s)", requiredEdu);
        } else {
            // One level below: 50% credit; two or more: 0%
            ratio = candidateRank == requiredRank - 1 ? 0.5 : 0.0;
            explanation = String.format("Education below requirement: %s (required: %s)", candidateEdu, requiredEdu);
        }

        int points = (int) Math.round(ratio * WEIGHT_EDUCATION);
        return new ScoreCriteria("Education", WEIGHT_EDUCATION, ratio, points, explanation);
    }

    private ScoreCriteria scoreLanguages(JobOffer offer, CandidateProfile profile) {
        List<String> required = parseCommaSeparated(offer.getRequiredLanguages());
        if (required.isEmpty()) {
            return new ScoreCriteria("Languages", WEIGHT_LANGUAGES, 1.0,
                    WEIGHT_LANGUAGES, "No language requirement — full points awarded");
        }

        List<String> candidateLangs = normalizeList(profile.getLanguages());
        List<String> matched = required.stream()
                .filter(l -> containsIgnoreCase(candidateLangs, l))
                .collect(Collectors.toList());
        List<String> missing = required.stream()
                .filter(l -> !containsIgnoreCase(candidateLangs, l))
                .collect(Collectors.toList());

        double ratio = (double) matched.size() / required.size();
        int points = (int) Math.round(ratio * WEIGHT_LANGUAGES);

        String explanation = String.format("Matched %d/%d languages: [%s]. Missing: [%s]",
                matched.size(), required.size(),
                String.join(", ", matched), String.join(", ", missing));

        return new ScoreCriteria("Languages", WEIGHT_LANGUAGES, ratio, points, explanation);
    }

    private ScoreCriteria scoreKeywords(JobOffer offer, JobApplication application, CandidateProfile profile) {
        // Keywords come from: offer description/requirements, matched against cover letter + extracted keywords
        List<String> offerKeywords = new ArrayList<>();
        if (offer.getRequirements() != null) {
            // Extract simple words of length > 4 from requirements as approximate keywords
            String[] words = offer.getRequirements().toLowerCase().split("[\\s,;.]+");
            for (String w : words) {
                if (w.length() > 4) offerKeywords.add(w.trim());
            }
        }

        if (offerKeywords.isEmpty()) {
            return new ScoreCriteria("Keywords", WEIGHT_KEYWORDS, 1.0,
                    WEIGHT_KEYWORDS, "No keyword requirement — full points awarded");
        }

        // Build candidate keyword corpus: cover letter + extracted keywords
        StringBuilder corpus = new StringBuilder();
        if (application.getMessage() != null) corpus.append(application.getMessage().toLowerCase()).append(" ");
        for (String kw : profile.getKeywords()) corpus.append(kw.toLowerCase()).append(" ");
        String corpusText = corpus.toString();

        long matchCount = offerKeywords.stream()
                .distinct()
                .filter(kw -> corpusText.contains(kw.toLowerCase()))
                .count();

        // Use top 10 offer keywords for scoring (to avoid massive dilution)
        int sampleSize = Math.min(offerKeywords.size(), 10);
        double ratio = (double) matchCount / sampleSize;
        ratio = Math.min(1.0, ratio);
        int points = (int) Math.round(ratio * WEIGHT_KEYWORDS);

        String explanation = String.format("Found %d relevant keywords in cover letter / profile", matchCount);
        return new ScoreCriteria("Keywords", WEIGHT_KEYWORDS, ratio, points, explanation);
    }

    // ── Disqualification ─────────────────────────────────────────────────────

    private void applyDisqualificationRules(ScoreBreakdown breakdown, JobOffer offer,
                                            CandidateProfile profile, ScoreCriteria reqSkillsCriteria) {
        // Hard filter 1: zero required skills matched
        List<String> required = parseCommaSeparated(offer.getRequiredSkills());
        if (!required.isEmpty() && reqSkillsCriteria.getPointsAwarded() == 0) {
            breakdown.setDisqualified(true);
            breakdown.setDisqualifyReason("Candidate matched none of the required skills: " +
                    String.join(", ", required));
            return;
        }

        // Hard filter 2: score below threshold
        if (breakdown.getTotalScore() < DISQUALIFY_THRESHOLD) {
            breakdown.setDisqualified(true);
            breakdown.setDisqualifyReason(String.format(
                    "Total score (%d) is below the minimum threshold (%d)",
                    breakdown.getTotalScore(), DISQUALIFY_THRESHOLD));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extract a CandidateProfile from the application's extractedData JSON field.
     * Falls back to an empty profile if parsing fails (e.g., data not yet extracted).
     */
    private CandidateProfile extractProfile(JobApplication application) {
        String json = application.getExtractedData();
        if (json == null || json.isBlank()) {
            LOGGER.debug("No extracted data for application {} — scoring with empty profile", application.getId());
            return new CandidateProfile();
        }
        try {
            return objectMapper.readValue(json, CandidateProfile.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse extracted_data for application {}: {}", application.getId(), e.getMessage());
            return new CandidateProfile();
        }
    }

    /** Parse a comma-separated string into a normalized lowercase list. */
    private List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        return Arrays.stream(value.split("[,;\\n]+"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** Normalize a list to lowercase trimmed strings. */
    private List<String> normalizeList(List<String> list) {
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toList());
    }

    /** Case-insensitive containment check with partial match support. */
    private boolean containsIgnoreCase(List<String> haystack, String needle) {
        if (needle == null || needle.isBlank()) return false;
        String normalizedNeedle = needle.trim().toLowerCase();
        return haystack.stream().anyMatch(item ->
                item.contains(normalizedNeedle) || normalizedNeedle.contains(item));
    }
}
