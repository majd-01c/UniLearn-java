package services;

import entities.JobOffer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceJobOffer extends ServiceSupport implements IService<JobOffer> {

    @Override
    public void add(JobOffer jobOffer) {
        String sql = "INSERT INTO job_offer (partner_id, title, type, location, description, requirements, status, created_at, updated_at, published_at, expires_at, required_skills, preferred_skills, min_experience_years, min_education, required_languages) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            setNullableString(statement, 12, jobOffer.getRequiredSkills());
            setNullableString(statement, 13, jobOffer.getPreferredSkills());
            setNullableInteger(statement, 14, jobOffer.getMinExperienceYears());
            setNullableString(statement, 15, jobOffer.getMinEducation());
            setNullableString(statement, 16, jobOffer.getRequiredLanguages());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(JobOffer jobOffer) {
        String sql = "UPDATE job_offer SET partner_id = ?, title = ?, type = ?, location = ?, description = ?, requirements = ?, status = ?, created_at = ?, updated_at = ?, published_at = ?, expires_at = ?, required_skills = ?, preferred_skills = ?, min_experience_years = ?, min_education = ?, required_languages = ? WHERE id = ?";
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
            setNullableString(statement, 12, jobOffer.getRequiredSkills());
            setNullableString(statement, 13, jobOffer.getPreferredSkills());
            setNullableInteger(statement, 14, jobOffer.getMinExperienceYears());
            setNullableString(statement, 15, jobOffer.getMinEducation());
            setNullableString(statement, 16, jobOffer.getRequiredLanguages());
            statement.setInt(17, jobOffer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(JobOffer jobOffer) {
        String sql = "DELETE FROM job_offer WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, jobOffer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<JobOffer> getALL() {
        String sql = "SELECT id, partner_id, title, type, location, description, requirements, status, created_at, updated_at, published_at, expires_at, required_skills, preferred_skills, min_experience_years, min_education, required_languages FROM job_offer";
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
                jobOffer.setRequiredSkills(resultSet.getString("required_skills"));
                jobOffer.setPreferredSkills(resultSet.getString("preferred_skills"));
                jobOffer.setMinExperienceYears(getNullableInteger(resultSet, "min_experience_years"));
                jobOffer.setMinEducation(resultSet.getString("min_education"));
                jobOffer.setRequiredLanguages(resultSet.getString("required_languages"));
                jobOffers.add(jobOffer);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return jobOffers;
    }
}
