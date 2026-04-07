package services;

import entities.ForumCommentReaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumCommentReaction extends ServiceSupport implements IService<ForumCommentReaction> {

    @Override
    public void add(ForumCommentReaction reaction) {
        String sql = "INSERT INTO forum_comment_reaction (id, user_id, comment_id, type, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reaction.getId());
            statement.setInt(2, reaction.getUser().getId());
            statement.setInt(3, reaction.getForumComment().getId());
            statement.setString(4, reaction.getType());
            statement.setTimestamp(5, reaction.getCreatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumCommentReaction reaction) {
        String sql = "UPDATE forum_comment_reaction SET user_id = ?, comment_id = ?, type = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reaction.getUser().getId());
            statement.setInt(2, reaction.getForumComment().getId());
            statement.setString(3, reaction.getType());
            statement.setTimestamp(4, reaction.getCreatedAt());
            statement.setInt(5, reaction.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void delete(ForumCommentReaction reaction) {
        String sql = "DELETE FROM forum_comment_reaction WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reaction.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<ForumCommentReaction> getALL() {
        String sql = "SELECT id, user_id, comment_id, type, created_at FROM forum_comment_reaction";
        List<ForumCommentReaction> reactions = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ForumCommentReaction reaction = new ForumCommentReaction();
                reaction.setId(resultSet.getInt("id"));
                reaction.setUser(mapUserReference(resultSet.getInt("user_id")));
                reaction.setForumComment(mapForumCommentReference(resultSet.getInt("comment_id")));
                reaction.setType(resultSet.getString("type"));
                reaction.setCreatedAt(resultSet.getTimestamp("created_at"));
                reactions.add(reaction);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return reactions;
    }
}
