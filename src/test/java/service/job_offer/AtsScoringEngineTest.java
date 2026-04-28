package service.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import entities.job_offer.CandidateProfile;
import entities.job_offer.JobApplication;
import entities.job_offer.JobOffer;
import entities.job_offer.ScoreBreakdown;
import entities.job_offer.ScoreCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AtsScoringEngine")
class AtsScoringEngineTest {

    private AtsScoringEngine engine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        engine = new AtsScoringEngine();
        objectMapper = new ObjectMapper();
    }

    @Test
    void perfectMatchScores100() throws Exception {
        JobOffer offer = offer("java, spring, sql", "docker, kubernetes", 3, "licence", "english, french");
        CandidateProfile profile = profile(
                List.of("Java", "Spring", "SQL", "Docker", "Kubernetes"),
                5,
                "master",
                List.of("English", "French")
        );

        JobApplication application = application(offer, profile);
        ScoreBreakdown breakdown = engine.score(application);

        assertEquals(100, breakdown.getTotalScore());
        assertEquals(100, application.getScore());
        assertEquals(5, breakdown.getCriteria().size());
    }

    @Test
    void requiredSkillsUse40PointWeight() throws Exception {
        JobOffer offer = offer("java, python, sql", null, 0, null, null);
        JobApplication application = application(offer, profile(List.of("Java"), 0, null, List.of()));

        ScoreBreakdown breakdown = engine.score(application);
        ScoreCriteria requiredSkills = criterion(breakdown, "Required Skills");

        assertEquals(13, requiredSkills.getPointsAwarded());
        assertEquals(List.of("java"), requiredSkills.getMatched());
        assertEquals(List.of("python", "sql"), requiredSkills.getMissing());
        assertEquals(3, requiredSkills.getTotal());
    }

    @Test
    void educationUsesRatioAgainstRequiredLevel() throws Exception {
        JobOffer offer = offer(null, null, 0, "master", null);
        JobApplication application = application(offer, profile(List.of(), 0, "licence", List.of()));

        ScoreBreakdown breakdown = engine.score(application);
        ScoreCriteria education = criterion(breakdown, "Education");

        assertEquals(15, education.getPointsAwarded());
        assertEquals("licence", education.getCandidateLevel());
        assertEquals("master", education.getRequiredLevel());
        assertFalse(Boolean.TRUE.equals(education.getMeetsRequirement()));
    }

    @Test
    void experienceUses15PointWeight() throws Exception {
        JobOffer offer = offer(null, null, 4, null, null);
        JobApplication application = application(offer, profile(List.of(), 2, null, List.of()));

        ScoreBreakdown breakdown = engine.score(application);
        ScoreCriteria experience = criterion(breakdown, "Experience");

        assertEquals(8, experience.getPointsAwarded());
        assertEquals(2, experience.getCandidateYears());
        assertEquals(4, experience.getRequiredYears());
        assertFalse(Boolean.TRUE.equals(experience.getMeetsRequirement()));
    }

    @Test
    void emptyOfferCriteriaAwardFullCategoryPoints() throws Exception {
        JobOffer offer = offer(null, null, 0, null, null);
        JobApplication application = application(offer, profile(List.of(), 0, null, List.of()));

        ScoreBreakdown breakdown = engine.score(application);

        assertEquals(100, breakdown.getTotalScore());
        assertEquals(40, criterion(breakdown, "Required Skills").getPointsAwarded());
        assertEquals(15, criterion(breakdown, "Preferred Skills").getPointsAwarded());
        assertEquals(20, criterion(breakdown, "Education").getPointsAwarded());
        assertEquals(15, criterion(breakdown, "Experience").getPointsAwarded());
        assertEquals(10, criterion(breakdown, "Languages").getPointsAwarded());
    }

    @Test
    void parseBreakdownRoundTripsJson() throws Exception {
        JobOffer offer = offer("java", "docker", 2, "bac+2", "english");
        JobApplication application = application(offer, profile(List.of("Java", "Docker"), 3, "licence", List.of("English")));

        engine.score(application);
        ScoreBreakdown parsed = engine.parseBreakdown(application.getScoreBreakdown());

        assertEquals(application.getScore(), parsed.getTotalScore());
        assertFalse(parsed.getCriteria().isEmpty());
    }

    @Test
    void scoringWithoutExtractedDataDoesNotThrow() {
        JobApplication application = new JobApplication();
        application.setId(1);
        application.setJobOffer(offer("java", "docker", 3, "licence", "english"));
        application.setStatus("SUBMITTED");

        assertDoesNotThrow(() -> {
            ScoreBreakdown breakdown = engine.score(application);
            assertNotNull(breakdown);
            assertTrue(breakdown.getTotalScore() >= 0);
        });
    }

    private JobOffer offer(String requiredSkills, String preferredSkills, int minExperienceYears, String minEducation, String requiredLanguages) {
        JobOffer offer = new JobOffer();
        offer.setId(1);
        offer.setTitle("Test Offer");
        offer.setRequiredSkills(requiredSkills);
        offer.setPreferredSkills(preferredSkills);
        offer.setMinExperienceYears(minExperienceYears);
        offer.setMinEducation(minEducation);
        offer.setRequiredLanguages(requiredLanguages);
        return offer;
    }

    private CandidateProfile profile(List<String> skills, int experienceYears, String educationLevel, List<String> languages) {
        CandidateProfile profile = new CandidateProfile();
        profile.setSkills(skills);
        profile.setExperienceYears(experienceYears);
        profile.setEducationLevel(educationLevel);
        profile.setLanguages(languages);
        return profile;
    }

    private JobApplication application(JobOffer offer, CandidateProfile profile) throws Exception {
        JobApplication application = new JobApplication();
        application.setId(99);
        application.setJobOffer(offer);
        application.setStatus("SUBMITTED");
        application.setExtractedData(objectMapper.writeValueAsString(profile));
        return application;
    }

    private ScoreCriteria criterion(ScoreBreakdown breakdown, String name) {
        return breakdown.getCriteria().stream()
                .filter(criteria -> name.equals(criteria.getName()))
                .findFirst()
                .orElseThrow();
    }
}
