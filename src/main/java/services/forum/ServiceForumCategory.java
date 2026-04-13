package services.forum;

import entities.forum.ForumCategory;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumCategory extends ServiceSupport implements IService<ForumCategory> {

    @Override
    public void add(ForumCategory category) {
        String sql = "INSERT INTO forum_category (name, description, icon, position, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            setNullableString(statement, 2, category.getDescription());
            setNullableString(statement, 3, category.getIcon());
            statement.setInt(4, category.getPosition());
            statement.setByte(5, category.getIsActive());
            statement.setTimestamp(6, category.getCreatedAt() != null ? category.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumCategory category) {
        String sql = "UPDATE forum_category SET name = ?, description = ?, icon = ?, position = ?, is_active = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getName());
            setNullableString(statement, 2, category.getDescription());
            setNullableString(statement, 3, category.getIcon());
            statement.setInt(4, category.getPosition());
            statement.setByte(5, category.getIsActive());
            statement.setInt(6, category.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ForumCategory category) {
        String sql = "DELETE FROM forum_category WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, category.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ForumCategory> getALL() {
        String sql = "SELECT id, name, description, icon, position, is_active, created_at FROM forum_category ORDER BY position ASC";
        return executeQuery(sql);
    }

    public List<ForumCategory> findAllActive() {
        String sql = "SELECT id, name, description, icon, position, is_active, created_at FROM forum_category WHERE is_active = 1 ORDER BY position ASC";
        return executeQuery(sql);
    }

    public ForumCategory getById(int id) {
        String sql = "SELECT id, name, description, icon, position, is_active, created_at FROM forum_category WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
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

    public int getTopicsCount(int categoryId) {
        String sql = "SELECT COUNT(*) FROM forum_topic WHERE category_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    public int getCommentsCount(int categoryId) {
        String sql = "SELECT COUNT(*) FROM forum_comment fc JOIN forum_topic ft ON fc.topic_id = ft.id WHERE ft.category_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    private List<ForumCategory> executeQuery(String sql) {
        List<ForumCategory> categories = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(mapRow(resultSet));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return categories;
    }

    private ForumCategory mapRow(ResultSet resultSet) throws SQLException {
        ForumCategory category = new ForumCategory();
        category.setId(resultSet.getInt("id"));
        category.setName(resultSet.getString("name"));
        category.setDescription(resultSet.getString("description"));
        category.setIcon(resultSet.getString("icon"));
        category.setPosition(resultSet.getInt("position"));
        category.setIsActive(resultSet.getByte("is_active"));
        category.setCreatedAt(resultSet.getTimestamp("created_at"));
        return category;
    }
}
