package services;

import entities.ResetToken;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceResetToken extends ServiceSupport implements IService<ResetToken> {

    @Override
    public void add(ResetToken token) {
        String sql = "INSERT INTO reset_token (id, user_id, token, expiry_date, created_at, used) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, token.getId());
            statement.setInt(2, token.getUser().getId());
            statement.setString(3, token.getToken());
            statement.setTimestamp(4, token.getExpiryDate());
            statement.setTimestamp(5, token.getCreatedAt());
            statement.setByte(6, token.getUsed());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ResetToken token) {
        String sql = "UPDATE reset_token SET user_id = ?, token = ?, expiry_date = ?, created_at = ?, used = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, token.getUser().getId());
            statement.setString(2, token.getToken());
            statement.setTimestamp(3, token.getExpiryDate());
            statement.setTimestamp(4, token.getCreatedAt());
            statement.setByte(5, token.getUsed());
            statement.setInt(6, token.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ResetToken token) {
        String sql = "DELETE FROM reset_token WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, token.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ResetToken> getALL() {
        String sql = "SELECT id, user_id, token, expiry_date, created_at, used FROM reset_token";
        List<ResetToken> tokens = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ResetToken token = new ResetToken();
                token.setId(resultSet.getInt("id"));
                token.setUser(mapUserReference(resultSet.getInt("user_id")));
                token.setToken(resultSet.getString("token"));
                token.setExpiryDate(resultSet.getTimestamp("expiry_date"));
                token.setCreatedAt(resultSet.getTimestamp("created_at"));
                token.setUsed(resultSet.getByte("used"));
                tokens.add(token);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return tokens;
    }
}
