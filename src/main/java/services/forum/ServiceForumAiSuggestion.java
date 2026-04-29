package services.forum;

import entities.forum.ForumAiSuggestion;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumAiSuggestion extends ServiceSupport implements IService<ForumAiSuggestion> {

    @Override
    public void add(ForumAiSuggestion suggestion) {
        String sql = "INSERT INTO forum_ai_suggestion (question_hash, question, suggestions, ai_response, created_at, expires_at, usage_count) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, suggestion.getQuestionHash());
            statement.setString(2, suggestion.getQuestion());
            statement.setString(3, suggestion.getSuggestions());
            setNullableString(statement, 4, suggestion.getAiResponse());
            statement.setTimestamp(5, suggestion.getCreatedAt());
            statement.setTimestamp(6, suggestion.getExpiresAt());
            statement.setInt(7, suggestion.getUsageCount());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    suggestion.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumAiSuggestion suggestion) {
        String sql = "UPDATE forum_ai_suggestion SET question_hash = ?, question = ?, suggestions = ?, ai_response = ?, created_at = ?, expires_at = ?, usage_count = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, suggestion.getQuestionHash());
            statement.setString(2, suggestion.getQuestion());
            statement.setString(3, suggestion.getSuggestions());
            setNullableString(statement, 4, suggestion.getAiResponse());
            statement.setTimestamp(5, suggestion.getCreatedAt());
            statement.setTimestamp(6, suggestion.getExpiresAt());
            statement.setInt(7, suggestion.getUsageCount());
            statement.setInt(8, suggestion.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ForumAiSuggestion suggestion) {
        String sql = "DELETE FROM forum_ai_suggestion WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, suggestion.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ForumAiSuggestion> getALL() {
        String sql = "SELECT id, question_hash, question, suggestions, ai_response, created_at, expires_at, usage_count FROM forum_ai_suggestion";
        List<ForumAiSuggestion> suggestions = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ForumAiSuggestion suggestion = new ForumAiSuggestion();
                suggestion.setId(resultSet.getInt("id"));
                suggestion.setQuestionHash(resultSet.getString("question_hash"));
                suggestion.setQuestion(resultSet.getString("question"));
                suggestion.setSuggestions(resultSet.getString("suggestions"));
                suggestion.setAiResponse(resultSet.getString("ai_response"));
                suggestion.setCreatedAt(resultSet.getTimestamp("created_at"));
                suggestion.setExpiresAt(resultSet.getTimestamp("expires_at"));
                suggestion.setUsageCount(resultSet.getInt("usage_count"));
                suggestions.add(suggestion);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return suggestions;
    }

    public ForumAiSuggestion findCachedSuggestion(String questionHash) {
        String sql = "SELECT id, question_hash, question, suggestions, ai_response, created_at, expires_at, usage_count " +
                "FROM forum_ai_suggestion WHERE question_hash = ? AND expires_at > CURRENT_TIMESTAMP " +
                "ORDER BY created_at DESC LIMIT 1";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, questionHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    public void incrementUsage(int suggestionId) {
        String sql = "UPDATE forum_ai_suggestion SET usage_count = usage_count + 1 WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, suggestionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public int cleanupExpired() {
        String sql = "DELETE FROM forum_ai_suggestion WHERE expires_at < CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return 0;
        }
    }

    private ForumAiSuggestion mapRow(ResultSet resultSet) throws SQLException {
        ForumAiSuggestion suggestion = new ForumAiSuggestion();
        suggestion.setId(resultSet.getInt("id"));
        suggestion.setQuestionHash(resultSet.getString("question_hash"));
        suggestion.setQuestion(resultSet.getString("question"));
        suggestion.setSuggestions(resultSet.getString("suggestions"));
        suggestion.setAiResponse(resultSet.getString("ai_response"));
        suggestion.setCreatedAt(resultSet.getTimestamp("created_at"));
        suggestion.setExpiresAt(resultSet.getTimestamp("expires_at"));
        suggestion.setUsageCount(resultSet.getInt("usage_count"));
        return suggestion;
    }
}
