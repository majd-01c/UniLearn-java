package services;

import entities.Course;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceCourse extends ServiceSupport implements IService<Course> {

    @Override
    public void add(Course course) {
        String sql = "INSERT INTO course (title, created_at, updated_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.getTitle());
            statement.setTimestamp(2, course.getCreatedAt());
            statement.setTimestamp(3, course.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Course course) {
        String sql = "UPDATE course SET title = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.getTitle());
            statement.setTimestamp(2, course.getCreatedAt());
            statement.setTimestamp(3, course.getUpdatedAt());
            statement.setInt(4, course.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Course course) {
        String sql = "DELETE FROM course WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, course.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Course> getALL() {
        String sql = "SELECT id, title, created_at, updated_at FROM course";
        List<Course> courses = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Course course = new Course();
                course.setId(resultSet.getInt("id"));
                course.setTitle(resultSet.getString("title"));
                course.setCreatedAt(resultSet.getTimestamp("created_at"));
                course.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                courses.add(course);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return courses;
    }
}
