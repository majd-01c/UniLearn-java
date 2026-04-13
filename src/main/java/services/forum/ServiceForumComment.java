package services.forum;

import entities.forum.ForumComment;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumComment extends ServiceSupport implements IService<ForumComment> {

    @Override
    public void add(ForumComment comment) {
        String sql = "INSERT INTO forum_comment (author_id, topic_id, parent_id, content, is_teacher_response, is_accepted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            statement.setInt(1, comment.getUser().getId());
            statement.setInt(2, comment.getForumTopic().getId());
            setNullableInteger(statement, 3, comment.getForumComment() == null ? null : comment.getForumComment().getId());
            statement.setString(4, comment.getContent());
            statement.setBoolean(5, comment.isIsTeacherResponse());
            statement.setBoolean(6, comment.isIsAccepted());
            statement.setTimestamp(7, comment.getCreatedAt() != null ? comment.getCreatedAt() : now);
            statement.setTimestamp(8, comment.getUpdatedAt() != null ? comment.getUpdatedAt() : now);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    comment.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumComment comment) {
        String sql = "UPDATE forum_comment SET content = ?, is_teacher_response = ?, is_accepted = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, comment.getContent());
            statement.setBoolean(2, comment.isIsTeacherResponse());
            statement.setBoolean(3, comment.isIsAccepted());
            statement.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            statement.setInt(5, comment.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ForumComment comment) {
        String sql = "DELETE FROM forum_comment WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, comment.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ForumComment> getALL() {
        String sql = "SELECT id, author_id, topic_id, parent_id, content, is_teacher_response, is_accepted, created_at, updated_at FROM forum_comment ORDER BY created_at ASC";
        return executeQuery(sql);
    }

    public ForumComment getById(int id) {
        String sql = "SELECT id, author_id, topic_id, parent_id, content, is_teacher_response, is_accepted, created_at, updated_at FROM forum_comment WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public List<ForumComment> findTopLevelByTopic(int topicId) {
        String sql = "SELECT id, author_id, topic_id, parent_id, content, is_teacher_response, is_accepted, created_at, updated_at FROM forum_comment WHERE topic_id = ? AND parent_id IS NULL ORDER BY created_at ASC";
        List<ForumComment> comments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topicId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) comments.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return comments;
    }

    public List<ForumComment> findReplies(int parentId) {
        String sql = "SELECT id, author_id, topic_id, parent_id, content, is_teacher_response, is_accepted, created_at, updated_at FROM forum_comment WHERE parent_id = ? ORDER BY created_at ASC";
        List<ForumComment> replies = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, parentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) replies.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return replies;
    }

    public int countByTopic(int topicId) {
        String sql = "SELECT COUNT(*) FROM forum_comment WHERE topic_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topicId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    public void toggleAccepted(int commentId, boolean accepted) {
        String sql = "UPDATE forum_comment SET is_accepted = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, accepted);
            statement.setInt(2, commentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public boolean hasAcceptedAnswer(int topicId) {
        String sql = "SELECT COUNT(*) FROM forum_comment WHERE topic_id = ? AND is_accepted = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topicId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    public String getAuthorName(int userId) {
        String sql = "SELECT COALESCE(name, email) AS display_name FROM user WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getString("display_name");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return "Unknown";
    }

    private List<ForumComment> executeQuery(String sql) {
        List<ForumComment> comments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) comments.add(mapRow(rs));
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return comments;
    }

    private ForumComment mapRow(ResultSet rs) throws SQLException {
        ForumComment comment = new ForumComment();
        comment.setId(rs.getInt("id"));
        comment.setUser(mapUserReference(rs.getInt("author_id")));
        comment.setForumTopic(mapForumTopicReference(rs.getInt("topic_id")));
        comment.setForumComment(mapForumCommentReference(getNullableInteger(rs, "parent_id")));
        comment.setContent(rs.getString("content"));
        comment.setIsTeacherResponse(rs.getBoolean("is_teacher_response"));
        comment.setIsAccepted(rs.getBoolean("is_accepted"));
        comment.setCreatedAt(rs.getTimestamp("created_at"));
        comment.setUpdatedAt(rs.getTimestamp("updated_at"));
        return comment;
    }
}
