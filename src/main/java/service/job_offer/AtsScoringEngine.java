package service.job_offer;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Desktop ATS scoring engine aligned with the web implementation.
 */
public class AtsScoringEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtsScoringEngine.class);

    public static final int REQUIRED_SKILLS_WEIGHT = 40;
    public static final int PREFERRED_SKILLS_WEIGHT = 15;
    public static final int EDUCATION_WEIGHT = 20;
    public static final int EXPERIENCE_WEIGHT = 15;
    public static final int LANGUAGES_WEIGHT = 10;

    private final ObjectMapper objectMapper;

    public AtsScoringEngine() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ScoreBreakdown score(JobApplication application) {
        if (application == null) {
            throw new IllegalArgumentException("Application must not be null");
        }
        if (application.getJobOffer() == null) {
            throw new IllegalArgumentException("Application must have an associated job offer");
        }

        CandidateProfile profile = extractProfile(application);
        JobOffer offer = application.getJobOffer();

        ScoreBreakdown breakdown = new ScoreBreakdown();
        breakdown.setComputedAt(Instant.now());

        ScoreCriteria requiredSkills = calculateSkillsScore(
                "Required Skills",
                profile.getSkills(),
                parseList(offer.getRequiredSkills()),
                REQUIRED_SKILLS_WEIGHT
        );
        ScoreCriteria preferredSkills = calculateSkillsScore(
                "Preferred Skills",
                profile.getSkills(),
                parseList(offer.getPreferredSkills()),
                PREFERRED_SKILLS_WEIGHT
        );
        ScoreCriteria education = calculateEducationScore(
                profile.getEducationLevel(),
                offer.getMinEducation()
        );
        ScoreCriteria experience = calculateExperienceScore(
                profile.getExperienceYears(),
                offer.getMinExperienceYears()
        );
        ScoreCriteria languages = calculateSkillsScore(
                "Languages",
                profile.getLanguages(),
                parseList(offer.getRequiredLanguages()),
                LANGUAGES_WEIGHT
        );

        breakdown.addCriteria(requiredSkills);
        breakdown.addCriteria(preferredSkills);
        breakdown.addCriteria(education);
        breakdown.addCriteria(experience);
        breakdown.addCriteria(languages);

        int total = requiredSkills.getPointsAwarded()
                + preferredSkills.getPointsAwarded()
                + education.getPointsAwarded()
                + experience.getPointsAwarded()
                + languages.getPointsAwarded();
        total = Math.min(100, Math.max(0, Math.round(total)));

        breakdown.setTotalScore(total);

        application.setScore(total);
        application.setScoredAt(java.sql.Timestamp.from(Instant.now()));
        try {
            application.setScoreBreakdown(objectMapper.writeValueAsString(breakdown));
        } catch (Exception exception) {
            LOGGER.warn("Failed to serialize score breakdown for application {}", application.getId(), exception);
            application.setScoreBreakdown(breakdown.toDisplayText());
        }

        return breakdown;
    }

    public ScoreBreakdown parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return new ScoreBreakdown();
        }
        try {
            return objectMapper.readValue(json, ScoreBreakdown.class);
        } catch (Exception exception) {
            LOGGER.warn("Could not parse stored score breakdown JSON", exception);
            return new ScoreBreakdown();
        }
    }

    private ScoreCriteria calculateSkillsScore(String name, List<String> candidateValues, List<String> requiredValues, int maxPoints) {
        if (requiredValues.isEmpty()) {
            ScoreCriteria criteria = new ScoreCriteria(name, maxPoints, 1.0, maxPoints);
            criteria.setTotal(0);
            return criteria;
        }

        List<String> normalizedCandidates = normalizeList(candidateValues);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String required : requiredValues) {
            if (matchesAny(normalizedCandidates, required)) {
                matched.add(required);
            } else {
                missing.add(required);
            }
        }

        double matchPercentage = (double) matched.size() / requiredValues.size();
        int score = (int) Math.round(matchPercentage * maxPoints);

        ScoreCriteria criteria = new ScoreCriteria(name, maxPoints, matchPercentage, score);
        criteria.setMatched(matched);
        criteria.setMissing(missing);
        criteria.setTotal(requiredValues.size());
        return criteria;
    }

    private ScoreCriteria calculateEducationScore(String candidateLevel, String requiredLevel) {
        String normalizedCandidate = normalizeEducationLevel(candidateLevel);
        String normalizedRequired = normalizeEducationLevel(requiredLevel);

        if (normalizedRequired == null) {
            ScoreCriteria criteria = new ScoreCriteria("Education", EDUCATION_WEIGHT, 1.0, EDUCATION_WEIGHT);
            criteria.setCandidateLevel(normalizedCandidate);
            criteria.setRequiredLevel(null);
            criteria.setMeetsRequirement(true);
            return criteria;
        }

        if (normalizedCandidate == null) {
            ScoreCriteria criteria = new ScoreCriteria("Education", EDUCATION_WEIGHT, 0.0, 0);
            criteria.setCandidateLevel(null);
            criteria.setRequiredLevel(normalizedRequired);
            criteria.setMeetsRequirement(false);
            return criteria;
        }

        int candidateWeight = CandidateProfile.educationRank(normalizedCandidate);
        int requiredWeight = CandidateProfile.educationRank(normalizedRequired);
        double ratio = requiredWeight <= 0 ? 1.0 : Math.min(1.0, (double) candidateWeight / requiredWeight);
        int score = candidateWeight >= requiredWeight
                ? EDUCATION_WEIGHT
                : (int) Math.round(ratio * EDUCATION_WEIGHT);

        ScoreCriteria criteria = new ScoreCriteria("Education", EDUCATION_WEIGHT, ratio, score);
        criteria.setCandidateLevel(normalizedCandidate);
        criteria.setRequiredLevel(normalizedRequired);
        criteria.setMeetsRequirement(candidateWeight >= requiredWeight);
        return criteria;
    }

    private ScoreCriteria calculateExperienceScore(int candidateYears, Integer requiredYears) {
        int normalizedRequired = requiredYears == null ? 0 : Math.max(0, requiredYears);
        if (normalizedRequired <= 0) {
            ScoreCriteria criteria = new ScoreCriteria("Experience", EXPERIENCE_WEIGHT, 1.0, EXPERIENCE_WEIGHT);
            criteria.setCandidateYears(Math.max(0, candidateYears));
            criteria.setRequiredYears(normalizedRequired);
            criteria.setMeetsRequirement(true);
            return criteria;
        }

        int normalizedCandidate = Math.max(0, candidateYears);
        double ratio = Math.min(1.0, (double) normalizedCandidate / normalizedRequired);
        int score = normalizedCandidate >= normalizedRequired
                ? EXPERIENCE_WEIGHT
                : (int) Math.round(ratio * EXPERIENCE_WEIGHT);

        ScoreCriteria criteria = new ScoreCriteria("Experience", EXPERIENCE_WEIGHT, ratio, score);
        criteria.setCandidateYears(normalizedCandidate);
        criteria.setRequiredYears(normalizedRequired);
        criteria.setMeetsRequirement(normalizedCandidate >= normalizedRequired);
        return criteria;
    }

    private CandidateProfile extractProfile(JobApplication application) {
        String json = application.getExtractedData();
        if (json == null || json.isBlank()) {
            return new CandidateProfile();
        }
        try {
            return objectMapper.readValue(json, CandidateProfile.class);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse extracted_data for application {}: {}", application.getId(), exception.getMessage());
            return new CandidateProfile();
        }
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                List<String> raw = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                return raw.stream()
                        .map(this::normalizeToken)
                        .filter(item -> item != null && !item.isBlank())
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
                // Fall through to delimiter-based parsing.
            }
        }

        return Arrays.stream(trimmed.split("[,;\\n]+"))
                .map(this::normalizeToken)
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.toList());
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .map(this::normalizeToken)
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.toList());
    }

    private boolean matchesAny(List<String> candidates, String required) {
        String normalizedRequired = normalizeToken(required);
        if (normalizedRequired == null) {
            return false;
        }
        return candidates.stream().anyMatch(candidate ->
                candidate.equals(normalizedRequired)
                        || candidate.contains(normalizedRequired)
                        || normalizedRequired.contains(candidate)
        );
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeEducationLevel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e")
                .replace("à", "a");

        return switch (normalized) {
            case "bac", "high_school", "high school", "baccalaureat", "baccalaureate" -> "bac";
            case "bac+2", "bac +2", "bac + 2", "associate", "deust", "dut", "bts" -> "bac+2";
            case "licence", "license", "licence professionnelle", "bachelor" -> "licence";
            case "master", "mastère", "msc", "maitrise" -> "master";
            case "ingenieur", "ingénieur", "engineering", "engineer" -> "ingenieur";
            case "doctorat", "doctorate", "phd", "ph.d" -> "doctorat";
            case "not_required", "not required", "none" -> null;
            default -> normalized;
        };
    }
}
