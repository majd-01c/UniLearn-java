package services;

import entities.Reclamation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceReclamation extends ServiceSupport implements IService<Reclamation> {

    @Override
    public void add(Reclamation reclamation) {
        String sql = "INSERT INTO reclamation (student_id, related_course_id, subject, description, status, admin_response, created_at, resolved_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reclamation.getUser().getId());
            setNullableInteger(statement, 2, reclamation.getCourse() == null ? null : reclamation.getCourse().getId());
            statement.setString(3, reclamation.getSubject());
            statement.setString(4, reclamation.getDescription());
            statement.setString(5, reclamation.getStatus());
            setNullableString(statement, 6, reclamation.getAdminResponse());
            statement.setTimestamp(7, reclamation.getCreatedAt());
            setNullableTimestamp(statement, 8, reclamation.getResolvedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Reclamation reclamation) {
        String sql = "UPDATE reclamation SET student_id = ?, related_course_id = ?, subject = ?, description = ?, status = ?, admin_response = ?, created_at = ?, resolved_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reclamation.getUser().getId());
            setNullableInteger(statement, 2, reclamation.getCourse() == null ? null : reclamation.getCourse().getId());
            statement.setString(3, reclamation.getSubject());
            statement.setString(4, reclamation.getDescription());
            statement.setString(5, reclamation.getStatus());
            setNullableString(statement, 6, reclamation.getAdminResponse());
            statement.setTimestamp(7, reclamation.getCreatedAt());
            setNullableTimestamp(statement, 8, reclamation.getResolvedAt());
            statement.setInt(9, reclamation.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Reclamation reclamation) {
        String sql = "DELETE FROM reclamation WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reclamation.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Reclamation> getALL() {
        String sql = "SELECT id, student_id, related_course_id, subject, description, status, admin_response, created_at, resolved_at FROM reclamation";
        List<Reclamation> reclamations = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Reclamation reclamation = new Reclamation();
                reclamation.setId(resultSet.getInt("id"));
                reclamation.setUser(mapUserReference(resultSet.getInt("student_id")));
                reclamation.setCourse(mapCourseReference(getNullableInteger(resultSet, "related_course_id")));
                reclamation.setSubject(resultSet.getString("subject"));
                reclamation.setDescription(resultSet.getString("description"));
                reclamation.setStatus(resultSet.getString("status"));
                reclamation.setAdminResponse(resultSet.getString("admin_response"));
                reclamation.setCreatedAt(resultSet.getTimestamp("created_at"));
                reclamation.setResolvedAt(resultSet.getTimestamp("resolved_at"));
                reclamations.add(reclamation);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return reclamations;
    }
}
