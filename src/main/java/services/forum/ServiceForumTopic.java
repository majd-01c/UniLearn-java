package services.forum;

import entities.forum.ForumTopic;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumTopic extends ServiceSupport implements IService<ForumTopic> {

    @Override
    public void add(ForumTopic topic) {
        String sql = "INSERT INTO forum_topic (author_id, category_id, title, content, status, is_pinned, view_count, created_at, updated_at, last_activity_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            statement.setInt(1, topic.getUser().getId());
            statement.setInt(2, topic.getForumCategory().getId());
            statement.setString(3, topic.getTitle());
            statement.setString(4, topic.getContent());
            statement.setString(5, topic.getStatus() != null ? topic.getStatus() : "open");
            statement.setByte(6, topic.getIsPinned());
            statement.setInt(7, topic.getViewCount());
            statement.setTimestamp(8, topic.getCreatedAt() != null ? topic.getCreatedAt() : now);
            statement.setTimestamp(9, topic.getUpdatedAt() != null ? topic.getUpdatedAt() : now);
            setNullableTimestamp(statement, 10, topic.getLastActivityAt() != null ? topic.getLastActivityAt() : now);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    topic.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumTopic topic) {
        String sql = "UPDATE forum_topic SET author_id = ?, category_id = ?, title = ?, content = ?, status = ?, is_pinned = ?, view_count = ?, updated_at = ?, last_activity_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topic.getUser().getId());
            statement.setInt(2, topic.getForumCategory().getId());
            statement.setString(3, topic.getTitle());
            statement.setString(4, topic.getContent());
            statement.setString(5, topic.getStatus());
            statement.setByte(6, topic.getIsPinned());
            statement.setInt(7, topic.getViewCount());
            statement.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            setNullableTimestamp(statement, 9, topic.getLastActivityAt());
            statement.setInt(10, topic.getId());
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
        String sql = "SELECT ft.id, ft.author_id, ft.category_id, ft.title, ft.content, ft.status, ft.is_pinned, ft.view_count, ft.created_at, ft.updated_at, ft.last_activity_at FROM forum_topic ft ORDER BY ft.is_pinned DESC, ft.last_activity_at DESC";
        return executeQuery(sql);
    }

    public ForumTopic getById(int id) {
        String sql = "SELECT id, author_id, category_id, title, content, status, is_pinned, view_count, created_at, updated_at, last_activity_at FROM forum_topic WHERE id = ?";
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

    public List<ForumTopic> findByCategory(int categoryId, String search, int page, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, author_id, category_id, title, content, status, is_pinned, view_count, created_at, updated_at, last_activity_at FROM forum_topic WHERE category_id = ?");

        if (search != null && !search.isBlank()) {
            sql.append(" AND (title LIKE ? OR content LIKE ?)");
        }
        sql.append(" ORDER BY is_pinned DESC, last_activity_at DESC LIMIT ? OFFSET ?");

        List<ForumTopic> topics = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            statement.setInt(idx++, categoryId);
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search + "%";
                statement.setString(idx++, pattern);
                statement.setString(idx++, pattern);
            }
            statement.setInt(idx++, limit);
            statement.setInt(idx, (page - 1) * limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    topics.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return topics;
    }

    public int countByCategory(int categoryId, String search) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM forum_topic WHERE category_id = ?");
        if (search != null && !search.isBlank()) {
            sql.append(" AND (title LIKE ? OR content LIKE ?)");
        }
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            statement.setInt(idx++, categoryId);
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search + "%";
                statement.setString(idx++, pattern);
                statement.setString(idx, pattern);
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    public List<ForumTopic> findRecent(int limit) {
        String sql = "SELECT ft.id, ft.author_id, ft.category_id, ft.title, ft.content, ft.status, ft.is_pinned, ft.view_count, ft.created_at, ft.updated_at, ft.last_activity_at " +
                "FROM forum_topic ft JOIN forum_category fc ON ft.category_id = fc.id WHERE fc.is_active = 1 ORDER BY ft.created_at DESC LIMIT ?";
        List<ForumTopic> topics = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) topics.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return topics;
    }

    public List<ForumTopic> findUnanswered(int limit) {
        String sql = "SELECT ft.id, ft.author_id, ft.category_id, ft.title, ft.content, ft.status, ft.is_pinned, ft.view_count, ft.created_at, ft.updated_at, ft.last_activity_at " +
                "FROM forum_topic ft JOIN forum_category fc ON ft.category_id = fc.id " +
                "LEFT JOIN forum_comment fcom ON ft.id = fcom.topic_id " +
                "WHERE fc.is_active = 1 AND ft.status = 'open' " +
                "GROUP BY ft.id HAVING COUNT(fcom.id) = 0 ORDER BY ft.created_at DESC LIMIT ?";
        List<ForumTopic> topics = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) topics.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return topics;
    }

    public void incrementViewCount(int topicId) {
        String sql = "UPDATE forum_topic SET view_count = view_count + 1 WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, topicId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
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

    public String getCategoryName(int categoryId) {
        String sql = "SELECT name FROM forum_category WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return "";
    }

    public int getCommentCount(int topicId) {
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

    private List<ForumTopic> executeQuery(String sql) {
        List<ForumTopic> topics = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                topics.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return topics;
    }

    private ForumTopic mapRow(ResultSet rs) throws SQLException {
        ForumTopic topic = new ForumTopic();
        topic.setId(rs.getInt("id"));
        topic.setUser(mapUserReference(rs.getInt("author_id")));
        topic.setForumCategory(mapForumCategoryReference(rs.getInt("category_id")));
        topic.setTitle(rs.getString("title"));
        topic.setContent(rs.getString("content"));
        topic.setStatus(rs.getString("status"));
        topic.setIsPinned(rs.getByte("is_pinned"));
        topic.setViewCount(rs.getInt("view_count"));
        topic.setCreatedAt(rs.getTimestamp("created_at"));
        topic.setUpdatedAt(rs.getTimestamp("updated_at"));
        topic.setLastActivityAt(rs.getTimestamp("last_activity_at"));
        return topic;
    }
}
