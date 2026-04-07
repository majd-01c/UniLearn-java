package services;

import entities.Assessment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceAssessment extends ServiceSupport implements IService<Assessment> {

    @Override
    public void add(Assessment assessment) {
        String sql = "INSERT INTO assessment (id, contenu_id, teacher_id, course_id, classe_id, type, title, description, date, created_at, updated_at, max_score) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assessment.getId());
            setNullableInteger(statement, 2, assessment.getContenu() == null ? null : assessment.getContenu().getId());
            statement.setInt(3, assessment.getUser().getId());
            setNullableInteger(statement, 4, assessment.getCourse() == null ? null : assessment.getCourse().getId());
            setNullableInteger(statement, 5, assessment.getClasse() == null ? null : assessment.getClasse().getId());
            statement.setString(6, assessment.getType());
            statement.setString(7, assessment.getTitle());
            setNullableString(statement, 8, assessment.getDescription());
            setNullableTimestamp(statement, 9, assessment.getDate());
            statement.setTimestamp(10, assessment.getCreatedAt());
            statement.setTimestamp(11, assessment.getUpdatedAt());
            statement.setDouble(12, assessment.getMaxScore());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Assessment assessment) {
        String sql = "UPDATE assessment SET contenu_id = ?, teacher_id = ?, course_id = ?, classe_id = ?, type = ?, title = ?, description = ?, date = ?, created_at = ?, updated_at = ?, max_score = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, assessment.getContenu() == null ? null : assessment.getContenu().getId());
            statement.setInt(2, assessment.getUser().getId());
            setNullableInteger(statement, 3, assessment.getCourse() == null ? null : assessment.getCourse().getId());
            setNullableInteger(statement, 4, assessment.getClasse() == null ? null : assessment.getClasse().getId());
            statement.setString(5, assessment.getType());
            statement.setString(6, assessment.getTitle());
            setNullableString(statement, 7, assessment.getDescription());
            setNullableTimestamp(statement, 8, assessment.getDate());
            statement.setTimestamp(9, assessment.getCreatedAt());
            statement.setTimestamp(10, assessment.getUpdatedAt());
            statement.setDouble(11, assessment.getMaxScore());
            statement.setInt(12, assessment.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Assessment assessment) {
        String sql = "DELETE FROM assessment WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, assessment.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Assessment> getALL() {
        String sql = "SELECT id, contenu_id, teacher_id, course_id, classe_id, type, title, description, date, created_at, updated_at, max_score FROM assessment";
        List<Assessment> assessments = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Assessment assessment = new Assessment();
                assessment.setId(resultSet.getInt("id"));
                assessment.setContenu(mapContenuReference(getNullableInteger(resultSet, "contenu_id")));
                assessment.setUser(mapUserReference(resultSet.getInt("teacher_id")));
                assessment.setCourse(mapCourseReference(getNullableInteger(resultSet, "course_id")));
                assessment.setClasse(mapClasseReference(getNullableInteger(resultSet, "classe_id")));
                assessment.setType(resultSet.getString("type"));
                assessment.setTitle(resultSet.getString("title"));
                assessment.setDescription(resultSet.getString("description"));
                assessment.setDate(resultSet.getTimestamp("date"));
                assessment.setCreatedAt(resultSet.getTimestamp("created_at"));
                assessment.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                assessment.setMaxScore(resultSet.getDouble("max_score"));
                assessments.add(assessment);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return assessments;
    }
}
