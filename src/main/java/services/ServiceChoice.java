package services;

import entities.Choice;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceChoice extends ServiceSupport implements IService<Choice> {

    @Override
    public void add(Choice choice) {
        String sql = "INSERT INTO choice (question_id, choice_text, is_correct, position) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, choice.getQuestion().getId());
            statement.setString(2, choice.getChoiceText());
            statement.setByte(3, choice.getIsCorrect());
            statement.setInt(4, choice.getPosition());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(Choice choice) {
        String sql = "UPDATE choice SET question_id = ?, choice_text = ?, is_correct = ?, position = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, choice.getQuestion().getId());
            statement.setString(2, choice.getChoiceText());
            statement.setByte(3, choice.getIsCorrect());
            statement.setInt(4, choice.getPosition());
            statement.setInt(5, choice.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(Choice choice) {
        String sql = "DELETE FROM choice WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, choice.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Choice> getALL() {
        String sql = "SELECT id, question_id, choice_text, is_correct, position FROM choice";
        List<Choice> choices = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Choice choice = new Choice();
                choice.setId(resultSet.getInt("id"));
                choice.setQuestion(mapQuestionReference(resultSet.getInt("question_id")));
                choice.setChoiceText(resultSet.getString("choice_text"));
                choice.setIsCorrect(resultSet.getByte("is_correct"));
                choice.setPosition(resultSet.getInt("position"));
                choices.add(choice);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return choices;
    }

    private entities.Question mapQuestionReference(Integer id) {
        if (id == null) {
            return null;
        }
        entities.Question question = new entities.Question();
        question.setId(id);
        return question;
    }

    /**
     * Get all choices for a specific question
     */
    public List<Choice> getByQuestionId(int questionId) {
        String sql = "SELECT id, question_id, choice_text, is_correct, position FROM choice WHERE question_id = ? ORDER BY position";
        List<Choice> choices = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Choice choice = new Choice();
                    choice.setId(resultSet.getInt("id"));
                    choice.setQuestion(mapQuestionReference(resultSet.getInt("question_id")));
                    choice.setChoiceText(resultSet.getString("choice_text"));
                    choice.setIsCorrect(resultSet.getByte("is_correct"));
                    choice.setPosition(resultSet.getInt("position"));
                    choices.add(choice);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving choices by question_id: " + e.getMessage());
        }
        return choices;
    }

    /**
     * Get a single choice by ID (fully loaded with isCorrect)
     */
    public Choice getById(int choiceId) {
        String sql = "SELECT id, question_id, choice_text, is_correct, position FROM choice WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, choiceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Choice choice = new Choice();
                    choice.setId(resultSet.getInt("id"));
                    choice.setQuestion(mapQuestionReference(resultSet.getInt("question_id")));
                    choice.setChoiceText(resultSet.getString("choice_text"));
                    choice.setIsCorrect(resultSet.getByte("is_correct"));
                    choice.setPosition(resultSet.getInt("position"));
                    return choice;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving choice by id: " + e.getMessage());
        }
        return null;
    }
}

