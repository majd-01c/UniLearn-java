package service.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import entities.job_offer.CandidateProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;

/**
 * Gemini API client that extracts structured candidate profile data from a CV file or text.
 *
 * <h3>Configuration</h3>
 * Place your API key in {@code src/main/resources/config/ats-config.properties}:
 * <pre>
 *   gemini.api.key=YOUR_KEY_HERE
 *   gemini.model=gemini-2.0-flash
 *   gemini.enabled=true
 * </pre>
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Read CV text (from file or directly).</li>
 *   <li>Send to Gemini with a structured extraction prompt.</li>
 *   <li>Parse JSON response into {@link CandidateProfile}.</li>
 *   <li>Caller stores result in {@code job_application.extracted_data}.</li>
 * </ol>
 *
 * The extracted profile is stored once and reused by {@link AtsScoringEngine}
 * without re-calling Gemini.
 */
public class GeminiCvExtractorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiCvExtractorService.class);
    private static final String CONFIG_PATH = "/config/ats-config.properties";

    private static final String GEMINI_BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String EXTRACTION_PROMPT_TEMPLATE = """
            You are an ATS (Applicant Tracking System) assistant. Extract structured data from the following CV/resume text.
            Return ONLY a valid JSON object with exactly these fields (no extra text, no markdown, no code blocks):
            {
              "skills": ["skill1", "skill2"],
              "yearsOfExperience": 3,
              "educationLevel": "BACHELOR",
              "languages": ["English", "French"],
              "currentTitle": "Software Engineer",
              "summary": "brief professional summary",
              "keywords": ["java", "spring", "agile"]
            }
            
            Rules:
            - educationLevel must be exactly one of: HIGH_SCHOOL, BACHELOR, MASTER, PHD
            - yearsOfExperience must be a non-negative integer
            - skills and languages must be lists of strings
            - keywords should include technical terms, frameworks, methodologies from the CV
            - If a field cannot be determined, use null for strings and 0 for numbers, [] for arrays
            
            CV TEXT:
            %s
            """;

    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiCvExtractorService() {
        Properties config = loadConfig();
        this.apiKey  = config.getProperty("gemini.api.key", "").trim();
        this.model   = config.getProperty("gemini.model", "gemini-2.0-flash").trim();
        this.enabled = Boolean.parseBoolean(config.getProperty("gemini.enabled", "false").trim());
        int timeoutSec = Integer.parseInt(config.getProperty("gemini.timeout.seconds", "30"));

        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Extract structured profile from a CV file (PDF or plain text).
     * Reads the file as UTF-8 text; for binary PDFs you would need a PDF-text extractor first.
     *
     * @param cvFile the CV file on disk
     * @return extracted profile, or an empty profile on failure
     */
    public CandidateProfile extractFromFile(File cvFile) {
        if (cvFile == null || !cvFile.exists()) {
            LOGGER.warn("CV file not found or null — returning empty profile");
            return new CandidateProfile();
        }
        try {
            String text = Files.readString(cvFile.toPath());
            return extractFromText(text);
        } catch (IOException e) {
            LOGGER.error("Failed to read CV file: {}", cvFile.getName(), e);
            return new CandidateProfile();
        }
    }

    /**
     * Extract structured profile from raw CV text.
     *
     * @param cvText raw text from the CV
     * @return extracted profile, or an empty profile if Gemini is disabled or fails
     */
    public CandidateProfile extractFromText(String cvText) {
        if (!enabled) {
            LOGGER.info("Gemini extraction is disabled (gemini.enabled=false). " +
                        "Set your API key in config/ats-config.properties to enable it.");
            return new CandidateProfile();
        }

        if (apiKey.isBlank() || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            LOGGER.warn("Gemini API key not configured. " +
                        "Set gemini.api.key in src/main/resources/config/ats-config.properties");
            return new CandidateProfile();
        }

        if (cvText == null || cvText.isBlank()) {
            LOGGER.warn("Empty CV text — returning empty profile");
            return new CandidateProfile();
        }

        try {
            String prompt = String.format(EXTRACTION_PROMPT_TEMPLATE,
                    cvText.substring(0, Math.min(cvText.length(), 8000))); // Token safety
            String responseJson = callGeminiApi(prompt);
            return parseGeminiResponse(responseJson);
        } catch (Exception e) {
            LOGGER.error("Gemini CV extraction failed", e);
            return new CandidateProfile();
        }
    }

    /** Returns true if the service is configured and ready to use. */
    public boolean isEnabled() {
        return enabled && !apiKey.isBlank() && !apiKey.equals("YOUR_GEMINI_API_KEY_HERE");
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String callGeminiApi(String prompt) throws Exception {
        String url = String.format(GEMINI_BASE_URL, model, apiKey);

        // Build Gemini request body
        String requestBody = """
                {
                  "contents": [{
                    "parts": [{"text": %s}]
                  }],
                  "generationConfig": {
                    "temperature": 0.1,
                    "maxOutputTokens": 1024
                  }
                }
                """.formatted(objectMapper.writeValueAsString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private CandidateProfile parseGeminiResponse(String responseJson) throws Exception {
        // Gemini wraps output in candidates[0].content.parts[0].text
        var root       = objectMapper.readTree(responseJson);
        var candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            LOGGER.warn("Gemini returned no candidates in response");
            return new CandidateProfile();
        }

        String text = candidates.get(0)
                .path("content").path("parts").get(0).path("text").asText();

        // Strip markdown code fences if present (``̀json...``̀)
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        return objectMapper.readValue(text, CandidateProfile.class);
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = GeminiCvExtractorService.class.getResourceAsStream(CONFIG_PATH)) {
            if (is != null) {
                props.load(is);
            } else {
                LOGGER.warn("ats-config.properties not found at {}. Gemini will be disabled.", CONFIG_PATH);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load ats-config.properties", e);
        }
        return props;
    }
}
