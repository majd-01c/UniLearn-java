package services;

import entities.JobApplication;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceJobApplication extends ServiceSupport implements IService<JobApplication> {

    @Override
    public void add(JobApplication application) {
        String sql = "INSERT INTO job_application (id, student_id, offer_id, message, cv_file_name, status, created_at, updated_at, score, score_breakdown, scored_at, extracted_data, status_notified, status_notified_at, status_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, application.getId());
            statement.setInt(2, application.getUser().getId());
            statement.setInt(3, application.getJobOffer().getId());
            setNullableString(statement, 4, application.getMessage());
            setNullableString(statement, 5, application.getCvFileName());
            statement.setString(6, application.getStatus());
            statement.setTimestamp(7, application.getCreatedAt());
            setNullableTimestamp(statement, 8, application.getUpdatedAt());
            setNullableInteger(statement, 9, application.getScore());
            setNullableString(statement, 10, application.getScoreBreakdown());
            setNullableTimestamp(statement, 11, application.getScoredAt());
            setNullableString(statement, 12, application.getExtractedData());
            if (application.getStatusNotified() == null) {
                statement.setNull(13, java.sql.Types.TINYINT);
            } else {
                statement.setByte(13, application.getStatusNotified());
            }
            setNullableTimestamp(statement, 14, application.getStatusNotifiedAt());
            setNullableString(statement, 15, application.getStatusMessage());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(JobApplication application) {
        String sql = "UPDATE job_application SET student_id = ?, offer_id = ?, message = ?, cv_file_name = ?, status = ?, created_at = ?, updated_at = ?, score = ?, score_breakdown = ?, scored_at = ?, extracted_data = ?, status_notified = ?, status_notified_at = ?, status_message = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, application.getUser().getId());
            statement.setInt(2, application.getJobOffer().getId());
            setNullableString(statement, 3, application.getMessage());
            setNullableString(statement, 4, application.getCvFileName());
            statement.setString(5, application.getStatus());
            statement.setTimestamp(6, application.getCreatedAt());
            setNullableTimestamp(statement, 7, application.getUpdatedAt());
            setNullableInteger(statement, 8, application.getScore());
            setNullableString(statement, 9, application.getScoreBreakdown());
            setNullableTimestamp(statement, 10, application.getScoredAt());
            setNullableString(statement, 11, application.getExtractedData());
            if (application.getStatusNotified() == null) {
                statement.setNull(12, java.sql.Types.TINYINT);
            } else {
                statement.setByte(12, application.getStatusNotified());
            }
            setNullableTimestamp(statement, 13, application.getStatusNotifiedAt());
            setNullableString(statement, 14, application.getStatusMessage());
            statement.setInt(15, application.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(JobApplication application) {
        String sql = "DELETE FROM job_application WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, application.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<JobApplication> getALL() {
        String sql = "SELECT id, student_id, offer_id, message, cv_file_name, status, created_at, updated_at, score, score_breakdown, scored_at, extracted_data, status_notified, status_notified_at, status_message FROM job_application";
        List<JobApplication> applications = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                JobApplication application = new JobApplication();
                application.setId(resultSet.getInt("id"));
                application.setUser(mapUserReference(resultSet.getInt("student_id")));
                application.setJobOffer(mapJobOfferReference(resultSet.getInt("offer_id")));
                application.setMessage(resultSet.getString("message"));
                application.setCvFileName(resultSet.getString("cv_file_name"));
                application.setStatus(resultSet.getString("status"));
                application.setCreatedAt(resultSet.getTimestamp("created_at"));
                application.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                application.setScore(getNullableInteger(resultSet, "score"));
                application.setScoreBreakdown(resultSet.getString("score_breakdown"));
                application.setScoredAt(resultSet.getTimestamp("scored_at"));
                application.setExtractedData(resultSet.getString("extracted_data"));
                byte statusNotified = resultSet.getByte("status_notified");
                application.setStatusNotified(resultSet.wasNull() ? null : statusNotified);
                application.setStatusNotifiedAt(resultSet.getTimestamp("status_notified_at"));
                application.setStatusMessage(resultSet.getString("status_message"));
                applications.add(application);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return applications;
    }
}
