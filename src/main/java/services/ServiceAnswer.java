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
        String sql = "INSERT INTO answer (selected_choice_id, user_answer_id, question_id, text_answer, is_correct, points_earned) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, answer.getChoice() == null ? null : answer.getChoice().getId());
            statement.setInt(2, answer.getUserAnswer().getId());
            statement.setInt(3, answer.getQuestion().getId());
            setNullableString(statement, 4, answer.getTextAnswer());
            statement.setByte(5, answer.getIsCorrect());
            setNullableInteger(statement, 6, answer.getPointsEarned());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Answer answer) {
        String sql = "UPDATE answer SET selected_choice_id = ?, user_answer_id = ?, question_id = ?, text_answer = ?, is_correct = ?, points_earned = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, answer.getChoice() == null ? null : answer.getChoice().getId());
            statement.setInt(2, answer.getUserAnswer().getId());
            statement.setInt(3, answer.getQuestion().getId());
            setNullableString(statement, 4, answer.getTextAnswer());
            statement.setByte(5, answer.getIsCorrect());
            setNullableInteger(statement, 6, answer.getPointsEarned());
            statement.setInt(7, answer.getId());
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
        String sql = "SELECT id, selected_choice_id, user_answer_id, question_id, text_answer, is_correct, points_earned FROM answer";
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
                answers.add(answer);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return answers;
    }

    /**
     * Get all answers for a specific user attempt
     */
    public List<Answer> getByUserAnswerId(int userAnswerId) {
        String sql = "SELECT id, selected_choice_id, user_answer_id, question_id, text_answer, is_correct, points_earned FROM answer WHERE user_answer_id = ?";
        List<Answer> answers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userAnswerId);
            try (ResultSet resultSet = statement.executeQuery()) {
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
                    answers.add(answer);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving answers by user_answer_id: " + e.getMessage());
        }
        return answers;
    }

    /**
     * Get a specific answer for a user attempt and question (for upsert logic)
     */
    public Answer getByUserAnswerAndQuestion(int userAnswerId, int questionId) {
        String sql = "SELECT id, selected_choice_id, user_answer_id, question_id, text_answer, is_correct, points_earned FROM answer WHERE user_answer_id = ? AND question_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userAnswerId);
            statement.setInt(2, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
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
                    return answer;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving answer by user_answer and question: " + e.getMessage());
        }
        return null;
    }
}
