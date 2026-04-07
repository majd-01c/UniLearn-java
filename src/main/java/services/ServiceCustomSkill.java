package services;

import entities.CustomSkill;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceCustomSkill extends ServiceSupport implements IService<CustomSkill> {

    @Override
    public void add(CustomSkill skill) {
        String sql = "INSERT INTO custom_skill (id, partner_id, name, category, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, skill.getId());
            statement.setInt(2, skill.getUser().getId());
            statement.setString(3, skill.getName());
            setNullableString(statement, 4, skill.getCategory());
            setNullableString(statement, 5, skill.getDescription());
            statement.setTimestamp(6, skill.getCreatedAt());
            statement.setTimestamp(7, skill.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(CustomSkill skill) {
        String sql = "UPDATE custom_skill SET partner_id = ?, name = ?, category = ?, description = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, skill.getUser().getId());
            statement.setString(2, skill.getName());
            setNullableString(statement, 3, skill.getCategory());
            setNullableString(statement, 4, skill.getDescription());
            statement.setTimestamp(5, skill.getCreatedAt());
            statement.setTimestamp(6, skill.getUpdatedAt());
            statement.setInt(7, skill.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(CustomSkill skill) {
        String sql = "DELETE FROM custom_skill WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, skill.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<CustomSkill> getALL() {
        String sql = "SELECT id, partner_id, name, category, description, created_at, updated_at FROM custom_skill";
        List<CustomSkill> skills = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CustomSkill skill = new CustomSkill();
                skill.setId(resultSet.getInt("id"));
                skill.setUser(mapUserReference(resultSet.getInt("partner_id")));
                skill.setName(resultSet.getString("name"));
                skill.setCategory(resultSet.getString("category"));
                skill.setDescription(resultSet.getString("description"));
                skill.setCreatedAt(resultSet.getTimestamp("created_at"));
                skill.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                skills.add(skill);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return skills;
    }
}
