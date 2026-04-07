package services;

import entities.Quiz;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceQuiz extends ServiceSupport implements IService<Quiz> {

    @Override
    public void add(Quiz quiz) {
        String sql = "INSERT INTO quiz (contenu_id, title, description, passing_score, time_limit, shuffle_questions, shuffle_choices, show_correct_answers) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quiz.getContenu().getId());
            statement.setString(2, quiz.getTitle());
            setNullableString(statement, 3, quiz.getDescription());
            setNullableInteger(statement, 4, quiz.getPassingScore());
            setNullableInteger(statement, 5, quiz.getTimeLimit());
            statement.setByte(6, quiz.getShuffleQuestions());
            statement.setByte(7, quiz.getShuffleChoices());
            statement.setByte(8, quiz.getShowCorrectAnswers());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Quiz quiz) {
        String sql = "UPDATE quiz SET contenu_id = ?, title = ?, description = ?, passing_score = ?, time_limit = ?, shuffle_questions = ?, shuffle_choices = ?, show_correct_answers = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quiz.getContenu().getId());
            statement.setString(2, quiz.getTitle());
            setNullableString(statement, 3, quiz.getDescription());
            setNullableInteger(statement, 4, quiz.getPassingScore());
            setNullableInteger(statement, 5, quiz.getTimeLimit());
            statement.setByte(6, quiz.getShuffleQuestions());
            statement.setByte(7, quiz.getShuffleChoices());
            statement.setByte(8, quiz.getShowCorrectAnswers());
            statement.setInt(9, quiz.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Quiz quiz) {
        String sql = "DELETE FROM quiz WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quiz.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Quiz> getALL() {
        String sql = "SELECT id, contenu_id, title, description, passing_score, time_limit, shuffle_questions, shuffle_choices, show_correct_answers FROM quiz";
        List<Quiz> quizzes = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Quiz quiz = new Quiz();
                quiz.setId(resultSet.getInt("id"));
                quiz.setContenu(mapContenuReference(resultSet.getInt("contenu_id")));
                quiz.setTitle(resultSet.getString("title"));
                quiz.setDescription(resultSet.getString("description"));
                quiz.setPassingScore(getNullableInteger(resultSet, "passing_score"));
                quiz.setTimeLimit(getNullableInteger(resultSet, "time_limit"));
                quiz.setShuffleQuestions(resultSet.getByte("shuffle_questions"));
                quiz.setShuffleChoices(resultSet.getByte("shuffle_choices"));
                quiz.setShowCorrectAnswers(resultSet.getByte("show_correct_answers"));
                quizzes.add(quiz);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return quizzes;
    }
}
