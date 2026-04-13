package services.forum;

import entities.forum.ForumCommentReaction;
import services.IService;
import services.ServiceSupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ServiceForumCommentReaction extends ServiceSupport implements IService<ForumCommentReaction> {

    @Override
    public void add(ForumCommentReaction reaction) {
        String sql = "INSERT INTO forum_comment_reaction (user_id, comment_id, type, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, reaction.getUser().getId());
            statement.setInt(2, reaction.getForumComment().getId());
            statement.setString(3, reaction.getType());
            statement.setTimestamp(4, reaction.getCreatedAt() != null ? reaction.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    reaction.setId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void update(ForumCommentReaction reaction) {
        String sql = "UPDATE forum_comment_reaction SET type = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reaction.getType());
            statement.setInt(2, reaction.getId());
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
        return executeQuery(sql);
    }

    public ForumCommentReaction findUserReaction(int userId, int commentId) {
        String sql = "SELECT id, user_id, comment_id, type, created_at FROM forum_comment_reaction WHERE user_id = ? AND comment_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, commentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public int countByType(int commentId, String type) {
        String sql = "SELECT COUNT(*) FROM forum_comment_reaction WHERE comment_id = ? AND type = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, commentId);
            statement.setString(2, type);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    public void deleteByUserAndComment(int userId, int commentId) {
        String sql = "DELETE FROM forum_comment_reaction WHERE user_id = ? AND comment_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, commentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private List<ForumCommentReaction> executeQuery(String sql) {
        List<ForumCommentReaction> reactions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) reactions.add(mapRow(rs));
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return reactions;
    }

    private ForumCommentReaction mapRow(ResultSet rs) throws SQLException {
        ForumCommentReaction reaction = new ForumCommentReaction();
        reaction.setId(rs.getInt("id"));
        reaction.setUser(mapUserReference(rs.getInt("user_id")));
        reaction.setForumComment(mapForumCommentReference(rs.getInt("comment_id")));
        reaction.setType(rs.getString("type"));
        reaction.setCreatedAt(rs.getTimestamp("created_at"));
        return reaction;
    }
}
