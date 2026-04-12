package services.forum;

import entities.forum.ForumTopic;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumTopic extends ServiceSupport implements IService<ForumTopic> {

    @Override
    public void add(ForumTopic topic) {
        String sql = "INSERT INTO forum_topic (author_id, category_id, title, content, status, is_pinned, view_count, created_at, updated_at, last_activity_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topic.getUser().getId());
            statement.setInt(2, topic.getForumCategory().getId());
            statement.setString(3, topic.getTitle());
            statement.setString(4, topic.getContent());
            statement.setString(5, topic.getStatus());
            statement.setByte(6, topic.getIsPinned());
            statement.setInt(7, topic.getViewCount());
            statement.setTimestamp(8, topic.getCreatedAt());
            statement.setTimestamp(9, topic.getUpdatedAt());
            setNullableTimestamp(statement, 10, topic.getLastActivityAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumTopic topic) {
        String sql = "UPDATE forum_topic SET author_id = ?, category_id = ?, title = ?, content = ?, status = ?, is_pinned = ?, view_count = ?, created_at = ?, updated_at = ?, last_activity_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topic.getUser().getId());
            statement.setInt(2, topic.getForumCategory().getId());
            statement.setString(3, topic.getTitle());
            statement.setString(4, topic.getContent());
            statement.setString(5, topic.getStatus());
            statement.setByte(6, topic.getIsPinned());
            statement.setInt(7, topic.getViewCount());
            statement.setTimestamp(8, topic.getCreatedAt());
            statement.setTimestamp(9, topic.getUpdatedAt());
            setNullableTimestamp(statement, 10, topic.getLastActivityAt());
            statement.setInt(11, topic.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ForumTopic topic) {
        String sql = "DELETE FROM forum_topic WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topic.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ForumTopic> getALL() {
        String sql = "SELECT id, author_id, category_id, title, content, status, is_pinned, view_count, created_at, updated_at, last_activity_at FROM forum_topic";
        List<ForumTopic> topics = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ForumTopic topic = new ForumTopic();
                topic.setId(resultSet.getInt("id"));
                topic.setUser(mapUserReference(resultSet.getInt("author_id")));
                topic.setForumCategory(mapForumCategoryReference(resultSet.getInt("category_id")));
                topic.setTitle(resultSet.getString("title"));
                topic.setContent(resultSet.getString("content"));
                topic.setStatus(resultSet.getString("status"));
                topic.setIsPinned(resultSet.getByte("is_pinned"));
                topic.setViewCount(resultSet.getInt("view_count"));
                topic.setCreatedAt(resultSet.getTimestamp("created_at"));
                topic.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                topic.setLastActivityAt(resultSet.getTimestamp("last_activity_at"));
                topics.add(topic);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return topics;
    }
}
