package services.forum;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import entities.forum.ForumAiSuggestion;
import entities.forum.ForumComment;
import entities.forum.ForumTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.ServiceSupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ForumAiAssistantService extends ServiceSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForumAiAssistantService.class);
    private static final int KEYWORD_SEARCH_LIMIT = 15;
    private static final int FINAL_TOPIC_LIMIT = 5;
    private static final int AI_CONTEXT_TOPIC_LIMIT = 10;
    private static final int CACHE_DAYS = 30;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "how", "what", "when", "where", "why", "who", "the", "is", "are", "was", "were",
            "can", "could", "should", "would", "do", "does", "did", "my", "me", "you", "your",
            "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "this",
            "that", "it", "its", "not", "no", "so", "if", "then", "than", "too", "very",
            "just", "about", "also", "been", "have", "has", "had", "will",
            "le", "la", "les", "un", "une", "des", "du", "de", "et", "ou", "est", "son",
            "sa", "ses", "ce", "cette", "ces", "qui", "que", "quoi", "dans", "par", "pour",
            "sur", "avec", "sans", "pas", "plus", "ne", "se", "je", "tu", "il", "elle",
            "nous", "vous", "ils", "elles", "mon", "ton", "mes", "tes", "nos", "vos",
            "comment", "quel", "quelle", "quels", "quelles", "aux", "au"
    ));

    private final GroqApiService groqApi = new GroqApiService();
    private final ServiceForumAiSuggestion suggestionService = new ServiceForumAiSuggestion();
    private final ServiceForumComment commentService = new ServiceForumComment();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isAvailable() {
        return groqApi.isAvailable();
    }

    public SimilarTopicsResult getSimilarTopics(String question, Integer categoryId) {
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion == null || normalizedQuestion.length() < 3) {
            return new SimilarTopicsResult(List.of(), null, null, false);
        }

        String hash = generateQuestionHash(normalizedQuestion, categoryId);
        ForumAiSuggestion cached = suggestionService.findCachedSuggestion(hash);
        if (cached != null && !isExpired(cached)) {
            suggestionService.incrementUsage(cached.getId());
            return new SimilarTopicsResult(
                    parseCachedSuggestions(cached.getSuggestions()),
                    cached.getAiResponse(),
                    null,
                    true
            );
        }

        List<TopicCandidate> keywordResults = searchByKeywords(normalizedQuestion, categoryId, KEYWORD_SEARCH_LIMIT);
        if (keywordResults.isEmpty()) {
            String directAnswer = isAvailable()
                    ? generateTopicAnswer(normalizedQuestion, normalizedQuestion, List.of())
                    : null;
            String advice = directAnswer == null ? "No similar topics found. You can create a new topic." : null;
            return new SimilarTopicsResult(List.of(), advice, directAnswer, false);
        }

        AiSimilarTopicsResult aiResult = isAvailable()
                ? findSimilarTopicsWithAi(normalizedQuestion, keywordResults)
                : AiSimilarTopicsResult.empty();

        String directAnswer = aiResult.directAnswer();
        if (directAnswer == null && isAvailable()) {
            directAnswer = generateTopicAnswer(normalizedQuestion, normalizedQuestion, List.of());
        }

        List<TopicSuggestion> finalTopics = combineResults(keywordResults, aiResult.topicIds());
        cacheResults(hash, normalizedQuestion, finalTopics, aiResult.advice());

        return new SimilarTopicsResult(finalTopics, aiResult.advice(), directAnswer, false);
    }

    public String generateTopicAnswer(ForumTopic topic, List<ForumComment> comments) {
        List<ExistingCommentContext> contexts = comments.stream()
                .limit(5)
                .map(comment -> new ExistingCommentContext(
                        truncate(comment.getContent(), 500),
                        comment.isIsAccepted(),
                        comment.isIsTeacherResponse()
                ))
                .toList();

        return generateTopicAnswer(
                topic.getTitle(),
                truncate(topic.getContent(), 1000),
                contexts
        );
    }

    public String generateTopicAnswer(String title, String content, List<ExistingCommentContext> existingComments) {
        if (!isAvailable()) {
            return null;
        }

        StringBuilder commentsContext = new StringBuilder();
        if (existingComments != null && !existingComments.isEmpty()) {
            commentsContext.append("\n\nExisting answers from other users:\n");
            for (int i = 0; i < Math.min(existingComments.size(), 5); i++) {
                ExistingCommentContext comment = existingComments.get(i);
                commentsContext.append(i + 1).append(".");
                if (comment.accepted()) {
                    commentsContext.append(" [ACCEPTED ANSWER]");
                }
                if (comment.teacherResponse()) {
                    commentsContext.append(" [TEACHER RESPONSE]");
                }
                commentsContext.append(" ").append(comment.content()).append("\n");
            }
        }

        String prompt = """
                You are UniLearn AI, a helpful teaching assistant for a university forum. A student posted a question:

                Title: %s
                Question: %s
                %s

                Provide a helpful, concise answer in 3 to 4 short paragraphs. Be educational and encouraging.
                If the topic has existing accepted answers, summarize them and add value.
                If no good answers exist, provide your best guidance.
                Use simple language appropriate for university students.
                If you are not sure, say so and suggest where to find more information.
                Do not use markdown code blocks.
                """.formatted(title, content, commentsContext);

        return groqApi.generateContent(prompt);
    }

    public ToxicityResult checkToxicity(String text) {
        if (!isAvailable() || normalize(text) == null || text.trim().length() < 3) {
            return new ToxicityResult(false, "none", "");
        }

        String prompt = """
                You are a content moderation assistant for a university forum. Analyze the following text for:
                - Profanity or vulgar language
                - Hate speech, racism, sexism
                - Harassment or bullying
                - Spam or promotional content
                - Threats or violent language

                Text to analyze: "%s"

                Respond in JSON format only:
                {
                  "isToxic": true/false,
                  "severity": "none" | "low" | "medium" | "high",
                  "reason": "brief explanation",
                  "flaggedWords": ["word1", "word2"]
                }
                """.formatted(text);

        JsonNode json = groqApi.extractJsonObject(groqApi.generateContent(prompt));
        if (json == null) {
            return new ToxicityResult(false, "none", "Unable to check");
        }

        return new ToxicityResult(
                json.path("isToxic").asBoolean(false),
                textValue(json, "severity", "none"),
                textValue(json, "reason", "")
        );
    }

    public QualityRatingResult rateAnswerQuality(String question, String answer) {
        if (!isAvailable()) {
            return new QualityRatingResult(0, "Not rated", "AI service is not available");
        }

        String prompt = """
                You are evaluating the quality of an answer on a university forum.

                Question: %s
                Answer: %s

                Rate the answer quality from 1 to 5 stars based on:
                - Relevance to the question
                - Accuracy and correctness
                - Clarity and helpfulness
                - Completeness

                Respond in JSON format only:
                {
                  "score": 1-5,
                  "label": "Poor" | "Below Average" | "Average" | "Good" | "Excellent",
                  "reason": "one sentence explaining the rating"
                }
                """.formatted(question, answer);

        JsonNode json = groqApi.extractJsonObject(groqApi.generateContent(prompt));
        if (json == null) {
            return new QualityRatingResult(0, "Not rated", "Unable to rate");
        }

        int score = Math.max(0, Math.min(5, json.path("score").asInt(0)));
        return new QualityRatingResult(
                score,
                textValue(json, "label", "Not rated"),
                textValue(json, "reason", "")
        );
    }

    private AiSimilarTopicsResult findSimilarTopicsWithAi(String question, List<TopicCandidate> topicCandidates) {
        String prompt = """
                You are a helpful teaching assistant for a university forum.

                A student is asking: "%s"

                Here are recent discussions from our forum:
                %s

                Task:
                1. Suggest the top 3 most relevant existing discussions that might help answer this question.
                2. Provide a direct, helpful answer to the student's question in 2 to 3 paragraphs. Use simple language. Do not use markdown.

                Respond in JSON format:
                {
                  "suggestions": [
                    {"id": topic_id, "title": "topic title", "relevance": "why it is relevant"}
                  ],
                  "needsNewTopic": true/false,
                  "advice": "brief advice for the student",
                  "directAnswer": "Your full helpful answer to the student's question here."
                }
                """.formatted(question, formatTopicsForAi(topicCandidates));

        JsonNode json = groqApi.extractJsonObject(groqApi.generateContent(prompt));
        if (json == null) {
            return AiSimilarTopicsResult.empty();
        }

        List<Integer> topicIds = new ArrayList<>();
        JsonNode suggestions = json.path("suggestions");
        if (suggestions.isArray()) {
            for (JsonNode suggestion : suggestions) {
                int id = suggestion.path("id").asInt(0);
                if (id > 0) {
                    topicIds.add(id);
                }
            }
        }

        return new AiSimilarTopicsResult(
                topicIds,
                textOrNull(json, "advice"),
                textOrNull(json, "directAnswer")
        );
    }

    private List<TopicCandidate> searchByKeywords(String question, Integer categoryId, int limit) {
        List<String> keywords = extractKeywords(question);
        List<TopicCandidate> results = executeKeywordSearch(keywords, categoryId, limit);

        if (results.isEmpty()) {
            List<String> broadKeywords = extractBroadKeywords(question);
            if (!broadKeywords.equals(keywords)) {
                results = executeKeywordSearch(broadKeywords, categoryId, limit);
            }
        }

        if (results.isEmpty()) {
            List<String> rawWords = Arrays.stream(question.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                    .filter(word -> word.length() >= 2)
                    .distinct()
                    .toList();
            results = executeKeywordSearch(rawWords, categoryId, limit);
        }

        return results;
    }

    private List<TopicCandidate> executeKeywordSearch(List<String> keywords, Integer categoryId, int limit) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    ft.id,
                    ft.title,
                    ft.content,
                    ft.status,
                    ft.view_count,
                    ft.created_at,
                    fc.name AS category_name,
                    (SELECT COUNT(*) FROM forum_comment c WHERE c.topic_id = ft.id) AS comments_count,
                    (SELECT COUNT(*) FROM forum_comment c WHERE c.topic_id = ft.id AND c.is_accepted = 1) AS accepted_count
                FROM forum_topic ft
                LEFT JOIN forum_category fc ON ft.category_id = fc.id
                WHERE ft.status <> ?
                """);

        if (categoryId != null) {
            sql.append(" AND ft.category_id = ?");
        }

        sql.append(" AND (");
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("ft.title LIKE ? OR ft.content LIKE ?");
        }
        sql.append(") ORDER BY ft.view_count DESC, ft.created_at DESC LIMIT ?");

        List<TopicCandidate> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setString(index++, "locked");
            if (categoryId != null) {
                statement.setInt(index++, categoryId);
            }
            for (String keyword : keywords) {
                String pattern = "%" + keyword + "%";
                statement.setString(index++, pattern);
                statement.setString(index++, pattern);
            }
            statement.setInt(index, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapTopicCandidate(resultSet));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Forum AI keyword search failed", exception);
        }

        return results;
    }

    private TopicCandidate mapTopicCandidate(ResultSet resultSet) throws SQLException {
        return new TopicCandidate(
                resultSet.getInt("id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("status"),
                resultSet.getString("category_name"),
                resultSet.getInt("comments_count"),
                resultSet.getInt("accepted_count") > 0,
                resultSet.getInt("view_count"),
                resultSet.getTimestamp("created_at")
        );
    }

    private List<String> extractKeywords(String text) {
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return Arrays.stream(cleaned.split("\\s+"))
                .map(String::trim)
                .filter(word -> word.length() >= 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .distinct()
                .toList();
    }

    private List<String> extractBroadKeywords(String text) {
        Set<String> basicStopWords = Set.of("a", "i", "an", "the", "is", "le", "la", "un", "une", "de", "et");
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return Arrays.stream(cleaned.split("\\s+"))
                .map(String::trim)
                .filter(word -> word.length() >= 2)
                .filter(word -> !basicStopWords.contains(word))
                .distinct()
                .toList();
    }

    private List<TopicSuggestion> combineResults(List<TopicCandidate> keywordResults, List<Integer> aiTopicIds) {
        Map<Integer, TopicCandidate> candidatesById = new LinkedHashMap<>();
        for (TopicCandidate candidate : keywordResults) {
            candidatesById.put(candidate.id(), candidate);
        }

        List<TopicSuggestion> ordered = new ArrayList<>();
        if (aiTopicIds != null) {
            for (Integer id : aiTopicIds) {
                TopicCandidate candidate = candidatesById.get(id);
                if (candidate != null && ordered.stream().noneMatch(topic -> topic.id() == id)) {
                    ordered.add(candidate.toSuggestion());
                }
            }
        }

        for (TopicCandidate candidate : keywordResults) {
            if (ordered.size() >= FINAL_TOPIC_LIMIT) {
                break;
            }
            if (ordered.stream().noneMatch(topic -> topic.id() == candidate.id())) {
                ordered.add(candidate.toSuggestion());
            }
        }

        return ordered;
    }

    private String formatTopicsForAi(List<TopicCandidate> topics) {
        StringBuilder formatted = new StringBuilder();
        int count = 0;
        for (TopicCandidate topic : topics) {
            if (count >= AI_CONTEXT_TOPIC_LIMIT) {
                break;
            }
            String status = topic.hasAcceptedAnswers() ? "[SOLVED]" : "[OPEN]";
            formatted.append(topic.id())
                    .append(". ")
                    .append(status)
                    .append(" \"")
                    .append(topic.title())
                    .append("\" - ")
                    .append(topic.commentsCount())
                    .append(" answers - Category: ")
                    .append(valueOrDefault(topic.categoryName(), "General"))
                    .append("\n");
            count++;
        }
        return formatted.toString();
    }

    private void cacheResults(String hash, String question, List<TopicSuggestion> topics, String advice) {
        try {
            ForumAiSuggestion suggestion = new ForumAiSuggestion();
            suggestion.setQuestionHash(hash);
            suggestion.setQuestion(question);
            suggestion.setSuggestions(objectMapper.writeValueAsString(topics));
            suggestion.setAiResponse(advice);
            suggestion.setCreatedAt(Timestamp.from(Instant.now()));
            suggestion.setExpiresAt(Timestamp.from(Instant.now().plus(CACHE_DAYS, ChronoUnit.DAYS)));
            suggestion.setUsageCount(0);
            suggestionService.add(suggestion);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to serialize forum AI cache", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to cache forum AI result", exception);
        }
    }

    private List<TopicSuggestion> parseCachedSuggestions(String suggestionsJson) {
        if (suggestionsJson == null || suggestionsJson.isBlank()) {
            return List.of();
        }

        List<TopicSuggestion> suggestions = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(suggestionsJson);
            if (!root.isArray()) {
                return List.of();
            }

            for (JsonNode node : root) {
                suggestions.add(new TopicSuggestion(
                        node.path("id").asInt(),
                        textValue(node, "title", ""),
                        node.path("commentsCount").asInt(0),
                        node.path("hasAcceptedAnswers").asBoolean(false),
                        textOrNull(node, "categoryName"),
                        textValue(node, "status", "open")
                ));
            }
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Unable to parse cached forum AI suggestions", exception);
        }
        return suggestions;
    }

    private boolean isExpired(ForumAiSuggestion suggestion) {
        return suggestion.getExpiresAt() != null && suggestion.getExpiresAt().before(Timestamp.from(Instant.now()));
    }

    private String generateQuestionHash(String question, Integer categoryId) {
        String normalized = question.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        String source = normalized + "_" + (categoryId == null ? "all" : categoryId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static String textValue(JsonNode node, String field, String fallback) {
        return valueOrDefault(textOrNull(node, field), fallback);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String valueOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SimilarTopicsResult(
            List<TopicSuggestion> topics,
            String aiAdvice,
            String directAnswer,
            boolean fromCache
    ) {
    }

    public record TopicSuggestion(
            int id,
            String title,
            int commentsCount,
            boolean hasAcceptedAnswers,
            String categoryName,
            String status
    ) {
    }

    public record ExistingCommentContext(
            String content,
            boolean accepted,
            boolean teacherResponse
    ) {
    }

    public record ToxicityResult(
            boolean isToxic,
            String severity,
            String reason
    ) {
    }

    public record QualityRatingResult(
            int score,
            String label,
            String reason
    ) {
    }

    private record TopicCandidate(
            int id,
            String title,
            String content,
            String status,
            String categoryName,
            int commentsCount,
            boolean hasAcceptedAnswers,
            int viewCount,
            Timestamp createdAt
    ) {
        TopicSuggestion toSuggestion() {
            return new TopicSuggestion(
                    id,
                    title,
                    commentsCount,
                    hasAcceptedAnswers,
                    categoryName,
                    valueOrDefault(status, "open")
            );
        }
    }

    private record AiSimilarTopicsResult(
            List<Integer> topicIds,
            String advice,
            String directAnswer
    ) {
        static AiSimilarTopicsResult empty() {
            return new AiSimilarTopicsResult(List.of(), null, null);
        }
    }
}
