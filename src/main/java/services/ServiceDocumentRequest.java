package services;

import entities.DocumentRequest;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceDocumentRequest extends ServiceSupport implements IService<DocumentRequest> {

    @Override
    public void add(DocumentRequest request) {
        String sql = "INSERT INTO document_request (student_id, document_type, additional_info, status, requested_at, delivered_at, document_path) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, request.getUser().getId());
            statement.setString(2, request.getDocumentType());
            setNullableString(statement, 3, request.getAdditionalInfo());
            statement.setString(4, request.getStatus());
            statement.setTimestamp(5, request.getRequestedAt());
            setNullableTimestamp(statement, 6, request.getDeliveredAt());
            setNullableString(statement, 7, request.getDocumentPath());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(DocumentRequest request) {
        String sql = "UPDATE document_request SET student_id = ?, document_type = ?, additional_info = ?, status = ?, requested_at = ?, delivered_at = ?, document_path = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, request.getUser().getId());
            statement.setString(2, request.getDocumentType());
            setNullableString(statement, 3, request.getAdditionalInfo());
            statement.setString(4, request.getStatus());
            statement.setTimestamp(5, request.getRequestedAt());
            setNullableTimestamp(statement, 6, request.getDeliveredAt());
            setNullableString(statement, 7, request.getDocumentPath());
            statement.setInt(8, request.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(DocumentRequest request) {
        String sql = "DELETE FROM document_request WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, request.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<DocumentRequest> getALL() {
        String sql = "SELECT id, student_id, document_type, additional_info, status, requested_at, delivered_at, document_path FROM document_request";
        List<DocumentRequest> requests = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                DocumentRequest request = new DocumentRequest();
                request.setId(resultSet.getInt("id"));
                request.setUser(mapUserReference(resultSet.getInt("student_id")));
                request.setDocumentType(resultSet.getString("document_type"));
                request.setAdditionalInfo(resultSet.getString("additional_info"));
                request.setStatus(resultSet.getString("status"));
                request.setRequestedAt(resultSet.getTimestamp("requested_at"));
                request.setDeliveredAt(resultSet.getTimestamp("delivered_at"));
                request.setDocumentPath(resultSet.getString("document_path"));
                requests.add(request);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return requests;
    }
}
