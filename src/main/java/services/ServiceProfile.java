package services;

import entities.Profile;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceProfile extends ServiceSupport implements IService<Profile> {

    @Override
    public void add(Profile profile) {
        String sql = "INSERT INTO profile (user_id, first_name, last_name, phone, photo, description, updated_at, avatar_filename, avatar_updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profile.getUser().getId());
            statement.setString(2, profile.getFirstName());
            statement.setString(3, profile.getLastName());
            setNullableString(statement, 4, profile.getPhone());
            setNullableString(statement, 5, profile.getPhoto());
            setNullableString(statement, 6, profile.getDescription());
            setNullableTimestamp(statement, 7, profile.getUpdatedAt());
            setNullableString(statement, 8, profile.getAvatarFilename());
            setNullableTimestamp(statement, 9, profile.getAvatarUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Profile profile) {
        String sql = "UPDATE profile SET user_id = ?, first_name = ?, last_name = ?, phone = ?, photo = ?, description = ?, updated_at = ?, avatar_filename = ?, avatar_updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profile.getUser().getId());
            statement.setString(2, profile.getFirstName());
            statement.setString(3, profile.getLastName());
            setNullableString(statement, 4, profile.getPhone());
            setNullableString(statement, 5, profile.getPhoto());
            setNullableString(statement, 6, profile.getDescription());
            setNullableTimestamp(statement, 7, profile.getUpdatedAt());
            setNullableString(statement, 8, profile.getAvatarFilename());
            setNullableTimestamp(statement, 9, profile.getAvatarUpdatedAt());
            statement.setInt(10, profile.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Profile profile) {
        String sql = "DELETE FROM profile WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profile.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Profile> getALL() {
        String sql = "SELECT id, user_id, first_name, last_name, phone, photo, description, updated_at, avatar_filename, avatar_updated_at FROM profile";
        List<Profile> profiles = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Profile profile = new Profile();
                profile.setId(resultSet.getInt("id"));
                profile.setUser(mapUserReference(resultSet.getInt("user_id")));
                profile.setFirstName(resultSet.getString("first_name"));
                profile.setLastName(resultSet.getString("last_name"));
                profile.setPhone(resultSet.getString("phone"));
                profile.setPhoto(resultSet.getString("photo"));
                profile.setDescription(resultSet.getString("description"));
                profile.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                profile.setAvatarFilename(resultSet.getString("avatar_filename"));
                profile.setAvatarUpdatedAt(resultSet.getTimestamp("avatar_updated_at"));
                profiles.add(profile);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return profiles;
    }
}
