package services;

import entities.UserAnswer;
import entities.User;
import entities.Quiz;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ServiceUserAnswer extends ServiceSupport implements IService<UserAnswer> {

    @Override
    public void add(UserAnswer userAnswer) {
        String sql = "INSERT INTO user_answer (user_id, quiz_id, score, total_points, started_at, completed_at, is_passed, cheat_flag) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userAnswer.getUser().getId());
            statement.setInt(2, userAnswer.getQuiz().getId());
            setNullableInteger(statement, 3, userAnswer.getScore());
            setNullableInteger(statement, 4, userAnswer.getTotalPoints());
            setNullableTimestamp(statement, 5, userAnswer.getStartedAt());
            setNullableTimestamp(statement, 6, userAnswer.getCompletedAt());
            statement.setByte(7, userAnswer.getIsPassed());
            statement.setByte(8, userAnswer.getCheatFlag());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error adding UserAnswer: " + e.getMessage());
        }
    }

    @Override
    public void update(UserAnswer userAnswer) {
        String sql = "UPDATE user_answer SET user_id = ?, quiz_id = ?, score = ?, total_points = ?, started_at = ?, completed_at = ?, is_passed = ?, cheat_flag = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userAnswer.getUser().getId());
            statement.setInt(2, userAnswer.getQuiz().getId());
            setNullableInteger(statement, 3, userAnswer.getScore());
            setNullableInteger(statement, 4, userAnswer.getTotalPoints());
            setNullableTimestamp(statement, 5, userAnswer.getStartedAt());
            setNullableTimestamp(statement, 6, userAnswer.getCompletedAt());
            statement.setByte(7, userAnswer.getIsPassed());
            statement.setByte(8, userAnswer.getCheatFlag());
            statement.setInt(9, userAnswer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error updating UserAnswer: " + e.getMessage());
        }
    }

    @Override
    public void delete(UserAnswer userAnswer) {
        String sql = "DELETE FROM user_answer WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userAnswer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error deleting UserAnswer: " + e.getMessage());
        }
    }

    @Override
    public List<UserAnswer> getALL() {
        String sql = "SELECT * FROM user_answer";
        List<UserAnswer> userAnswers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                UserAnswer userAnswer = mapResultSetToEntity(resultSet);
                userAnswers.add(userAnswer);
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving UserAnswers: " + e.getMessage());
        }
        return userAnswers;
    }

    /**
     * Get user attempt by ID
     */
    public UserAnswer getParFiltreInt(int id) {
        String sql = "SELECT * FROM user_answer WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToEntity(resultSet);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving UserAnswer by ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get user attempts for a specific quiz
     */
    public List<UserAnswer> getByQuizId(int quizId) {
        String sql = "SELECT * FROM user_answer WHERE quiz_id = ? ORDER BY completed_at DESC";
        List<UserAnswer> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quizId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapResultSetToEntity(resultSet));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving UserAnswers by Quiz ID: " + e.getMessage());
        }
        return results;
    }

    /**
     * Get user attempts for a specific student
     */
    public List<UserAnswer> getByUserId(int userId) {
        String sql = "SELECT * FROM user_answer WHERE user_id = ? ORDER BY completed_at DESC";
        List<UserAnswer> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapResultSetToEntity(resultSet));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving UserAnswers by User ID: " + e.getMessage());
        }
        return results;
    }

    /**
     * Get a specific user's attempt for a specific quiz
     */
    public UserAnswer getByUserAndQuiz(int userId, int quizId) {
        String sql = "SELECT * FROM user_answer WHERE user_id = ? AND quiz_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, quizId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToEntity(resultSet);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving UserAnswer by User and Quiz: " + e.getMessage());
        }
        return null;
    }

    /**
     * Set a student's score to 0 for a specific attempt
     */
    public void markScoreZero(int id) {
        String sql = "UPDATE user_answer SET score = 0, is_passed = 0 WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error marking score as zero: " + e.getMessage());
        }
    }

    /**
     * Map ResultSet to UserAnswer entity
     */
    private UserAnswer mapResultSetToEntity(ResultSet resultSet) throws SQLException {
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setId(resultSet.getInt("id"));
        userAnswer.setUser(mapUserReference(resultSet.getInt("user_id")));
        userAnswer.setQuiz(mapQuizReference(resultSet.getInt("quiz_id")));
        
        int score = resultSet.getInt("score");
        userAnswer.setScore(resultSet.wasNull() ? null : score);
        
        int totalPoints = resultSet.getInt("total_points");
        userAnswer.setTotalPoints(resultSet.wasNull() ? null : totalPoints);
        
        userAnswer.setStartedAt(resultSet.getTimestamp("started_at"));
        userAnswer.setCompletedAt(resultSet.getTimestamp("completed_at"));
        userAnswer.setIsPassed(resultSet.getByte("is_passed"));
        userAnswer.setCheatFlag(resultSet.getByte("cheat_flag"));
        
        return userAnswer;
    }

    private User mapUserReference(int userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    private Quiz mapQuizReference(int quizId) {
        Quiz quiz = new Quiz();
        quiz.setId(quizId);
        return quiz;
    }
}
