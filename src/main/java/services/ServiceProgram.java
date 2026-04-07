package services;

import entities.Program;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceProgram extends ServiceSupport implements IService<Program> {

    @Override
    public void add(Program program) {
        String sql = "INSERT INTO program (name, published, created_at, updated_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, program.getName());
            statement.setByte(2, program.getPublished());
            statement.setTimestamp(3, program.getCreatedAt());
            statement.setTimestamp(4, program.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Program program) {
        String sql = "UPDATE program SET name = ?, published = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, program.getName());
            statement.setByte(2, program.getPublished());
            statement.setTimestamp(3, program.getCreatedAt());
            statement.setTimestamp(4, program.getUpdatedAt());
            statement.setInt(5, program.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Program program) {
        String sql = "DELETE FROM program WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, program.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Program> getALL() {
        String sql = "SELECT id, name, published, created_at, updated_at FROM program";
        List<Program> programs = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Program program = new Program();
                program.setId(resultSet.getInt("id"));
                program.setName(resultSet.getString("name"));
                program.setPublished(resultSet.getByte("published"));
                program.setCreatedAt(resultSet.getTimestamp("created_at"));
                program.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                programs.add(program);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return programs;
    }
}
