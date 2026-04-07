package services;

import entities.Grade;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceGrade extends ServiceSupport implements IService<Grade> {

    @Override
    public void add(Grade grade) {
        String sql = "INSERT INTO grade (id, assessment_id, student_id, teacher_id, score, comment, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, grade.getId());
            statement.setInt(2, grade.getAssessment().getId());
            statement.setInt(3, grade.getUserByStudentId().getId());
            statement.setInt(4, grade.getUserByTeacherId().getId());
            statement.setDouble(5, grade.getScore());
            setNullableString(statement, 6, grade.getComment());
            statement.setTimestamp(7, grade.getCreatedAt());
            statement.setTimestamp(8, grade.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Grade grade) {
        String sql = "UPDATE grade SET assessment_id = ?, student_id = ?, teacher_id = ?, score = ?, comment = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, grade.getAssessment().getId());
            statement.setInt(2, grade.getUserByStudentId().getId());
            statement.setInt(3, grade.getUserByTeacherId().getId());
            statement.setDouble(4, grade.getScore());
            setNullableString(statement, 5, grade.getComment());
            statement.setTimestamp(6, grade.getCreatedAt());
            statement.setTimestamp(7, grade.getUpdatedAt());
            statement.setInt(8, grade.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Grade grade) {
        String sql = "DELETE FROM grade WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, grade.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Grade> getALL() {
        String sql = "SELECT id, assessment_id, student_id, teacher_id, score, comment, created_at, updated_at FROM grade";
        List<Grade> grades = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Grade grade = new Grade();
                grade.setId(resultSet.getInt("id"));
                grade.setAssessment(mapAssessmentReference(resultSet.getInt("assessment_id")));
                grade.setUserByStudentId(mapUserReference(resultSet.getInt("student_id")));
                grade.setUserByTeacherId(mapUserReference(resultSet.getInt("teacher_id")));
                grade.setScore(resultSet.getDouble("score"));
                grade.setComment(resultSet.getString("comment"));
                grade.setCreatedAt(resultSet.getTimestamp("created_at"));
                grade.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                grades.add(grade);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return grades;
    }
}
