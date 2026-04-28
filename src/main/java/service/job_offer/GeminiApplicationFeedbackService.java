package service.job_offer;

import entities.job_offer.JobApplication;
import entities.job_offer.JobApplicationStatus;
import entities.job_offer.JobOffer;
import entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Properties;

/**
 * Generates recruiter feedback text for accept/reject/reviewed outcomes using Gemini.
 */
public class GeminiApplicationFeedbackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiApplicationFeedbackService.class);
    private static final String CONFIG_PATH = "/config/ats-config.properties";
    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String FEEDBACK_PROMPT_TEMPLATE = """
            You are helping a hiring partner write a candidate status message.
            Write a precise, professional message for the candidate based on the selected decision and the application context.

            Decision: %s
            Additional partner guidance: %s

            Rules:
            - Return only the message body, no markdown, no bullet points, no intro labels.
            - Keep it between 110 and 190 words.
            - Use a warm, professional, human tone.
            - Be specific and concrete. Avoid generic HR phrases.
            - If the decision is REJECTED, explain the 1 or 2 most important gaps using the job requirements and candidate profile, then give 2 or 3 realistic improvement actions the student can take before applying again.
            - If the decision is ACCEPTED, explain why the candidate is a good fit, say the team wants to move forward, and include one short preparation suggestion for the next step.
            - If the decision is REVIEWED, write a neutral update saying the profile has been reviewed and the team will follow up.
            - When suggesting improvements, focus on skills alignment, CV clarity, project evidence, experience depth, motivation letter quality, and interview preparation when relevant.
            - Do not mention protected characteristics, legal risk, or anything discriminatory.
            - Do not invent facts that are not supported by the application context.
            - Base the message only on the application context below.

            Job offer title: %s
            Job offer type: %s
            Job offer location: %s
            Job offer description: %s
            Job requirements: %s
            Required skills: %s
            Preferred skills: %s
            Minimum experience years: %s
            Minimum education: %s
            Required languages: %s

            Candidate name: %s
            Candidate email: %s
            Candidate score: %s
            Candidate cover message: %s
            Extracted candidate profile JSON: %s
            CV text snippet: %s
            """;

    private static final String STUDENT_ADVICE_PROMPT_TEMPLATE = """
            You are an AI career coach helping a student understand a job application outcome.
            Write practical, professional advice for the student.

            Rules:
            - Return only the advice body, no markdown, no bullet points, no labels.
            - Keep it between 100 and 180 words.
            - Use a supportive but direct tone.
            - If the application was rejected, explain the likely gap briefly and then give concrete next steps.
            - If the application is still under review or accepted, focus on how the student can strengthen their profile for future opportunities.
            - Mention CV improvements, skills alignment, and message quality when relevant.
            - Do not invent facts outside the application context.

            Application status: %s
            Job offer title: %s
            Job offer type: %s
            Job offer location: %s
            Job requirements: %s
            Required skills: %s
            Preferred skills: %s
            Minimum experience years: %s
            Minimum education: %s
            Required languages: %s

            Candidate name: %s
            Candidate score: %s
            Candidate cover message: %s
            Recruiter feedback message: %s
            Extracted candidate profile JSON: %s
            CV text snippet: %s
            """;

    private static final String MOTIVATION_LETTER_PROMPT_TEMPLATE = """
            You are helping a student write a strong motivation letter for a job application.
            Write a complete final motivation letter based on the job offer and student profile.

            Rules:
            - Return only the final letter body, no markdown, no bullet points, no title.
            - Keep it between 170 and 280 words.
            - Use a professional, confident, natural tone.
            - Tailor the letter to the job offer's responsibilities, requirements, skills, and context.
            - Make the student sound genuinely interested in the role and the organization.
            - Highlight the student's relevant strengths, background, skills, and motivation.
            - If student data is limited, write a credible general letter without inventing specific experiences.
            - Do not mention AI, prompts, or missing fields.

            Job offer title: %s
            Job offer type: %s
            Job offer location: %s
            Job offer description: %s
            Job requirements: %s
            Required skills: %s
            Preferred skills: %s
            Minimum experience years: %s
            Minimum education: %s
            Required languages: %s

            Student name: %s
            Student email: %s
            Student location: %s
            Student skills: %s
            Student profile/about: %s
            """;

    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final HttpClient httpClient;

    public GeminiApplicationFeedbackService() {
        Properties config = loadConfig();
        this.apiKey = config.getProperty("gemini.api.key", "").trim();
        this.model = config.getProperty("gemini.model", "gemini-2.0-flash").trim();
        this.enabled = Boolean.parseBoolean(config.getProperty("gemini.enabled", "false").trim());
        int timeoutSec = Integer.parseInt(config.getProperty("gemini.timeout.seconds", "30"));

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();
    }

    public boolean isEnabled() {
        return enabled && !apiKey.isBlank() && !apiKey.equals("YOUR_GEMINI_API_KEY_HERE");
    }

    public String generateFeedback(JobApplication application, JobApplicationStatus decision) {
        return generateFeedback(application, decision, null);
    }

    public String generateFeedback(JobApplication application,
                                   JobApplicationStatus decision,
                                   String partnerGuidance) {
        if (application == null) {
            throw new IllegalArgumentException("Application is required.");
        }
        if (decision == null) {
            throw new IllegalArgumentException("Decision is required.");
        }
        if (!isEnabled()) {
            throw new IllegalStateException("Gemini feedback generation is not configured.");
        }

        try {
            String prompt = buildPrompt(application, decision, partnerGuidance);
            String generated = extractMessage(callGeminiApi(prompt));
            return ensureProfessionalFeedback(generated, application, decision, partnerGuidance);
        } catch (Exception exception) {
            LOGGER.warn("Gemini feedback generation failed, using local fallback message", exception);
            return buildFallbackFeedback(application, decision, partnerGuidance);
        }
    }

    public String generateStudentAdvice(JobApplication application) {
        if (application == null) {
            throw new IllegalArgumentException("Application is required.");
        }

        if (!isEnabled()) {
            return buildFallbackStudentAdvice(application);
        }

        try {
            String prompt = buildStudentAdvicePrompt(application);
            String generated = extractMessage(callGeminiApi(prompt));
            return ensureStudentAdvice(generated, application);
        } catch (Exception exception) {
            LOGGER.warn("Gemini student advice generation failed, using local fallback advice", exception);
            return buildFallbackStudentAdvice(application);
        }
    }

    public String generateMotivationLetter(JobOffer offer, User student) {
        if (offer == null) {
            throw new IllegalArgumentException("Job offer is required.");
        }
        if (student == null) {
            throw new IllegalArgumentException("Student is required.");
        }

        if (!isEnabled()) {
            return buildFallbackMotivationLetter(offer, student);
        }

        try {
            String prompt = buildMotivationLetterPrompt(offer, student);
            return extractMessage(callGeminiApi(prompt));
        } catch (Exception exception) {
            LOGGER.warn("Gemini motivation letter generation failed, using local fallback letter", exception);
            return buildFallbackMotivationLetter(offer, student);
        }
    }

    private String buildPrompt(JobApplication application,
                               JobApplicationStatus decision,
                               String partnerGuidance) {
        JobOffer offer = application.getJobOffer();

        String cvSnippet = readCvSnippet(application.getCvFileName());

        return FEEDBACK_PROMPT_TEMPLATE.formatted(
                decision.name(),
                limit(safe(partnerGuidance), 700),
                safe(offer != null ? offer.getTitle() : null),
                safe(offer != null ? offer.getType() : null),
                safe(offer != null ? offer.getLocation() : null),
                limit(safe(offer != null ? offer.getDescription() : null), 1800),
                limit(safe(offer != null ? offer.getRequirements() : null), 1400),
                safe(offer != null ? offer.getRequiredSkills() : null),
                safe(offer != null ? offer.getPreferredSkills() : null),
                offer != null && offer.getMinExperienceYears() != null ? offer.getMinExperienceYears() : "Not specified",
                safe(offer != null ? offer.getMinEducation() : null),
                safe(offer != null ? offer.getRequiredLanguages() : null),
                safe(application.getUser() != null ? application.getUser().getName() : null),
                safe(application.getUser() != null ? application.getUser().getEmail() : null),
                application.getScore() != null ? application.getScore() + "/100" : "Not calculated",
                limit(safe(application.getMessage()), 1200),
                limit(safe(application.getExtractedData()), 2200),
                cvSnippet
        );
    }

    private String buildStudentAdvicePrompt(JobApplication application) {
        JobOffer offer = application.getJobOffer();
        String cvSnippet = readCvSnippet(application.getCvFileName());

        return STUDENT_ADVICE_PROMPT_TEMPLATE.formatted(
                safe(application.getStatus()),
                safe(offer != null ? offer.getTitle() : null),
                safe(offer != null ? offer.getType() : null),
                safe(offer != null ? offer.getLocation() : null),
                limit(safe(offer != null ? offer.getRequirements() : null), 1400),
                safe(offer != null ? offer.getRequiredSkills() : null),
                safe(offer != null ? offer.getPreferredSkills() : null),
                offer != null && offer.getMinExperienceYears() != null ? offer.getMinExperienceYears() : "Not specified",
                safe(offer != null ? offer.getMinEducation() : null),
                safe(offer != null ? offer.getRequiredLanguages() : null),
                safe(application.getUser() != null ? application.getUser().getName() : null),
                application.getScore() != null ? application.getScore() + "/100" : "Not calculated",
                limit(safe(application.getMessage()), 1200),
                limit(safe(application.getStatusMessage()), 1200),
                limit(safe(application.getExtractedData()), 2200),
                cvSnippet
        );
    }

    private String buildMotivationLetterPrompt(JobOffer offer, User student) {
        return MOTIVATION_LETTER_PROMPT_TEMPLATE.formatted(
                safe(offer.getTitle()),
                safe(offer.getType()),
                safe(offer.getLocation()),
                limit(safe(offer.getDescription()), 1800),
                limit(safe(offer.getRequirements()), 1400),
                safe(offer.getRequiredSkills()),
                safe(offer.getPreferredSkills()),
                offer.getMinExperienceYears() != null ? offer.getMinExperienceYears() : "Not specified",
                safe(offer.getMinEducation()),
                safe(offer.getRequiredLanguages()),
                safe(student.getName()),
                safe(student.getEmail()),
                safe(student.getLocation()),
                limit(safe(student.getSkills()), 1200),
                limit(safe(student.getAbout()), 1200)
        );
    }

    private String readCvSnippet(String cvPath) {
        if (cvPath == null || cvPath.isBlank()) {
            return "No CV text available.";
        }

        try {
            File file = new File(cvPath.trim());
            if (!file.exists() || !file.isFile()) {
                return "CV file not available on disk.";
            }

            String lowerName = file.getName().toLowerCase();
            if (!(lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".csv")
                    || lowerName.endsWith(".log") || lowerName.endsWith(".json") || lowerName.endsWith(".xml")
                    || lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerName.endsWith(".doc")
                    || lowerName.endsWith(".docx") || lowerName.endsWith(".pdf"))) {
                return "CV exists but cannot be read as plain text in this workflow.";
            }

            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return "CV file is empty.";
            }
            return limit(raw.replaceAll("\\s+", " ").trim(), 2500);
        } catch (Exception exception) {
            LOGGER.debug("Failed reading CV snippet", exception);
            return "CV text could not be extracted directly.";
        }
    }

    private String callGeminiApi(String prompt) throws Exception {
        String url = String.format(GEMINI_BASE_URL, model, apiKey);

        String requestBody = """
                {
                  "contents": [{
                    "parts": [{"text": %s}]
                  }],
                  "generationConfig": {
                    "temperature": 0.4,
                    "maxOutputTokens": 400
                  }
                }
                """.formatted(toJsonString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(1200L * attempt);
                continue;
            }
            break;
        }

        throw new IOException("Gemini API error " + (response == null ? "unknown" : response.statusCode()));
    }

    private String buildFallbackFeedback(JobApplication application,
                                         JobApplicationStatus decision,
                                         String partnerGuidance) {
        JobOffer offer = application.getJobOffer();
        String candidateName = safe(application.getUser() != null ? application.getUser().getName() : null);
        if ("Not provided".equals(candidateName)) {
            candidateName = "Candidate";
        }

        String offerTitle = safe(offer != null ? offer.getTitle() : null);
        String requiredSkills = summarizeSkills(offer != null ? offer.getRequiredSkills() : null);
        String preferredSkills = summarizeSkills(offer != null ? offer.getPreferredSkills() : null);
        String scoreText = application.getScore() != null ? application.getScore() + "/100" : "the current evaluation";
        String candidateMessage = safe(application.getMessage());
        String experienceRequirement = offer != null && offer.getMinExperienceYears() != null
                ? offer.getMinExperienceYears() + " year(s) of experience"
                : "the expected level of practical experience";
        String educationRequirement = safe(offer != null ? offer.getMinEducation() : null);
        String partnerTail = appendPartnerGuidance(partnerGuidance);

        return switch (decision) {
            case ACCEPTED -> candidateName + ", thank you for your application to " + offerTitle
                    + ". After reviewing your profile, CV, and overall fit for the role, we would like to move forward with your application. "
                    + "Your profile shows solid alignment with the main requirements, especially in " + requiredSkills + ", and your application message communicated genuine interest in the opportunity. "
                    + "We also see potential in the way your background could contribute to " + preferredSkills + ". "
                    + "Based on " + scoreText + ", your application stands out as a strong fit for the next stage. "
                    + "For the next step, prepare clear examples of your work, the tools you used, and the results you achieved."
                    + partnerTail;
            case REJECTED -> candidateName + ", thank you for your interest in " + offerTitle
                    + " and for the time you invested in your application. After reviewing your CV and your overall fit for this role, "
                    + "we decided not to move forward at this stage. The main gap was a need for stronger evidence of " + requiredSkills
                    + " and a clearer match with " + experienceRequirement + ". "
                    + "Your application showed useful potential, especially around " + preferredSkills + ", but we needed more direct proof that your experience already fits this position. "
                    + "To strengthen future applications, make your CV more specific, add project examples with measurable results, and connect your motivation letter more directly to the role requirements."
                    + ("Not provided".equals(candidateMessage) ? "" : " A more tailored application message would also help the reviewer understand your fit faster.")
                    + ("Not provided".equals(educationRequirement) ? "" : " If relevant, highlight how your education meets or supports " + educationRequirement + ".")
                    + partnerTail;
            default -> candidateName + ", thank you for applying to " + offerTitle
                    + ". We have reviewed your application, CV, and current fit for the role. "
                    + "Your profile is being evaluated in relation to the position requirements, especially around " + requiredSkills + ". "
                    + "We also noted useful strengths in " + preferredSkills + ". "
                    + "Based on " + scoreText + ", your application remains under review and our team will follow up with you once the next decision is made."
                    + partnerTail;
        };
    }

    private String ensureProfessionalFeedback(String generated,
                                              JobApplication application,
                                              JobApplicationStatus decision,
                                              String partnerGuidance) {
        String normalized = normalizeGeneratedFeedback(generated);
        if (isFeedbackTooGeneric(normalized, decision)) {
            return buildFallbackFeedback(application, decision, partnerGuidance);
        }
        return normalized;
    }

    private String normalizeGeneratedFeedback(String generated) {
        String text = safe(generated);
        if ("Not provided".equals(text)) {
            return "";
        }

        text = text.replaceAll("^[\\s>*`#-]+", "").trim();
        text = text.replaceAll("(?i)^dear\\s+[^,\\n]+,\\s*", "");
        text = text.replaceAll("(?i)^hello\\s+[^,\\n]+,\\s*", "");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private boolean isFeedbackTooGeneric(String feedback, JobApplicationStatus decision) {
        if (feedback == null || feedback.isBlank()) {
            return true;
        }

        String lower = feedback.toLowerCase();
        if (feedback.length() < 140) {
            return true;
        }
        if (lower.startsWith("thank you for your interest in")) {
            return true;
        }
        if (lower.contains("dear student")) {
            return true;
        }
        if (!lower.contains("cv") && !lower.contains("profile") && !lower.contains("application")) {
            return true;
        }
        if (decision == JobApplicationStatus.REJECTED
                && !lower.contains("improve")
                && !lower.contains("strengthen")
                && !lower.contains("develop")
                && !lower.contains("highlight")) {
            return true;
        }
        if (decision == JobApplicationStatus.ACCEPTED
                && !lower.contains("next step")
                && !lower.contains("prepare")
                && !lower.contains("move forward")) {
            return true;
        }
        return false;
    }

    private String appendPartnerGuidance(String partnerGuidance) {
        String guidance = safe(partnerGuidance);
        if ("Not provided".equals(guidance)) {
            return "";
        }
        return " " + guidance;
    }

    private String buildFallbackStudentAdvice(JobApplication application) {
        JobOffer offer = application.getJobOffer();
        String requiredSkills = summarizeSkills(offer != null ? offer.getRequiredSkills() : null);
        String preferredSkills = summarizeSkills(offer != null ? offer.getPreferredSkills() : null);
        String recruiterMessage = safe(application.getStatusMessage());
        boolean hasRecruiterMessage = !"Not provided".equals(recruiterMessage);

        String scoreText;
        if (application.getScore() == null) {
            scoreText = "There is no ATS score yet, so the strongest immediate move is to make your CV clearer and more tailored to the role.";
        } else if (application.getScore() >= 75) {
            scoreText = "Your profile already looks fairly strong on paper, so the biggest gains will come from sharper tailoring and stronger evidence of impact.";
        } else if (application.getScore() >= 50) {
            scoreText = "Your profile shows partial alignment, which means you should make the match more obvious and better supported.";
        } else {
            scoreText = "Your current profile likely did not show enough alignment with the role, so a focused rewrite is worth doing before the next application.";
        }

        String recruiterContext = hasRecruiterMessage
                ? " The recruiter feedback suggests that the main gap was fit for this specific role."
                : "";

        return scoreText
                + recruiterContext
                + " Revise your CV so the strongest proof of " + requiredSkills
                + " appears near the top, using concrete projects, tools, and measurable results. "
                + "Then improve your application message by connecting your background directly to the offer instead of staying general. "
                + "If possible, strengthen experience around " + preferredSkills
                + " before applying to similar roles again.";
    }

    private String ensureStudentAdvice(String generated, JobApplication application) {
        String normalized = normalizeGeneratedFeedback(generated);
        if (isStudentAdviceTooWeak(normalized)) {
            return buildFallbackStudentAdvice(application);
        }
        return normalized;
    }

    private boolean isStudentAdviceTooWeak(String advice) {
        if (advice == null || advice.isBlank()) {
            return true;
        }

        String lower = advice.toLowerCase();
        if (advice.length() < 120) {
            return true;
        }
        if (!advice.endsWith(".") && !advice.endsWith("!") && !advice.endsWith("?")) {
            return true;
        }
        if (!lower.contains("cv") && !lower.contains("message") && !lower.contains("profile")) {
            return true;
        }
        if (!lower.contains("improve")
                && !lower.contains("strengthen")
                && !lower.contains("revise")
                && !lower.contains("tailor")) {
            return true;
        }
        return false;
    }

    private String buildFallbackMotivationLetter(JobOffer offer, User student) {
        String greeting = "Dear Hiring Team";
        String offerTitle = safe(offer.getTitle());
        String offerType = safe(offer.getType()).toLowerCase();
        String organizationLocation = safe(offer.getLocation());
        String requiredSkills = summarizeSkills(offer.getRequiredSkills());
        String preferredSkills = summarizeSkills(offer.getPreferredSkills());
        String studentSkills = summarizeSkills(student.getSkills());
        String studentAbout = safe(student.getAbout());
        boolean hasAbout = !"Not provided".equals(studentAbout);

        String secondParagraph = hasAbout
                ? "My background and profile reflect a strong interest in this field, and I believe my experience aligns well with the expectations of this opportunity. "
                + "I bring relevant strengths in " + studentSkills + ", and I am motivated to apply them in a practical environment where I can keep learning and contributing."
                : "I am highly motivated to contribute to this opportunity and continue growing through practical experience. "
                + "I believe my current strengths in " + studentSkills + " can help me support the team effectively while also developing the deeper expertise required by the role.";

        String signatureName = safe(student.getName());
        if ("Not provided".equals(signatureName)) {
            signatureName = "Student Candidate";
        }

        return greeting + ",\n\n"
                + "I am writing to express my interest in the " + offerTitle + " " + offerType + " opportunity"
                + ("Not provided".equals(organizationLocation) ? "." : " based in " + organizationLocation + ".")
                + " This position immediately caught my attention because it combines the kind of work environment and responsibilities I am actively seeking at this stage of my development. "
                + "After reviewing the offer, I was particularly interested in the focus on " + requiredSkills + " and the broader expectations described for the role.\n\n"
                + secondParagraph + " "
                + "I am also eager to strengthen my profile further in areas such as " + preferredSkills + ", which makes this role especially valuable for me.\n\n"
                + "What attracts me most to this opportunity is the chance to contribute seriously, learn from real projects, and adapt quickly to your expectations. "
                + "I am confident that my motivation, willingness to learn, and commitment to quality work would allow me to integrate well into your team and add value from the start.\n\n"
                + "Thank you for considering my application. I would welcome the opportunity to discuss my profile in more detail and explain how I can contribute to your team.\n\n"
                + "Sincerely,\n"
                + signatureName;
    }

    private String summarizeSkills(String rawSkills) {
        String safeSkills = safe(rawSkills);
        if ("Not provided".equals(safeSkills)) {
            return "the key role requirements";
        }

        String[] parts = safeSkills.split("[,;\\n]");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (count > 0) {
                builder.append(", ");
            }
            builder.append(trimmed);
            count++;
            if (count == 3) {
                break;
            }
        }
        return count == 0 ? "the key role requirements" : builder.toString();
    }

    private String extractMessage(String responseBody) {
        String marker = "\"text\":";
        int markerIndex = responseBody.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalStateException("Gemini returned no text content.");
        }

        int startQuote = responseBody.indexOf('"', markerIndex + marker.length());
        if (startQuote < 0) {
            throw new IllegalStateException("Gemini returned malformed text content.");
        }

        StringBuilder decoded = new StringBuilder();
        boolean escaping = false;
        for (int i = startQuote + 1; i < responseBody.length(); i++) {
            char current = responseBody.charAt(i);
            if (escaping) {
                switch (current) {
                    case '"' -> decoded.append('"');
                    case '\\' -> decoded.append('\\');
                    case '/' -> decoded.append('/');
                    case 'b' -> decoded.append('\b');
                    case 'f' -> decoded.append('\f');
                    case 'n' -> decoded.append('\n');
                    case 'r' -> decoded.append('\r');
                    case 't' -> decoded.append('\t');
                    case 'u' -> {
                        if (i + 4 >= responseBody.length()) {
                            throw new IllegalStateException("Invalid unicode escape in Gemini response.");
                        }
                        String hex = responseBody.substring(i + 1, i + 5);
                        decoded.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> decoded.append(current);
                }
                escaping = false;
                continue;
            }

            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                break;
            }
            decoded.append(current);
        }

        return decoded.toString().trim();
    }

    private String toJsonString(String text) {
        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Not provided" : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "Not provided";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = GeminiApplicationFeedbackService.class.getResourceAsStream(CONFIG_PATH)) {
            if (is != null) {
                props.load(is);
            } else {
                LOGGER.warn("ats-config.properties not found at {}. Gemini feedback generation will be disabled.", CONFIG_PATH);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to load ats-config.properties", exception);
        }
        return props;
    }
}
