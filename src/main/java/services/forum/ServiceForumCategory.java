package services.forum;

import entities.forum.ForumCategory;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumCategory extends ServiceSupport implements IService<ForumCategory> {

    @Override
    public void add(ForumCategory category) {
        String sql = "INSERT INTO forum_category (name, description, icon, position, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getName());
            setNullableString(statement, 2, category.getDescription());
            setNullableString(statement, 3, category.getIcon());
            statement.setInt(4, category.getPosition());
            statement.setByte(5, category.getIsActive());
            statement.setTimestamp(6, category.getCreatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumCategory category) {
        String sql = "UPDATE forum_category SET name = ?, description = ?, icon = ?, position = ?, is_active = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getName());
            setNullableString(statement, 2, category.getDescription());
            setNullableString(statement, 3, category.getIcon());
            statement.setInt(4, category.getPosition());
            statement.setByte(5, category.getIsActive());
            statement.setTimestamp(6, category.getCreatedAt());
            statement.setInt(7, category.getId());
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
        String sql = "SELECT id, name, description, icon, position, is_active, created_at FROM forum_category";
        List<ForumCategory> categories = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ForumCategory category = new ForumCategory();
                category.setId(resultSet.getInt("id"));
                category.setName(resultSet.getString("name"));
                category.setDescription(resultSet.getString("description"));
                category.setIcon(resultSet.getString("icon"));
                category.setPosition(resultSet.getInt("position"));
                category.setIsActive(resultSet.getByte("is_active"));
                category.setCreatedAt(resultSet.getTimestamp("created_at"));
                categories.add(category);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return categories;
    }
}
