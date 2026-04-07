package services;

import entities.ProgramChatMessage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceProgramChatMessage extends ServiceSupport implements IService<ProgramChatMessage> {

    @Override
    public void add(ProgramChatMessage message) {
        String sql = "INSERT INTO program_chat_message (id, sender_id, program_id, type, content, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, message.getId());
            setNullableInteger(statement, 2, message.getUser() == null ? null : message.getUser().getId());
            statement.setInt(3, message.getProgram().getId());
            statement.setString(4, message.getType());
            statement.setString(5, message.getContent());
            statement.setTimestamp(6, message.getCreatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ProgramChatMessage message) {
        String sql = "UPDATE program_chat_message SET sender_id = ?, program_id = ?, type = ?, content = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, message.getUser() == null ? null : message.getUser().getId());
            statement.setInt(2, message.getProgram().getId());
            statement.setString(3, message.getType());
            statement.setString(4, message.getContent());
            statement.setTimestamp(5, message.getCreatedAt());
            statement.setInt(6, message.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ProgramChatMessage message) {
        String sql = "DELETE FROM program_chat_message WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, message.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ProgramChatMessage> getALL() {
        String sql = "SELECT id, sender_id, program_id, type, content, created_at FROM program_chat_message";
        List<ProgramChatMessage> messages = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ProgramChatMessage message = new ProgramChatMessage();
                message.setId(resultSet.getInt("id"));
                message.setUser(mapUserReference(getNullableInteger(resultSet, "sender_id")));
                message.setProgram(mapProgramReference(resultSet.getInt("program_id")));
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
