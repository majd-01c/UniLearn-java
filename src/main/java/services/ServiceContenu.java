package services;

import entities.Contenu;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceContenu extends ServiceSupport implements IService<Contenu> {

    @Override
    public void add(Contenu contenu) {
        String sql = "INSERT INTO contenu (title, file_name, file_type, type, published, created_at, updated_at, file_size) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, contenu.getTitle());
            setNullableString(statement, 2, contenu.getFileName());
            setNullableString(statement, 3, contenu.getFileType());
            statement.setString(4, contenu.getType());
            statement.setByte(5, contenu.getPublished());
            statement.setTimestamp(6, contenu.getCreatedAt());
            statement.setTimestamp(7, contenu.getUpdatedAt());
            setNullableInteger(statement, 8, contenu.getFileSize());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Contenu contenu) {
        String sql = "UPDATE contenu SET title = ?, file_name = ?, file_type = ?, type = ?, published = ?, created_at = ?, updated_at = ?, file_size = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, contenu.getTitle());
            setNullableString(statement, 2, contenu.getFileName());
            setNullableString(statement, 3, contenu.getFileType());
            statement.setString(4, contenu.getType());
            statement.setByte(5, contenu.getPublished());
            statement.setTimestamp(6, contenu.getCreatedAt());
            statement.setTimestamp(7, contenu.getUpdatedAt());
            setNullableInteger(statement, 8, contenu.getFileSize());
            statement.setInt(9, contenu.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Contenu contenu) {
        String sql = "DELETE FROM contenu WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, contenu.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Contenu> getALL() {
        String sql = "SELECT id, title, file_name, file_type, type, published, created_at, updated_at, file_size FROM contenu";
        List<Contenu> contenus = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Contenu contenu = new Contenu();
                contenu.setId(resultSet.getInt("id"));
                contenu.setTitle(resultSet.getString("title"));
                contenu.setFileName(resultSet.getString("file_name"));
                contenu.setFileType(resultSet.getString("file_type"));
                contenu.setType(resultSet.getString("type"));
                contenu.setPublished(resultSet.getByte("published"));
                contenu.setCreatedAt(resultSet.getTimestamp("created_at"));
                contenu.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                contenu.setFileSize(getNullableInteger(resultSet, "file_size"));
                contenus.add(contenu);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return contenus;
    }
}
