package services;

import entities.ClassMeeting;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceClassMeeting extends ServiceSupport implements IService<ClassMeeting> {

    @Override
    public void add(ClassMeeting meeting) {
        String sql = "INSERT INTO class_meeting (id, teacher_classe_id, title, description, room_code, status, scheduled_at, started_at, ended_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, meeting.getId());
            statement.setInt(2, meeting.getTeacherClasse().getId());
            statement.setString(3, meeting.getTitle());
            setNullableString(statement, 4, meeting.getDescription());
            statement.setString(5, meeting.getRoomCode());
            statement.setString(6, meeting.getStatus());
            setNullableTimestamp(statement, 7, meeting.getScheduledAt());
            setNullableTimestamp(statement, 8, meeting.getStartedAt());
            setNullableTimestamp(statement, 9, meeting.getEndedAt());
            statement.setTimestamp(10, meeting.getCreatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ClassMeeting meeting) {
        String sql = "UPDATE class_meeting SET teacher_classe_id = ?, title = ?, description = ?, room_code = ?, status = ?, scheduled_at = ?, started_at = ?, ended_at = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, meeting.getTeacherClasse().getId());
            statement.setString(2, meeting.getTitle());
            setNullableString(statement, 3, meeting.getDescription());
            statement.setString(4, meeting.getRoomCode());
            statement.setString(5, meeting.getStatus());
            setNullableTimestamp(statement, 6, meeting.getScheduledAt());
            setNullableTimestamp(statement, 7, meeting.getStartedAt());
            setNullableTimestamp(statement, 8, meeting.getEndedAt());
            statement.setTimestamp(9, meeting.getCreatedAt());
            statement.setInt(10, meeting.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ClassMeeting meeting) {
        String sql = "DELETE FROM class_meeting WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, meeting.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ClassMeeting> getALL() {
        String sql = "SELECT id, teacher_classe_id, title, description, room_code, status, scheduled_at, started_at, ended_at, created_at FROM class_meeting";
        List<ClassMeeting> meetings = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ClassMeeting meeting = new ClassMeeting();
                meeting.setId(resultSet.getInt("id"));
                meeting.setTeacherClasse(mapTeacherClasseReference(resultSet.getInt("teacher_classe_id")));
                meeting.setTitle(resultSet.getString("title"));
                meeting.setDescription(resultSet.getString("description"));
                meeting.setRoomCode(resultSet.getString("room_code"));
                meeting.setStatus(resultSet.getString("status"));
                meeting.setScheduledAt(resultSet.getTimestamp("scheduled_at"));
                meeting.setStartedAt(resultSet.getTimestamp("started_at"));
                meeting.setEndedAt(resultSet.getTimestamp("ended_at"));
                meeting.setCreatedAt(resultSet.getTimestamp("created_at"));
                meetings.add(meeting);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return meetings;
    }
}
