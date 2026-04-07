package services;

import entities.Module;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceModule extends ServiceSupport implements IService<Module> {

    @Override
    public void add(Module module) {
        String sql = "INSERT INTO module (name, period_unit, duration, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, module.getName());
            statement.setString(2, module.getPeriodUnit());
            statement.setInt(3, module.getDuration());
            statement.setTimestamp(4, module.getCreatedAt());
            statement.setTimestamp(5, module.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Module module) {
        String sql = "UPDATE module SET name = ?, period_unit = ?, duration = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, module.getName());
            statement.setString(2, module.getPeriodUnit());
            statement.setInt(3, module.getDuration());
            statement.setTimestamp(4, module.getCreatedAt());
            statement.setTimestamp(5, module.getUpdatedAt());
            statement.setInt(6, module.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Module module) {
        String sql = "DELETE FROM module WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, module.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Module> getALL() {
        String sql = "SELECT id, name, period_unit, duration, created_at, updated_at FROM module";
        List<Module> modules = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Module module = new Module();
                module.setId(resultSet.getInt("id"));
                module.setName(resultSet.getString("name"));
                module.setPeriodUnit(resultSet.getString("period_unit"));
                module.setDuration(resultSet.getInt("duration"));
                module.setCreatedAt(resultSet.getTimestamp("created_at"));
                module.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                modules.add(module);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return modules;
    }
}
