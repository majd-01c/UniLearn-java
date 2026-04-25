package service.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import entities.job_offer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AtsScoringEngine}.
 *
 * Tests are fully deterministic and self-contained — no database, no Gemini API calls.
 * Each test builds a {@link JobOffer} + {@link JobApplication} pair with a pre-set
 * {@link CandidateProfile} stored as JSON in {@code extractedData}, then
 * calls {@code score()} and asserts on the breakdown values.
 */
@DisplayName("AtsScoringEngine — rule-based scoring unit tests")
class AtsScoringEngineTest {

    private AtsScoringEngine engine;
    private ObjectMapper     objectMapper;

    @BeforeEach
    void setup() {
        engine       = new AtsScoringEngine();
        objectMapper = new ObjectMapper();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Build a minimal JobOffer with the given skill requirements. */
    private JobOffer offer(String requiredSkills, String preferredSkills,
                           int minExp, String minEdu, String requiredLanguages) {
        JobOffer o = new JobOffer();
        o.setId(1);
        o.setTitle("Test Offer");
        o.setRequiredSkills(requiredSkills);
        o.setPreferredSkills(preferredSkills);
        o.setMinExperienceYears(minExp);
        o.setMinEducation(minEdu);
        o.setRequiredLanguages(requiredLanguages);
        o.setRequirements("java spring agile testing microservices");
        return o;
    }

    /** Build a JobApplication with a given CandidateProfile as its extractedData JSON. */
    private JobApplication application(JobOffer offer, CandidateProfile profile) throws Exception {
        JobApplication app = new JobApplication();
        app.setId(99);
        app.setJobOffer(offer);
        app.setStatus("SUBMITTED");
        app.setMessage("I am very interested in this role.");
        String json = objectMapper.writeValueAsString(profile);
        app.setExtractedData(json);
        return app;
    }

    /** Build a CandidateProfile with all fields. */
    private CandidateProfile profile(List<String> skills, int exp, String edu,
                                     List<String> languages, List<String> keywords) {
        CandidateProfile p = new CandidateProfile();
        p.setSkills(skills);
        p.setYearsOfExperience(exp);
        p.setEducationLevel(edu);
        p.setLanguages(languages);
        p.setKeywords(keywords);
        return p;
    }

    // ── Test Cases ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Perfect match candidate")
    class PerfectMatch {

        @Test
        @DisplayName("Should score 100/100 when candidate matches all criteria")
        void perfectScore() throws Exception {
            JobOffer offer = offer("java, spring, sql", "docker, kubernetes",
                    3, "BACHELOR", "English, French");

            CandidateProfile p = profile(
                    List.of("Java", "Spring", "SQL", "Docker", "Kubernetes"),
                    5, "MASTER",
                    List.of("English", "French"),
                    List.of("java", "spring", "agile", "testing", "microservices")
            );

            JobApplication app = application(offer, p);
            ScoreBreakdown breakdown = engine.score(app);

            assertEquals(100, breakdown.getTotalScore(),
                "Perfect match should score 100/100");
            assertFalse(breakdown.isDisqualified(),
                "Perfect match should NOT be disqualified");
            assertNotNull(app.getScore(), "Application score must be set");
            assertNotNull(app.getScoreBreakdown(), "Score breakdown JSON must be set");
        }

        @Test
        @DisplayName("Application score field should match breakdown total")
        void applicationScoreMatchesBreakdown() throws Exception {
            JobOffer offer = offer("java", null, 0, null, null);
            CandidateProfile p = profile(List.of("Java"), 5, "BACHELOR",
                    List.of("English"), List.of("java"));
            JobApplication app = application(offer, p);

            engine.score(app);
            ScoreBreakdown breakdown = engine.parseBreakdown(app.getScoreBreakdown());

            assertEquals(app.getScore(), breakdown.getTotalScore(),
                "Application.score must equal breakdown.totalScore");
        }
    }

    @Nested
    @DisplayName("Disqualification rules")
    class Disqualification {

        @Test
        @DisplayName("Should disqualify when zero required skills matched")
        void zeroRequiredSkillsMatchDisqualifies() throws Exception {
            JobOffer offer = offer("python, machine-learning", null, 0, null, null);
            CandidateProfile p = profile(List.of("Java", "Spring"), 5, "MASTER",
                    List.of("English"), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);

            assertTrue(breakdown.isDisqualified(), "Should be disqualified — no required skills matched");
            assertNotNull(breakdown.getDisqualifyReason(), "Disqualify reason must be set");
            assertTrue(breakdown.getDisqualifyReason().contains("required skills"),
                "Reason should mention required skills");
        }

        @Test
        @DisplayName("Should disqualify when overall score is below threshold (30)")
        void lowScoreDisqualifies() throws Exception {
            JobOffer offer = offer("python, tensorflow, pytorch, keras, nlp",
                    "spark, hadoop", 10, "PHD", "Japanese, Korean");
            CandidateProfile p = profile(List.of("Java"),
                    0, "HIGH_SCHOOL", List.of("English"), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);

            assertTrue(breakdown.isDisqualified(),
                "Very poor match should be disqualified");
        }

        @Test
        @DisplayName("Full-point offer with no requirements should NOT disqualify")
        void noRequirementsDoesNotDisqualify() throws Exception {
            JobOffer offer = offer(null, null, 0, null, null);
            CandidateProfile p = profile(List.of(), 0, null, List.of(), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);

            assertFalse(breakdown.isDisqualified(),
                "Offer with no requirements should never disqualify");
        }
    }

    @Nested
    @DisplayName("Partial matching")
    class PartialMatch {

        @Test
        @DisplayName("Partial required skill match produces partial required-skills score")
        void partialSkillMatchPartialPoints() throws Exception {
            JobOffer offer = offer("java, python, sql", null, 0, null, null);
            // Candidate only knows Java (1/3 skills)
            CandidateProfile p = profile(List.of("Java"), 0, null, List.of(), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);

            ScoreCriteria reqSkills = breakdown.getCriteria().stream()
                    .filter(c -> "Required Skills".equals(c.getName()))
                    .findFirst().orElseThrow();

            // 1/3 of weight=35 → ~12 pts
            assertEquals(12, reqSkills.getPointsAwarded(),
                "1/3 skill match should award ~12 pts out of 35");
        }

        @Test
        @DisplayName("Partial experience gives partial points")
        void partialExperiencePartialPoints() throws Exception {
            JobOffer offer = offer(null, null, 4, null, null);
            CandidateProfile p = profile(List.of(), 2, null, List.of(), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);

            ScoreCriteria exp = breakdown.getCriteria().stream()
                    .filter(c -> "Experience".equals(c.getName()))
                    .findFirst().orElseThrow();

            // 2/4 = 0.5 → 50% of 20 = 10 pts
            assertEquals(10, exp.getPointsAwarded(),
                "2 yrs experience with 4 required should yield 10/20 pts");
        }

        @Test
        @DisplayName("Exceeding experience requirement gives full experience points")
        void exceedingExperienceFullPoints() throws Exception {
            JobOffer offer = offer(null, null, 3, null, null);
            CandidateProfile p = profile(List.of(), 10, null, List.of(), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);

            ScoreCriteria exp = breakdown.getCriteria().stream()
                    .filter(c -> "Experience".equals(c.getName()))
                    .findFirst().orElseThrow();

            assertEquals(20, exp.getPointsAwarded(), "Exceeding exp should award full 20 pts");
        }
    }

    @Nested
    @DisplayName("Education scoring")
    class EducationScoring {

        @Test
        @DisplayName("Candidate education above requirement gets full education points")
        void higherEducationFullPoints() throws Exception {
            JobOffer offer = offer(null, null, 0, "BACHELOR", null);
            CandidateProfile p = profile(List.of(), 0, "MASTER", List.of(), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);
            ScoreCriteria edu = breakdown.getCriteria().stream()
                    .filter(c -> "Education".equals(c.getName())).findFirst().orElseThrow();

            assertEquals(15, edu.getPointsAwarded(), "MASTER ≥ BACHELOR should give 15/15 pts");
        }

        @Test
        @DisplayName("Education one level below gives 50% education points")
        void oneLevelBelowHalfPoints() throws Exception {
            JobOffer offer = offer(null, null, 0, "MASTER", null);
            CandidateProfile p = profile(List.of(), 0, "BACHELOR", List.of(), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);
            ScoreCriteria edu = breakdown.getCriteria().stream()
                    .filter(c -> "Education".equals(c.getName())).findFirst().orElseThrow();

            assertEquals(7, edu.getPointsAwarded(), "One level below should give ~7/15 pts");
        }

        @Test
        @DisplayName("Education two or more levels below gives zero education points")
        void twoLevelsBelowZeroPoints() throws Exception {
            JobOffer offer = offer(null, null, 0, "PHD", null);
            CandidateProfile p = profile(List.of(), 0, "BACHELOR", List.of(), List.of());
            JobApplication app = application(offer, p);

            ScoreBreakdown breakdown = engine.score(app);
            ScoreCriteria edu = breakdown.getCriteria().stream()
                    .filter(c -> "Education".equals(c.getName())).findFirst().orElseThrow();

            assertEquals(0, edu.getPointsAwarded(), "Two levels below should give 0 education pts");
        }
    }

    @Nested
    @DisplayName("Breakdown JSON round-trip")
    class BreakdownSerialization {

        @Test
        @DisplayName("Stored breakdown JSON should round-trip correctly")
        void jsonRoundTrip() throws Exception {
            JobOffer offer = offer("java", null, 2, "BACHELOR", "English");
            CandidateProfile p = profile(List.of("Java", "Spring"), 3, "MASTER",
                    List.of("English", "French"), List.of("java", "spring"));
            JobApplication app = application(offer, p);

            engine.score(app);

            String json = app.getScoreBreakdown();
            assertNotNull(json, "Breakdown JSON must not be null");

            ScoreBreakdown parsed = engine.parseBreakdown(json);
            assertNotNull(parsed, "Parsed breakdown must not be null");
            assertFalse(parsed.getCriteria().isEmpty(), "Parsed breakdown must have criteria");
            assertEquals(app.getScore(), parsed.getTotalScore(),
                "Parsed score must match application score");
        }

        @Test
        @DisplayName("Parsing null/empty JSON returns empty breakdown, not exception")
        void parsingNullReturnsEmptyBreakdown() {
            ScoreBreakdown empty1 = engine.parseBreakdown(null);
            ScoreBreakdown empty2 = engine.parseBreakdown("");
            ScoreBreakdown empty3 = engine.parseBreakdown("not-valid-json");

            assertNotNull(empty1);
            assertNotNull(empty2);
            assertNotNull(empty3);
            // None should throw
        }
    }

    @Nested
    @DisplayName("Empty extracted data")
    class EmptyProfile {

        @Test
        @DisplayName("Application with no extractedData scores without throwing")
        void noExtractedDataDoesNotThrow() {
            JobOffer offer = offer("java", "python", 3, "BACHELOR", "English");
            JobApplication app = new JobApplication();
            app.setId(1);
            app.setJobOffer(offer);
            app.setStatus("SUBMITTED");
            // No extractedData set

            assertDoesNotThrow(() -> {
                ScoreBreakdown breakdown = engine.score(app);
                assertNotNull(breakdown);
                assertTrue(breakdown.getTotalScore() >= 0);
            }, "Scoring with no extracted data must not throw");
        }
    }
}
