package services;

import entities.FaceVerificationLog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceFaceVerificationLog extends ServiceSupport implements IService<FaceVerificationLog> {

    @Override
    public void add(FaceVerificationLog log) {
        String sql = "INSERT INTO face_verification_log (id, user_id, action, distance, ip_address, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, log.getId());
            statement.setInt(2, log.getUser().getId());
            statement.setString(3, log.getAction());
            if (log.getDistance() == null) {
                statement.setNull(4, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(4, log.getDistance());
            }
            setNullableString(statement, 5, log.getIpAddress());
            statement.setTimestamp(6, log.getCreatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(FaceVerificationLog log) {
        String sql = "UPDATE face_verification_log SET user_id = ?, action = ?, distance = ?, ip_address = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, log.getUser().getId());
            statement.setString(2, log.getAction());
            if (log.getDistance() == null) {
                statement.setNull(3, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(3, log.getDistance());
            }
            setNullableString(statement, 4, log.getIpAddress());
            statement.setTimestamp(5, log.getCreatedAt());
            statement.setInt(6, log.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(FaceVerificationLog log) {
        String sql = "DELETE FROM face_verification_log WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, log.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<FaceVerificationLog> getALL() {
        String sql = "SELECT id, user_id, action, distance, ip_address, created_at FROM face_verification_log";
        List<FaceVerificationLog> logs = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                FaceVerificationLog log = new FaceVerificationLog();
                log.setId(resultSet.getInt("id"));
                log.setUser(mapUserReference(resultSet.getInt("user_id")));
                log.setAction(resultSet.getString("action"));
                double distance = resultSet.getDouble("distance");
                log.setDistance(resultSet.wasNull() ? null : distance);
                log.setIpAddress(resultSet.getString("ip_address"));
                log.setCreatedAt(resultSet.getTimestamp("created_at"));
                logs.add(log);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return logs;
    }
}
