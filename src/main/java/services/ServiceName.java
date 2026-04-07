package services;

import entities.Name;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceName extends ServiceSupport implements IService<Name> {

    @Override
    public void add(Name name) {
        String sql = "INSERT INTO name (id, name) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, name.getId());
            statement.setString(2, name.getName());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Name name) {
        String sql = "UPDATE name SET name = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.getName());
            statement.setInt(2, name.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Name name) {
        String sql = "DELETE FROM name WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, name.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Name> getALL() {
        String sql = "SELECT id, name FROM name";
        List<Name> names = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Name name = new Name();
                name.setId(resultSet.getInt("id"));
                name.setName(resultSet.getString("name"));
                names.add(name);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return names;
    }
}
