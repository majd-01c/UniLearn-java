package services;

import entities.CourseDocument;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceCourseDocument extends ServiceSupport implements IService<CourseDocument> {

    @Override
    public void add(CourseDocument document) {
        String sql = "INSERT INTO course_document (id, uploaded_by_id, classe_id, title, description, file_name, original_file_name, file_size, created_at, updated_at, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, document.getId());
            statement.setInt(2, document.getUser().getId());
            statement.setInt(3, document.getClasse().getId());
            statement.setString(4, document.getTitle());
            setNullableString(statement, 5, document.getDescription());
            setNullableString(statement, 6, document.getFileName());
            setNullableString(statement, 7, document.getOriginalFileName());
            setNullableInteger(statement, 8, document.getFileSize());
            statement.setTimestamp(9, document.getCreatedAt());
            statement.setTimestamp(10, document.getUpdatedAt());
            statement.setByte(11, document.getIsActive());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(CourseDocument document) {
        String sql = "UPDATE course_document SET uploaded_by_id = ?, classe_id = ?, title = ?, description = ?, file_name = ?, original_file_name = ?, file_size = ?, created_at = ?, updated_at = ?, is_active = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, document.getUser().getId());
            statement.setInt(2, document.getClasse().getId());
            statement.setString(3, document.getTitle());
            setNullableString(statement, 4, document.getDescription());
            setNullableString(statement, 5, document.getFileName());
            setNullableString(statement, 6, document.getOriginalFileName());
            setNullableInteger(statement, 7, document.getFileSize());
            statement.setTimestamp(8, document.getCreatedAt());
            statement.setTimestamp(9, document.getUpdatedAt());
            statement.setByte(10, document.getIsActive());
            statement.setInt(11, document.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(CourseDocument document) {
        String sql = "DELETE FROM course_document WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, document.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<CourseDocument> getALL() {
        String sql = "SELECT id, uploaded_by_id, classe_id, title, description, file_name, original_file_name, file_size, created_at, updated_at, is_active FROM course_document";
        List<CourseDocument> documents = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CourseDocument document = new CourseDocument();
                document.setId(resultSet.getInt("id"));
                document.setUser(mapUserReference(resultSet.getInt("uploaded_by_id")));
                document.setClasse(mapClasseReference(resultSet.getInt("classe_id")));
                document.setTitle(resultSet.getString("title"));
                document.setDescription(resultSet.getString("description"));
                document.setFileName(resultSet.getString("file_name"));
                document.setOriginalFileName(resultSet.getString("original_file_name"));
                document.setFileSize(getNullableInteger(resultSet, "file_size"));
                document.setCreatedAt(resultSet.getTimestamp("created_at"));
                document.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                document.setIsActive(resultSet.getByte("is_active"));
                documents.add(document);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return documents;
    }
}
