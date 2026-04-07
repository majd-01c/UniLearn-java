package services;

import entities.EventParticipation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceEventParticipation extends ServiceSupport implements IService<EventParticipation> {

    @Override
    public void add(EventParticipation participation) {
        String sql = "INSERT INTO event_participation (id, user_id, event_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, participation.getId());
            statement.setInt(2, participation.getUser().getId());
            statement.setInt(3, participation.getEvent().getId());
            statement.setString(4, participation.getStatus());
            statement.setTimestamp(5, participation.getCreatedAt());
            statement.setTimestamp(6, participation.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(EventParticipation participation) {
        String sql = "UPDATE event_participation SET user_id = ?, event_id = ?, status = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, participation.getUser().getId());
            statement.setInt(2, participation.getEvent().getId());
            statement.setString(3, participation.getStatus());
            statement.setTimestamp(4, participation.getCreatedAt());
            statement.setTimestamp(5, participation.getUpdatedAt());
            statement.setInt(6, participation.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(EventParticipation participation) {
        String sql = "DELETE FROM event_participation WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, participation.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<EventParticipation> getALL() {
        String sql = "SELECT id, user_id, event_id, status, created_at, updated_at FROM event_participation";
        List<EventParticipation> participations = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                EventParticipation participation = new EventParticipation();
                participation.setId(resultSet.getInt("id"));
                participation.setUser(mapUserReference(resultSet.getInt("user_id")));
                participation.setEvent(mapEventReference(resultSet.getInt("event_id")));
                participation.setStatus(resultSet.getString("status"));
                participation.setCreatedAt(resultSet.getTimestamp("created_at"));
                participation.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                participations.add(participation);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return participations;
    }
}
