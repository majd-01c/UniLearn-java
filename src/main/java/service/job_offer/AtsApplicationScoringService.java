package service.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import entities.job_offer.CandidateProfile;
import entities.job_offer.JobApplication;
import entities.job_offer.ScoreBreakdown;

import java.io.File;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Coordinates extraction and scoring so every ATS entry point uses the same flow.
 */
public class AtsApplicationScoringService {

    private final GeminiCvExtractorService cvExtractorService;
    private final AtsScoringEngine scoringEngine;
    private final ObjectMapper objectMapper;

    public AtsApplicationScoringService() {
        this(new GeminiCvExtractorService(), new AtsScoringEngine(), new ObjectMapper());
    }

    AtsApplicationScoringService(GeminiCvExtractorService cvExtractorService,
                                 AtsScoringEngine scoringEngine,
                                 ObjectMapper objectMapper) {
        this.cvExtractorService = cvExtractorService;
        this.scoringEngine = scoringEngine;
        this.objectMapper = objectMapper;
    }

    public ScoreBreakdown extractAndScore(JobApplication application) throws Exception {
        CandidateProfile profile = ensureExtractedData(application);
        application.setExtractedData(objectMapper.writeValueAsString(profile));
        application.setUpdatedAt(Timestamp.from(Instant.now()));
        return scoringEngine.score(application);
    }

    public CandidateProfile ensureExtractedData(JobApplication application) throws Exception {
        if (application == null) {
            throw new IllegalArgumentException("Application must not be null");
        }

        String existing = application.getExtractedData();
        if (existing != null && !existing.isBlank()) {
            try {
                return objectMapper.readValue(existing, CandidateProfile.class);
            } catch (Exception ignored) {
                // Re-extract if stored JSON is invalid.
            }
        }

        File cvFile = cvExtractorService.resolveCvFile(application.getCvFileName());
        CandidateProfile profile = cvExtractorService.extractFromFile(cvFile);
        application.setExtractedData(objectMapper.writeValueAsString(profile));
        return profile;
    }

    public boolean isExtractionEnabled() {
        return cvExtractorService.isEnabled();
    }
}
