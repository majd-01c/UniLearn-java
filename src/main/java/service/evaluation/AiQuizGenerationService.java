package service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.Tika;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class AiQuizGenerationService {

    private static final int MAX_SOURCE_CHARS = 18000;
    private static final int MAX_QUESTION_COUNT = 25;
    private static final Properties FILE_CONFIG = loadFileConfig();

    private final Tika tika = new Tika();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public QuizDraft generateQuizFromFile(File sourceFile, int requestedQuestions) {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Please select a valid source file.");
        }

        int questionCount = Math.max(3, Math.min(requestedQuestions, MAX_QUESTION_COUNT));
        String sourceText = extractText(sourceFile);
        if (sourceText.isBlank()) {
            throw new IllegalArgumentException("The uploaded file does not contain readable text.");
        }

        String geminiApiKey = readConfig("GEMINI_API_KEY");
        String openAiApiKey = readConfig("OPENAI_API_KEY");

        if ((geminiApiKey == null || geminiApiKey.isBlank()) && (openAiApiKey == null || openAiApiKey.isBlank())) {
            throw new IllegalStateException("GEMINI_API_KEY or OPENAI_API_KEY is missing. Configure one before using AI quiz generation.");
        }

        try {
            String prompt = buildPrompt(sourceText, questionCount);
            String content;
            if (geminiApiKey != null && !geminiApiKey.isBlank()) {
                try {
                    content = generateWithGemini(prompt, geminiApiKey);
                } catch (IllegalStateException ex) {
                    if (isQuotaOrUnavailable(ex)) {
                        content = null;
                    } else {
                        throw ex;
                    }
                }
            } else {
                content = generateWithOpenAi(prompt, openAiApiKey);
            }
            if (content == null || content.isBlank()) {
                if (openAiApiKey != null && !openAiApiKey.isBlank()) {
                    try {
                        content = generateWithOpenAi(prompt, openAiApiKey);
                    } catch (IllegalStateException ex) {
                        if (!isQuotaOrUnavailable(ex)) {
                            throw ex;
                        }
                        content = null;
                    }
                }
            }
            if (content == null || content.isBlank()) {
                return generateLocalFallbackQuiz(sourceText, questionCount);
            }
            if (content.isBlank()) {
                throw new IllegalStateException("AI response did not include quiz content.");
            }

            return parseAndValidateQuiz(content, questionCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI quiz generation failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("AI quiz generation failed: " + e.getMessage(), e);
        }
    }

    private String generateWithGemini(String prompt, String apiKey) throws IOException, InterruptedException {
        String configuredModel = readConfig("GEMINI_MODEL");
        if (configuredModel == null || configuredModel.isBlank()) {
            configuredModel = "gemini-2.0-flash";
        }

        List<String> modelsToTry = new ArrayList<>();
        modelsToTry.add(configuredModel);
        modelsToTry.addAll(Arrays.asList(
                "gemini-2.0-flash",
                "gemini-1.5-flash-latest",
                "gemini-1.5-pro-latest"
        ));

        String lastError = "";
        for (String model : modelsToTry) {
            try {
                return callGeminiModel(prompt, apiKey, model);
            } catch (IllegalStateException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                lastError = msg;
                boolean modelMissing = msg.contains("404") || msg.toLowerCase().contains("not found");
                if (!modelMissing) {
                    throw ex;
                }
            }
        }

        throw new IllegalStateException("Gemini model selection failed. Last error: " + lastError);
    }

    private String callGeminiModel(String prompt, String apiKey, String model) throws IOException, InterruptedException {
        String requestJson = buildGeminiRequestJson(prompt);
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model
                + ":generateContent?key="
                + encodedApiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Gemini error (" + response.statusCode() + "): " + shortBody(response.body()));
        }

        JsonNode root = mapper.readTree(response.body());
        return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
    }

    private String generateWithOpenAi(String prompt, String apiKey) throws IOException, InterruptedException {
        String model = readConfig("OPENAI_MODEL");
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini";
        }

        String requestJson = buildOpenAiRequestJson(model, prompt);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("OpenAI error (" + response.statusCode() + "): " + shortBody(response.body()));
        }

        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText("").trim();
    }

    private String extractText(File file) {
        try {
            String full = tika.parseToString(file);
            if (full == null) {
                return "";
            }
            String normalized = full.replace('\u0000', ' ').trim();
            if (normalized.length() > MAX_SOURCE_CHARS) {
                return normalized.substring(0, MAX_SOURCE_CHARS);
            }
            return normalized;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read text from file: " + e.getMessage(), e);
        }
    }

    private String buildOpenAiRequestJson(String model, String prompt) throws IOException {
        JsonNode payload = mapper.createObjectNode()
                .put("model", model)
                .put("temperature", 0.3)
                .set("response_format", mapper.createObjectNode().put("type", "json_object"));

        ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
                .set("messages", mapper.createArrayNode()
                        .add(mapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "You generate pedagogical multiple-choice quizzes. Return valid JSON only."))
                        .add(mapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));

        return mapper.writeValueAsString(payload);
    }

    private String buildGeminiRequestJson(String prompt) throws IOException {
    JsonNode payload = mapper.createObjectNode()
        .set("contents", mapper.createArrayNode()
            .add(mapper.createObjectNode()
                .set("parts", mapper.createArrayNode()
                    .add(mapper.createObjectNode().put("text", prompt)))));

    ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
        .set("generationConfig", mapper.createObjectNode()
            .put("temperature", 0.3)
            .put("responseMimeType", "application/json"));

    return mapper.writeValueAsString(payload);
    }

    private String buildPrompt(String sourceText, int questionCount) {
        return "Create a multiple-choice quiz from the source content below. "
                + "Output ONLY a JSON object with this schema: "
                + "{\"title\":string,\"description\":string,\"questions\":[{\"questionText\":string,\"explanation\":string,\"choices\":[{\"text\":string,\"correct\":boolean}]}]}. "
                + "Rules: produce exactly " + questionCount + " questions; each question must have exactly 4 choices; "
                + "exactly one choice must be correct; question and choices must be concise and based on source content only. "
                + "Source content:\n\n" + sourceText;
    }

    QuizDraft parseAndValidateQuiz(String content, int requestedCount) throws IOException {
        JsonNode quizNode = mapper.readTree(content);
        String title = textOrDefault(quizNode.path("title"), "AI Generated Quiz");
        String description = textOrDefault(quizNode.path("description"), "Quiz generated from uploaded source material.");

        List<QuestionDraft> questions = new ArrayList<>();
        for (JsonNode qNode : quizNode.path("questions")) {
            String questionText = textOrDefault(qNode.path("questionText"), "");
            String explanation = textOrDefault(qNode.path("explanation"), "");

            List<ChoiceDraft> choices = new ArrayList<>();
            int correctCount = 0;
            for (JsonNode cNode : qNode.path("choices")) {
                String text = textOrDefault(cNode.path("text"), "");
                boolean correct = cNode.path("correct").asBoolean(false);
                if (!text.isBlank()) {
                    choices.add(new ChoiceDraft(text, correct));
                    if (correct) {
                        correctCount++;
                    }
                }
            }

            if (!questionText.isBlank() && choices.size() >= 2 && correctCount == 1) {
                questions.add(new QuestionDraft(questionText, explanation, choices));
            }
        }

        if (questions.size() < 3) {
            throw new IllegalStateException("AI returned too few valid quiz questions.");
        }

        if (questions.size() > requestedCount) {
            questions = new ArrayList<>(questions.subList(0, requestedCount));
        }

        return new QuizDraft(title, description, questions);
    }

    public String evaluateAnswer(String questionText, String explanation, String studentAnswer, int maxPoints) {
        String prompt = "You are an expert teacher grading a student's answer to an open-ended question.\n" +
                "Question: " + questionText + "\n" +
                "Reference Explanation/Correct Answer: " + (explanation != null ? explanation : "N/A") + "\n" +
                "Student Answer: " + studentAnswer + "\n" +
                "Max Points: " + maxPoints + "\n\n" +
                "Evaluate the student's answer and output a JSON object with this schema: " +
                "{\"score\":number,\"feedback\":string}. The score must be an integer between 0 and " + maxPoints + ". " +
                "The feedback should explain why this score was given.";

        return callAiWithPrompt(prompt, "{\"score\":0,\"feedback\":\"AI evaluation unavailable.\"}");
    }

    /**
     * Detect if content was likely generated by an AI
     */
    public String detectAiContent(String text) {
        String prompt = "You are a specialized AI detection assistant.\n" +
                "Analyze the following text and determine the likelihood that it was generated by an AI (like ChatGPT, QuillBot, or Claude).\n" +
                "Text: " + text + "\n\n" +
                "Output ONLY a JSON object with this schema: " +
                "{\"likelihood\":number,\"explanation\":string}. " +
                "The likelihood must be a percentage (0-100). The explanation should mention specific AI indicators found in the text.";

        return callAiWithPrompt(prompt, "{\"likelihood\":0,\"explanation\":\"AI detection unavailable.\"}");
    }

    private String callAiWithPrompt(String prompt, String fallbackJson) {
        String geminiApiKey = readConfig("GEMINI_API_KEY");
        String openAiApiKey = readConfig("OPENAI_API_KEY");

        try {
            String content = null;
            if (geminiApiKey != null && !geminiApiKey.isBlank()) {
                content = generateWithGemini(prompt, geminiApiKey);
            } else if (openAiApiKey != null && !openAiApiKey.isBlank()) {
                content = generateWithOpenAi(prompt, openAiApiKey);
            }

            if (content == null || content.isBlank()) {
                return fallbackJson;
            }
            
            // Strip markdown code blocks if present
            String cleaned = content.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\n?", "");
                cleaned = cleaned.replaceAll("\\n?```$", "");
            }
            return cleaned.trim();
        } catch (Exception e) {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode errorNode = mapper.createObjectNode();
                if (fallbackJson.contains("likelihood")) {
                    errorNode.put("likelihood", 0);
                    errorNode.put("explanation", "AI Error: " + e.getMessage());
                } else {
                    errorNode.put("score", 0);
                    errorNode.put("feedback", "AI Error: " + e.getMessage());
                }
                return mapper.writeValueAsString(errorNode);
            } catch (Exception fatal) {
                return fallbackJson.replace("unavailable", "Error: " + e.getMessage());
            }
        }
    }

    private String textOrDefault(JsonNode node, String fallback) {
        String value = node == null ? "" : node.asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private String shortBody(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 250 ? trimmed : trimmed.substring(0, 250) + "...";
    }

    private boolean isQuotaOrUnavailable(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("429")
                || message.contains("quota")
                || message.contains("rate limit")
                || message.contains("too many requests")
                || message.contains("service unavailable")
                || message.contains("503")
                || message.contains("502")
                || message.contains("504");
    }

    private QuizDraft generateLocalFallbackQuiz(String sourceText, int requestedCount) {
        String[] sentences = sourceText
                .replace('\r', ' ')
                .replace('\n', ' ')
                .split("(?<=[.!?])\\s+");

        List<String> baseSentences = new ArrayList<>();
        for (String sentence : sentences) {
            String trimmed = sentence == null ? "" : sentence.trim();
            if (trimmed.length() >= 40) {
                baseSentences.add(trimmed);
            }
            if (baseSentences.size() >= requestedCount) {
                break;
            }
        }

        if (baseSentences.isEmpty()) {
            baseSentences.add(sourceText.trim());
        }

        List<QuestionDraft> questions = new ArrayList<>();
        int count = Math.max(3, Math.min(requestedCount, MAX_QUESTION_COUNT));
        for (int i = 0; i < count; i++) {
            String basis = baseSentences.get(i % baseSentences.size());
            String questionText = "Which statement best matches this part of the source content?";
            String explanation = "Generated locally because the AI provider quota was unavailable.";

            List<ChoiceDraft> choices = new ArrayList<>();
            choices.add(new ChoiceDraft(shortenForChoice(basis), true));
            choices.add(new ChoiceDraft("It introduces a different unrelated topic.", false));
            choices.add(new ChoiceDraft("It is a random distractor statement.", false));
            choices.add(new ChoiceDraft("It does not add meaningful information.", false));
            questions.add(new QuestionDraft(questionText, explanation, choices));
        }

        return new QuizDraft("Generated Quiz", "Generated locally from the uploaded file because AI quota was unavailable.", questions);
    }

    private String shortenForChoice(String text) {
        String cleaned = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 120) {
            return cleaned;
        }
        return cleaned.substring(0, 117) + "...";
    }

    private String readConfig(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return normalizeConfigValue(value.trim());
        }
        value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return normalizeConfigValue(value.trim());
        }
        value = FILE_CONFIG.getProperty(key);
        if (value != null && !value.isBlank()) {
            return normalizeConfigValue(value.trim());
        }

        // Backward compatibility for accidental format: OPENAI_API_KEY=GEMINI_API_KEY=xxxx
        if ("GEMINI_API_KEY".equals(key)) {
            String fallback = FILE_CONFIG.getProperty("OPENAI_API_KEY");
            if (fallback != null && fallback.startsWith("GEMINI_API_KEY=")) {
                return normalizeConfigValue(fallback.substring("GEMINI_API_KEY=".length()));
            }
        }

        return null;
    }

    private String normalizeConfigValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("GEMINI_API_KEY=")) {
            return raw.substring("GEMINI_API_KEY=".length()).trim();
        }
        if (raw.startsWith("OPENAI_API_KEY=")) {
            return raw.substring("OPENAI_API_KEY=".length()).trim();
        }
        return raw.trim();
    }

    private static Properties loadFileConfig() {
        Properties properties = new Properties();
        loadFromResource(properties, "/confg/ai.local.properties");
        return properties;
    }

    private static void loadFromResource(Properties target, String path) {
        try (InputStream in = AiQuizGenerationService.class.getResourceAsStream(path)) {
            if (in != null) {
                Properties source = new Properties();
                source.load(in);
                for (String name : source.stringPropertyNames()) {
                    String value = source.getProperty(name);
                    if (value != null && !value.isBlank()) {
                        target.setProperty(name, value.trim());
                    }
                }
            }
        } catch (IOException ignored) {
            // Best-effort config loading only; env/system values still work.
        }
    }

    public static final class QuizDraft {
        private final String title;
        private final String description;
        private final List<QuestionDraft> questions;

        public QuizDraft(String title, String description, List<QuestionDraft> questions) {
            this.title = title;
            this.description = description;
            this.questions = questions;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public List<QuestionDraft> getQuestions() {
            return questions;
        }
    }

    public static final class QuestionDraft {
        private final String questionText;
        private final String explanation;
        private final List<ChoiceDraft> choices;

        public QuestionDraft(String questionText, String explanation, List<ChoiceDraft> choices) {
            this.questionText = questionText;
            this.explanation = explanation;
            this.choices = choices;
        }

        public String getQuestionText() {
            return questionText;
        }

        public String getExplanation() {
            return explanation;
        }

        public List<ChoiceDraft> getChoices() {
            return choices;
        }
    }

    public static final class ChoiceDraft {
        private final String text;
        private final boolean correct;

        public ChoiceDraft(String text, boolean correct) {
            this.text = text;
            this.correct = correct;
        }

        public String getText() {
            return text;
        }

        public boolean isCorrect() {
            return correct;
        }
    }
}
