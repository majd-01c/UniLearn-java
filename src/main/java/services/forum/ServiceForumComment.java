package services.forum;

import entities.forum.ForumComment;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumComment extends ServiceSupport implements IService<ForumComment> {

    @Override
    public void add(ForumComment comment) {
        String sql = "INSERT INTO forum_comment (author_id, topic_id, parent_id, content, is_teacher_response, is_accepted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, comment.getUser().getId());
            statement.setInt(2, comment.getForumTopic().getId());
            setNullableInteger(statement, 3, comment.getForumComment() == null ? null : comment.getForumComment().getId());
            statement.setString(4, comment.getContent());
            statement.setBoolean(5, comment.isIsTeacherResponse());
            statement.setBoolean(6, comment.isIsAccepted());
            statement.setTimestamp(7, comment.getCreatedAt());
            statement.setTimestamp(8, comment.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumComment comment) {
        String sql = "UPDATE forum_comment SET author_id = ?, topic_id = ?, parent_id = ?, content = ?, is_teacher_response = ?, is_accepted = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, comment.getUser().getId());
            statement.setInt(2, comment.getForumTopic().getId());
            setNullableInteger(statement, 3, comment.getForumComment() == null ? null : comment.getForumComment().getId());
            statement.setString(4, comment.getContent());
            statement.setBoolean(5, comment.isIsTeacherResponse());
            statement.setBoolean(6, comment.isIsAccepted());
            statement.setTimestamp(7, comment.getCreatedAt());
            statement.setTimestamp(8, comment.getUpdatedAt());
            statement.setInt(9, comment.getId());
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
        String sql = "SELECT id, author_id, topic_id, parent_id, content, is_teacher_response, is_accepted, created_at, updated_at FROM forum_comment";
        List<ForumComment> comments = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ForumComment comment = new ForumComment();
                comment.setId(resultSet.getInt("id"));
                comment.setUser(mapUserReference(resultSet.getInt("author_id")));
                comment.setForumTopic(mapForumTopicReference(resultSet.getInt("topic_id")));
                comment.setForumComment(mapForumCommentReference(getNullableInteger(resultSet, "parent_id")));
                comment.setContent(resultSet.getString("content"));
                comment.setIsTeacherResponse(resultSet.getBoolean("is_teacher_response"));
                comment.setIsAccepted(resultSet.getBoolean("is_accepted"));
                comment.setCreatedAt(resultSet.getTimestamp("created_at"));
                comment.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                comments.add(comment);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return comments;
    }
}
