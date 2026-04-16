package services.job_offer;

import entities.job_offer.GeneralChatMessage;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceGeneralChatMessage extends ServiceSupport implements IService<GeneralChatMessage> {

    @Override
    public void add(GeneralChatMessage message) {
        String sql = "INSERT INTO general_chat_message (id, sender_id, type, content, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, message.getId());
            setNullableInteger(statement, 2, message.getUser() == null ? null : message.getUser().getId());
            statement.setString(3, message.getType());
            statement.setString(4, message.getContent());
            statement.setTimestamp(5, message.getCreatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(GeneralChatMessage message) {
        String sql = "UPDATE general_chat_message SET sender_id = ?, type = ?, content = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, message.getUser() == null ? null : message.getUser().getId());
            statement.setString(2, message.getType());
            statement.setString(3, message.getContent());
            statement.setTimestamp(4, message.getCreatedAt());
            statement.setInt(5, message.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(GeneralChatMessage message) {
        String sql = "DELETE FROM general_chat_message WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, message.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<GeneralChatMessage> getALL() {
        String sql = "SELECT id, sender_id, type, content, created_at FROM general_chat_message";
        List<GeneralChatMessage> messages = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                GeneralChatMessage message = new GeneralChatMessage();
                message.setId(resultSet.getInt("id"));
                message.setUser(mapUserReference(getNullableInteger(resultSet, "sender_id")));
                message.setType(resultSet.getString("type"));
                message.setContent(resultSet.getString("content"));
                message.setCreatedAt(resultSet.getTimestamp("created_at"));
                messages.add(message);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return messages;
    }
}
