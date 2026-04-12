package services;

import entities.Question;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceQuestion extends ServiceSupport implements IService<Question> {

    @Override
    public void add(Question question) {
        String sql = "INSERT INTO question (quiz_id, type, question_text, points, position, explanation) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, question.getQuiz().getId());
            statement.setString(2, question.getType());
            statement.setString(3, question.getQuestionText());
            statement.setInt(4, question.getPoints());
            statement.setInt(5, question.getPosition());
            setNullableString(statement, 6, question.getExplanation());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Question question) {
        String sql = "UPDATE question SET quiz_id = ?, type = ?, question_text = ?, points = ?, position = ?, explanation = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, question.getQuiz().getId());
            statement.setString(2, question.getType());
            statement.setString(3, question.getQuestionText());
            statement.setInt(4, question.getPoints());
            statement.setInt(5, question.getPosition());
            setNullableString(statement, 6, question.getExplanation());
            statement.setInt(7, question.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Question question) {
        String sql = "DELETE FROM question WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, question.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Question> getALL() {
        String sql = "SELECT id, quiz_id, type, question_text, points, position, explanation FROM question";
        List<Question> questions = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Question question = new Question();
                question.setId(resultSet.getInt("id"));
                question.setQuiz(mapQuizReference(resultSet.getInt("quiz_id")));
                question.setType(resultSet.getString("type"));
                question.setQuestionText(resultSet.getString("question_text"));
                question.setPoints(resultSet.getInt("points"));
                question.setPosition(resultSet.getInt("position"));
                question.setExplanation(resultSet.getString("explanation"));
                questions.add(question);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return questions;
    }
}
