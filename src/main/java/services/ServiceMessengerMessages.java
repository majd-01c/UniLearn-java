package services;

import entities.MessengerMessages;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceMessengerMessages extends ServiceSupport implements IService<MessengerMessages> {

    @Override
    public void add(MessengerMessages message) {
        String sql = "INSERT INTO messenger_messages (id, body, headers, queue_name, created_at, available_at, delivered_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, message.getId());
            statement.setString(2, message.getBody());
            statement.setString(3, message.getHeaders());
            statement.setString(4, message.getQueueName());
            statement.setTimestamp(5, message.getCreatedAt());
            statement.setTimestamp(6, message.getAvailableAt());
            setNullableTimestamp(statement, 7, message.getDeliveredAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(MessengerMessages message) {
        String sql = "UPDATE messenger_messages SET body = ?, headers = ?, queue_name = ?, created_at = ?, available_at = ?, delivered_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, message.getBody());
            statement.setString(2, message.getHeaders());
            statement.setString(3, message.getQueueName());
            statement.setTimestamp(4, message.getCreatedAt());
            statement.setTimestamp(5, message.getAvailableAt());
            setNullableTimestamp(statement, 6, message.getDeliveredAt());
            statement.setLong(7, message.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(MessengerMessages message) {
        String sql = "DELETE FROM messenger_messages WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, message.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<MessengerMessages> getALL() {
        String sql = "SELECT id, body, headers, queue_name, created_at, available_at, delivered_at FROM messenger_messages";
        List<MessengerMessages> messages = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                MessengerMessages message = new MessengerMessages();
                message.setId(resultSet.getLong("id"));
                message.setBody(resultSet.getString("body"));
                message.setHeaders(resultSet.getString("headers"));
                message.setQueueName(resultSet.getString("queue_name"));
                message.setCreatedAt(resultSet.getTimestamp("created_at"));
                message.setAvailableAt(resultSet.getTimestamp("available_at"));
                message.setDeliveredAt(resultSet.getTimestamp("delivered_at"));
                messages.add(message);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return messages;
    }
}
