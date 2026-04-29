package services.job_offer;

import com.fasterxml.jackson.databind.ObjectMapper;
import entities.job_offer.JobOffer;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ServiceJobOffer extends ServiceSupport implements IService<JobOffer> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void add(JobOffer jobOffer) {
        String sql = "INSERT INTO job_offer (partner_id, title, type, location, description, requirements, status, created_at, updated_at, published_at, expires_at, required_skills, preferred_skills, min_experience_years, min_education, required_languages, score_config) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, jobOffer.getUser().getId());
            statement.setString(2, jobOffer.getTitle());
            statement.setString(3, jobOffer.getType());
            setNullableString(statement, 4, jobOffer.getLocation());
            statement.setString(5, jobOffer.getDescription());
            setNullableString(statement, 6, jobOffer.getRequirements());
            statement.setString(7, jobOffer.getStatus());
            statement.setTimestamp(8, jobOffer.getCreatedAt());
            statement.setTimestamp(9, jobOffer.getUpdatedAt());
            setNullableTimestamp(statement, 10, jobOffer.getPublishedAt());
            setNullableTimestamp(statement, 11, jobOffer.getExpiresAt());
            setNullableString(statement, 12, toJsonArrayOrNull(jobOffer.getRequiredSkills()));
            setNullableString(statement, 13, toJsonArrayOrNull(jobOffer.getPreferredSkills()));
            setNullableInteger(statement, 14, jobOffer.getMinExperienceYears());
            setNullableString(statement, 15, jobOffer.getMinEducation());
            setNullableString(statement, 16, toJsonArrayOrNull(jobOffer.getRequiredLanguages()));
            setNullableString(statement, 17, jobOffer.getScoreConfig());

            int affectedRows = statement.executeUpdate();
            if (affectedRows != 1) {
                throw new SQLException("Failed to insert job offer: affected rows = " + affectedRows);
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    jobOffer.setId(generatedKeys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add job offer", e);
        }
    }

    @Override
    public void update(JobOffer jobOffer) {
        String sql = "UPDATE job_offer SET partner_id = ?, title = ?, type = ?, location = ?, description = ?, requirements = ?, status = ?, created_at = ?, updated_at = ?, published_at = ?, expires_at = ?, required_skills = ?, preferred_skills = ?, min_experience_years = ?, min_education = ?, required_languages = ?, score_config = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, jobOffer.getUser().getId());
            statement.setString(2, jobOffer.getTitle());
            statement.setString(3, jobOffer.getType());
            setNullableString(statement, 4, jobOffer.getLocation());
            statement.setString(5, jobOffer.getDescription());
            setNullableString(statement, 6, jobOffer.getRequirements());
            statement.setString(7, jobOffer.getStatus());
            statement.setTimestamp(8, jobOffer.getCreatedAt());
            statement.setTimestamp(9, jobOffer.getUpdatedAt());
            setNullableTimestamp(statement, 10, jobOffer.getPublishedAt());
            setNullableTimestamp(statement, 11, jobOffer.getExpiresAt());
            setNullableString(statement, 12, toJsonArrayOrNull(jobOffer.getRequiredSkills()));
            setNullableString(statement, 13, toJsonArrayOrNull(jobOffer.getPreferredSkills()));
            setNullableInteger(statement, 14, jobOffer.getMinExperienceYears());
            setNullableString(statement, 15, jobOffer.getMinEducation());
            setNullableString(statement, 16, toJsonArrayOrNull(jobOffer.getRequiredLanguages()));
            setNullableString(statement, 17, jobOffer.getScoreConfig());
            statement.setInt(18, jobOffer.getId());

            int affectedRows = statement.executeUpdate();
            if (affectedRows != 1) {
                throw new SQLException("Failed to update job offer with id " + jobOffer.getId() + ": affected rows = " + affectedRows);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update job offer", e);
        }
    }

    @Override
    public void delete(JobOffer jobOffer) {
        String sql = "DELETE FROM job_offer WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, jobOffer.getId());

            int affectedRows = statement.executeUpdate();
            if (affectedRows != 1) {
                throw new SQLException("Failed to delete job offer with id " + jobOffer.getId() + ": affected rows = " + affectedRows);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete job offer", e);
        }
    }

    @Override
    public List<JobOffer> getALL() {
        String sql = "SELECT id, partner_id, title, type, location, description, requirements, status, created_at, updated_at, published_at, expires_at, required_skills, preferred_skills, min_experience_years, min_education, required_languages, score_config FROM job_offer";
        List<JobOffer> jobOffers = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                JobOffer jobOffer = new JobOffer();
                jobOffer.setId(resultSet.getInt("id"));
                jobOffer.setUser(mapUserReference(resultSet.getInt("partner_id")));
                jobOffer.setTitle(resultSet.getString("title"));
                jobOffer.setType(resultSet.getString("type"));
                jobOffer.setLocation(resultSet.getString("location"));
                jobOffer.setDescription(resultSet.getString("description"));
                jobOffer.setRequirements(resultSet.getString("requirements"));
                jobOffer.setStatus(resultSet.getString("status"));
                jobOffer.setCreatedAt(resultSet.getTimestamp("created_at"));
                jobOffer.setUpdatedAt(resultSet.getTimestamp("updated_at"));
                jobOffer.setPublishedAt(resultSet.getTimestamp("published_at"));
                jobOffer.setExpiresAt(resultSet.getTimestamp("expires_at"));
                jobOffer.setRequiredSkills(fromJsonArrayToDisplay(resultSet.getString("required_skills")));
                jobOffer.setPreferredSkills(fromJsonArrayToDisplay(resultSet.getString("preferred_skills")));
                jobOffer.setMinExperienceYears(getNullableInteger(resultSet, "min_experience_years"));
                jobOffer.setMinEducation(resultSet.getString("min_education"));
                jobOffer.setRequiredLanguages(fromJsonArrayToDisplay(resultSet.getString("required_languages")));
                jobOffer.setScoreConfig(resultSet.getString("score_config"));
                jobOffers.add(jobOffer);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return jobOffers;
    }

    private String toJsonArrayOrNull(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (looksLikeJson(trimmed)) {
            return trimmed;
        }

        String[] parts = trimmed.split(",");
        StringBuilder builder = new StringBuilder("[");
        int itemCount = 0;

        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }

            if (itemCount > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(item)).append('"');
            itemCount++;
        }

        if (itemCount == 0) {
            return null;
        }

        builder.append(']');
        return builder.toString();
    }

    private String fromJsonArrayToDisplay(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (!looksLikeJson(trimmed)) {
            return rawValue;
        }

        try {
            String[] values = OBJECT_MAPPER.readValue(trimmed, String[].class);
            return String.join(", ", values);
        } catch (Exception exception) {
            return rawValue;
        }
    }

    private boolean looksLikeJson(String value) {
        return (value.startsWith("[") && value.endsWith("]"))
                || (value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("\"") && value.endsWith("\""));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
