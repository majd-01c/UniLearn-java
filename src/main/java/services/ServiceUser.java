package services;

import entities.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceUser extends ServiceSupport implements IService<User> {

    @Override
    public void add(User user) {
        String sql = "INSERT INTO user (role, email, password, is_active, name, phone, profile_pic, location, skills, about, is_verified, needs_verification, email_verified_at, email_verification_code, code_expiry_date, must_change_password, temp_password_generated_at, created_at, updated_at, face_enabled, face_descriptors, face_enrolled_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getRole());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPassword());
            statement.setByte(4, user.getIsActive());
            setNullableString(statement, 5, user.getName());
            setNullableString(statement, 6, user.getPhone());
            setNullableString(statement, 7, user.getProfilePic());
            setNullableString(statement, 8, user.getLocation());
            setNullableString(statement, 9, user.getSkills());
            setNullableString(statement, 10, user.getAbout());
            statement.setByte(11, user.getIsVerified());
            statement.setByte(12, user.getNeedsVerification());
            setNullableTimestamp(statement, 13, user.getEmailVerifiedAt());
            setNullableString(statement, 14, user.getEmailVerificationCode());
            setNullableTimestamp(statement, 15, user.getCodeExpiryDate());
            statement.setByte(16, user.getMustChangePassword());
            setNullableTimestamp(statement, 17, user.getTempPasswordGeneratedAt());
            statement.setTimestamp(18, user.getCreatedAt());
            statement.setTimestamp(19, user.getUpdatedAt());
            statement.setByte(20, user.getFaceEnabled());
            setNullableString(statement, 21, user.getFaceDescriptors());
            setNullableTimestamp(statement, 22, user.getFaceEnrolledAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE user SET role = ?, email = ?, password = ?, is_active = ?, name = ?, phone = ?, profile_pic = ?, location = ?, skills = ?, about = ?, is_verified = ?, needs_verification = ?, email_verified_at = ?, email_verification_code = ?, code_expiry_date = ?, must_change_password = ?, temp_password_generated_at = ?, created_at = ?, updated_at = ?, face_enabled = ?, face_descriptors = ?, face_enrolled_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getRole());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPassword());
            statement.setByte(4, user.getIsActive());
            setNullableString(statement, 5, user.getName());
            setNullableString(statement, 6, user.getPhone());
            setNullableString(statement, 7, user.getProfilePic());
            setNullableString(statement, 8, user.getLocation());
            setNullableString(statement, 9, user.getSkills());
            setNullableString(statement, 10, user.getAbout());
            statement.setByte(11, user.getIsVerified());
            statement.setByte(12, user.getNeedsVerification());
            setNullableTimestamp(statement, 13, user.getEmailVerifiedAt());
            setNullableString(statement, 14, user.getEmailVerificationCode());
            setNullableTimestamp(statement, 15, user.getCodeExpiryDate());
            statement.setByte(16, user.getMustChangePassword());
            setNullableTimestamp(statement, 17, user.getTempPasswordGeneratedAt());
            statement.setTimestamp(18, user.getCreatedAt());
            statement.setTimestamp(19, user.getUpdatedAt());
            statement.setByte(20, user.getFaceEnabled());
            setNullableString(statement, 21, user.getFaceDescriptors());
            setNullableTimestamp(statement, 22, user.getFaceEnrolledAt());
            statement.setInt(23, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(User user) {
        String sql = "DELETE FROM user WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<User> getALL() {
        String sql = "SELECT id, role, email, password, is_active, name, phone, profile_pic, location, skills, about, is_verified, needs_verification, email_verified_at, email_verification_code, code_expiry_date, must_change_password, temp_password_generated_at, created_at, updated_at, face_enabled, face_descriptors, face_enrolled_at FROM user";
        List<User> users = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapResultSetToUser(resultSet));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return users;
    }

    public User getById(int id) {
        String sql = "SELECT id, role, email, password, is_active, name, phone, profile_pic, location, skills, about, is_verified, needs_verification, email_verified_at, email_verification_code, code_expiry_date, must_change_password, temp_password_generated_at, created_at, updated_at, face_enabled, face_descriptors, face_enrolled_at FROM user WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToUser(resultSet);
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    private User mapResultSetToUser(ResultSet resultSet) throws SQLException {
        User user = new User();
        user.setId(resultSet.getInt("id"));
        user.setRole(resultSet.getString("role"));
        user.setEmail(resultSet.getString("email"));
        user.setPassword(resultSet.getString("password"));
        user.setIsActive(resultSet.getByte("is_active"));
        user.setName(resultSet.getString("name"));
        user.setPhone(resultSet.getString("phone"));
        user.setProfilePic(resultSet.getString("profile_pic"));
        user.setLocation(resultSet.getString("location"));
        user.setSkills(resultSet.getString("skills"));
        user.setAbout(resultSet.getString("about"));
        user.setIsVerified(resultSet.getByte("is_verified"));
        user.setNeedsVerification(resultSet.getByte("needs_verification"));
        user.setEmailVerifiedAt(resultSet.getTimestamp("email_verified_at"));
        user.setEmailVerificationCode(resultSet.getString("email_verification_code"));
        user.setCodeExpiryDate(resultSet.getTimestamp("code_expiry_date"));
        user.setMustChangePassword(resultSet.getByte("must_change_password"));
        user.setTempPasswordGeneratedAt(resultSet.getTimestamp("temp_password_generated_at"));
        user.setCreatedAt(resultSet.getTimestamp("created_at"));
        user.setUpdatedAt(resultSet.getTimestamp("updated_at"));
        user.setFaceEnabled(resultSet.getByte("face_enabled"));
        user.setFaceDescriptors(resultSet.getString("face_descriptors"));
        user.setFaceEnrolledAt(resultSet.getTimestamp("face_enrolled_at"));
        return user;
    }
}
