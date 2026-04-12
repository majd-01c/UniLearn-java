package services;

import entities.Event;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceEvent extends ServiceSupport implements IService<Event> {

    @Override
    public void add(Event event) {
        String sql = "INSERT INTO event (id, created_by_id, title, type, description, location, start_at, capacity, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, event.getId());
            statement.setInt(2, event.getUser().getId());
            statement.setString(3, event.getTitle());
            statement.setString(4, event.getType());
            setNullableString(statement, 5, event.getDescription());
            setNullableString(statement, 6, event.getLocation());
            statement.setTimestamp(7, event.getStartAt());
            setNullableInteger(statement, 8, event.getCapacity());
            statement.setString(9, event.getStatus());
            statement.setTimestamp(10, event.getCreatedAt());
            statement.setTimestamp(11, event.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Event event) {
        String sql = "UPDATE event SET created_by_id = ?, title = ?, type = ?, description = ?, location = ?, start_at = ?, capacity = ?, status = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, event.getUser().getId());
            statement.setString(2, event.getTitle());
            statement.setString(3, event.getType());
            setNullableString(statement, 4, event.getDescription());
            setNullableString(statement, 5, event.getLocation());
            statement.setTimestamp(6, event.getStartAt());
            setNullableInteger(statement, 7, event.getCapacity());
            statement.setString(8, event.getStatus());
            statement.setTimestamp(9, event.getCreatedAt());
            statement.setTimestamp(10, event.getUpdatedAt());
            statement.setInt(11, event.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Event event) {
        String sql = "DELETE FROM event WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, event.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Event> getALL() {
        String sql = "SELECT id, created_by_id, title, type, description, location, start_at, capacity, status, created_at, updated_at FROM event";
        List<Event> events = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Event event = new Event();
                event.setId(resultSet.getInt("id"));
                event.setUser(mapUserReference(resultSet.getInt("created_by_id")));
                event.setTitle(resultSet.getString("title"));
                event.setType(resultSet.getString("type"));
                event.setDescription(resultSet.getString("description"));
                event.setLocation(resultSet.getString("location"));
                event.setStartAt(resultSet.getTimestamp("start_at"));
                event.setCapacity(getNullableInteger(resultSet, "capacity"));
                event.setStatus(resultSet.getString("status"));
                event.setCreatedAt(resultSet.getTimestamp("created_at"));
                event.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                events.add(event);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return events;
    }
}
