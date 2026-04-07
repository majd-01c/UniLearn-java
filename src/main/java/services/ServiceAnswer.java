package services;

import entities.Answer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceAnswer extends ServiceSupport implements IService<Answer> {

    @Override
    public void add(Answer answer) {
        String sql = "INSERT INTO answer (selected_choice_id, user_answer_id, question_id, text_answer, is_correct, points_earned, ai_detection_score, ai_detection_result) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, answer.getChoice() == null ? null : answer.getChoice().getId());
            statement.setInt(2, answer.getUserAnswer().getId());
            statement.setInt(3, answer.getQuestion().getId());
            setNullableString(statement, 4, answer.getTextAnswer());
            statement.setByte(5, answer.getIsCorrect());
            setNullableInteger(statement, 6, answer.getPointsEarned());
            if (answer.getAiDetectionScore() == null) {
                statement.setNull(7, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(7, answer.getAiDetectionScore());
            }
            setNullableString(statement, 8, answer.getAiDetectionResult());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Answer answer) {
        String sql = "UPDATE answer SET selected_choice_id = ?, user_answer_id = ?, question_id = ?, text_answer = ?, is_correct = ?, points_earned = ?, ai_detection_score = ?, ai_detection_result = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, answer.getChoice() == null ? null : answer.getChoice().getId());
            statement.setInt(2, answer.getUserAnswer().getId());
            statement.setInt(3, answer.getQuestion().getId());
            setNullableString(statement, 4, answer.getTextAnswer());
            statement.setByte(5, answer.getIsCorrect());
            setNullableInteger(statement, 6, answer.getPointsEarned());
            if (answer.getAiDetectionScore() == null) {
                statement.setNull(7, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(7, answer.getAiDetectionScore());
            }
            setNullableString(statement, 8, answer.getAiDetectionResult());
            statement.setInt(9, answer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Answer answer) {
        String sql = "DELETE FROM answer WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, answer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Answer> getALL() {
        String sql = "SELECT id, selected_choice_id, user_answer_id, question_id, text_answer, is_correct, points_earned, ai_detection_score, ai_detection_result FROM answer";
        List<Answer> answers = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Answer answer = new Answer();
                answer.setId(resultSet.getInt("id"));
                answer.setChoice(mapChoiceReference(getNullableInteger(resultSet, "selected_choice_id")));
                answer.setUserAnswer(mapUserAnswerReference(resultSet.getInt("user_answer_id")));
                entities.Question question = new entities.Question();
                question.setId(resultSet.getInt("question_id"));
                answer.setQuestion(question);
                answer.setTextAnswer(resultSet.getString("text_answer"));
                answer.setIsCorrect(resultSet.getByte("is_correct"));
                answer.setPointsEarned(getNullableInteger(resultSet, "points_earned"));
                double aiDetectionScore = resultSet.getDouble("ai_detection_score");
                answer.setAiDetectionScore(resultSet.wasNull() ? null : aiDetectionScore);
                answer.setAiDetectionResult(resultSet.getString("ai_detection_result"));
                answers.add(answer);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return answers;
    }
}
