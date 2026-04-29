package service.job_offer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entities.CustomSkill;
import entities.job_offer.CandidateProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.job_offer.CustomSkillRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Gemini-backed CV extraction aligned with the web app flow.
 */
public class GeminiCvExtractorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiCvExtractorService.class);
    private static final String CONFIG_PATH = "/config/ats-config.properties";
    private static final int MAX_PROMPT_CV_CHARS = 8000;
    private static final int MAX_SKILL_HINTS = 50;
    private static final int MAX_ATTEMPTS_PER_MODEL = 2;
    private static final long RETRY_DELAY_MS = 5000L;

    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final List<String> DEFAULT_MODELS = List.of(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite"
    );

    private final String apiKey;
    private final boolean enabled;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CvParserService cvParserService;
    private final CustomSkillRepository customSkillRepository;
    private final List<String> modelOrder;

    public GeminiCvExtractorService() {
        this(loadConfig());
    }

    private GeminiCvExtractorService(Properties config) {
        this.apiKey = config.getProperty("gemini.api.key", "").trim();
        this.enabled = Boolean.parseBoolean(config.getProperty("gemini.enabled", "false").trim());
        int timeoutSec = Integer.parseInt(config.getProperty("gemini.timeout.seconds", "30"));

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();
        this.objectMapper = new ObjectMapper();
        this.cvParserService = new CvParserService();
        this.customSkillRepository = new CustomSkillRepository();
        this.modelOrder = resolveModelOrder(config.getProperty("gemini.model", "").trim());
    }

    public CandidateProfile extractFromFile(File cvFile) throws Exception {
        String cvText = cvParserService.extractTextFromPdf(cvFile);
        return extractFromText(cvText);
    }

    public CandidateProfile extractFromText(String cvText) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("Gemini extraction is not configured.");
        }
        if (cvText == null || cvText.isBlank()) {
            throw new IllegalArgumentException("CV text is empty.");
        }

        String prompt = buildPrompt(cvText, loadSkillHints());
        String response = callGeminiWithFallbacks(prompt);
        return normalizeProfile(parseGeminiResponse(response));
    }

    public boolean isEnabled() {
        return enabled && !apiKey.isBlank() && !apiKey.equals("YOUR_GEMINI_API_KEY_HERE");
    }

    public File resolveCvFile(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            throw new IllegalArgumentException("No CV file attached.");
        }

        File direct = new File(storedValue.trim());
        if (direct.exists()) {
            return direct;
        }

        String normalized = storedValue.trim()
                .replace("\\", File.separator)
                .replace("/", File.separator);
        File normalizedFile = new File(normalized);
        if (normalizedFile.exists()) {
            return normalizedFile;
        }

        String fileNameOnly = new File(normalized).getName();
        File uploadsCv = new File(System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "cvs", fileNameOnly);
        if (uploadsCv.exists()) {
            return uploadsCv;
        }

        throw new IllegalArgumentException("CV file not found: " + storedValue);
    }

    private String callGeminiWithFallbacks(String prompt) throws Exception {
        Exception lastError = null;

        for (String model : modelOrder) {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
                try {
                    return callGeminiApi(model, prompt);
                } catch (Exception exception) {
                    lastError = exception;
                    LOGGER.warn("Gemini extraction failed for model {} attempt {}/{}: {}",
                            model, attempt, MAX_ATTEMPTS_PER_MODEL, exception.getMessage());
                    if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                }
            }
        }

        throw lastError != null ? lastError : new IOException("Gemini extraction failed.");
    }

    private String callGeminiApi(String model, String prompt) throws Exception {
        String requestBody = objectMapper.writeValueAsString(buildRequestBody(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(GEMINI_URL_TEMPLATE, model, apiKey)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            throw new IOException("Gemini rate limit (429)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private Object buildRequestBody(String prompt) {
        return java.util.Map.of(
                "contents", List.of(java.util.Map.of(
                        "parts", List.of(java.util.Map.of("text", prompt))
                )),
                "generationConfig", java.util.Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );
    }

    private CandidateProfile parseGeminiResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        String text = textNode.asText("");
        if (text.isBlank()) {
            throw new IOException("Gemini returned an empty response.");
        }

        String cleanedJson = text
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("^```\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();

        return objectMapper.readValue(cleanedJson, CandidateProfile.class);
    }

    private CandidateProfile normalizeProfile(CandidateProfile rawProfile) {
        CandidateProfile normalized = rawProfile == null ? new CandidateProfile() : rawProfile;
        normalized.setSkills(cleanArray(normalized.getSkills()));
        normalized.setLanguages(cleanArray(normalized.getLanguages()));
        normalized.setPortfolioUrls(cleanArray(normalized.getPortfolioUrls()));
        normalized.setExperienceYears(Math.max(0, normalized.getExperienceYears()));
        normalized.setEducationLevel(normalizeEducationLevel(normalized.getEducationLevel()));
        normalized.setEducationField(cleanScalar(normalized.getEducationField()));
        return normalized;
    }

    private List<String> cleanArray(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = cleanScalar(value);
            if (trimmed != null) {
                cleaned.add(trimmed);
            }
        }
        return new ArrayList<>(cleaned);
    }

    private String cleanScalar(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() ? null : cleaned;
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
            case "bac", "high_school", "high school", "baccalaureat" -> "bac";
            case "bac+2", "bac +2", "bac + 2", "associate", "bts", "dut", "deust" -> "bac+2";
            case "licence", "license", "bachelor", "licence professionnelle" -> "licence";
            case "master", "mastère", "msc", "maitrise" -> "master";
            case "ingenieur", "ingénieur", "engineer", "engineering degree" -> "ingenieur";
            case "doctorat", "doctorate", "phd", "ph.d" -> "doctorat";
            default -> null;
        };
    }

    private String buildPrompt(String cvText, List<String> knownSkills) {
        String truncatedCvText = cvText.length() > MAX_PROMPT_CV_CHARS
                ? cvText.substring(0, MAX_PROMPT_CV_CHARS)
                : cvText;

        String skillHints = knownSkills.isEmpty() ? "Aucune liste de competences disponible." : String.join(", ", knownSkills);

        return """
                Tu es un moteur ATS. Analyse le CV ci-dessous et retourne uniquement un JSON valide.
                Ne retourne aucun texte supplementaire, aucun markdown et aucun bloc de code.

                Structure JSON attendue :
                {
                  "skills": ["skill1", "skill2"],
                  "educationLevel": "bac|bac+2|licence|master|ingenieur|doctorat|null",
                  "educationField": "string ou null",
                  "experienceYears": 0,
                  "languages": ["Francais", "Anglais"],
                  "portfolioUrls": ["https://..."]
                }

                Regles :
                - Utilise uniquement les informations presentes ou raisonnablement inferables depuis le CV.
                - `experienceYears` doit etre un entier >= 0.
                - `skills`, `languages` et `portfolioUrls` doivent etre des tableaux de chaines.
                - `educationLevel` doit etre l'une de ces valeurs exactes : bac, bac+2, licence, master, ingenieur, doctorat.
                - Si la valeur est inconnue, utilise null pour les champs texte et [] pour les tableaux.
                - Voici une liste indicative de competences connues a privilegier quand elles apparaissent : %s

                CV :
                %s
                """.formatted(skillHints, truncatedCvText);
    }

    private List<String> loadSkillHints() {
        try {
            return customSkillRepository.findAll().stream()
                    .map(CustomSkill::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .limit(MAX_SKILL_HINTS)
                    .toList();
        } catch (Exception exception) {
            LOGGER.debug("Could not load skill hints for Gemini extraction", exception);
            return List.of();
        }
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = GeminiCvExtractorService.class.getResourceAsStream(CONFIG_PATH)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to load ats-config.properties", exception);
        }
        return props;
    }

    private List<String> resolveModelOrder(String configuredPrimaryModel) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (configuredPrimaryModel != null && !configuredPrimaryModel.isBlank()) {
            models.add(configuredPrimaryModel);
        }
        models.addAll(DEFAULT_MODELS);
        return new ArrayList<>(models);
    }
}
