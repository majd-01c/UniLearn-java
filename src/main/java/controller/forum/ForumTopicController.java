package controller.forum;

import entities.User;
import entities.forum.ForumComment;
import entities.forum.ForumCommentReaction;
import entities.forum.ForumTopic;
import entities.forum.TopicStatus;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import security.UserSession;
import services.forum.ServiceForumComment;
import services.forum.ServiceForumCommentReaction;
import services.forum.ServiceForumTopic;
import util.AppNavigator;

import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class ForumTopicController implements Initializable {

    @FXML private Label topicTitleLabel;
    @FXML private Label topicMetaLabel;
    @FXML private Label topicContentLabel;
    @FXML private Label topicStatusLabel;
    @FXML private Label topicViewsLabel;
    @FXML private VBox commentsContainer;
    @FXML private TextArea commentInput;
    @FXML private Button submitCommentButton;
    @FXML private HBox topicActionsBox;
    @FXML private Label pinnedBadge;
    @FXML private VBox commentFormBox;

    private final ServiceForumTopic topicService = new ServiceForumTopic();
    private final ServiceForumComment commentService = new ServiceForumComment();
    private final ServiceForumCommentReaction reactionService = new ServiceForumCommentReaction();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm");

    private ForumTopic topic;
    private int currentUserId;
    private String currentUserRole = "";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentUserId = UserSession.getCurrentUserId().orElse(0);
        currentUserRole = getCurrentUserRole();
    }

    public void setTopic(ForumTopic topic) {
        // Refresh from DB
        this.topic = topicService.getById(topic.getId());
        if (this.topic == null) {
            this.topic = topic;
        }

        // Increment view count
        topicService.incrementViewCount(this.topic.getId());

        renderTopic();
        loadComments();
    }

    private void renderTopic() {
        topicTitleLabel.setText(topic.getTitle());

        String authorName = topicService.getAuthorName(topic.getUser().getId());
        String categoryName = topicService.getCategoryName(topic.getForumCategory().getId());
        String created = topic.getCreatedAt() != null ? dateFormat.format(topic.getCreatedAt()) : "";
        topicMetaLabel.setText("by " + authorName + " in " + categoryName + " • " + created);

        topicContentLabel.setText(topic.getContent());
        topicContentLabel.setWrapText(true);

        topicStatusLabel.setText(topic.getTopicStatus().label());
        topicStatusLabel.getStyleClass().setAll("forum-status-badge", "status-" + topic.getTopicStatus().getValue());

        topicViewsLabel.setText("👁 " + (topic.getViewCount() + 1));

        pinnedBadge.setVisible(topic.isPinnedTopic());
        pinnedBadge.setManaged(topic.isPinnedTopic());

        // Hide comment form if topic is locked
        commentFormBox.setVisible(!topic.isLocked());
        commentFormBox.setManaged(!topic.isLocked());

        // Topic actions
        renderTopicActions();
    }

    private void renderTopicActions() {
        topicActionsBox.getChildren().clear();
        boolean isAuthor = topic.getUser().getId() == currentUserId;
        boolean isAdmin = currentUserRole.contains("ADMIN");
        boolean isStaff = isAdmin || currentUserRole.contains("TEACHER") || currentUserRole.contains("TRAINER");

        if (isAuthor || isAdmin) {
            Button editBtn = new Button("✏️ Edit");
            editBtn.getStyleClass().add("ghost-button");
            editBtn.setOnAction(e -> AppNavigator.showForumEditTopic(topic));
            topicActionsBox.getChildren().add(editBtn);

            Button deleteBtn = new Button("🗑 Delete");
            deleteBtn.getStyleClass().add("danger-button");
            deleteBtn.setOnAction(e -> onDeleteTopic());
            topicActionsBox.getChildren().add(deleteBtn);
        }

        if (isStaff) {
            Button pinBtn = new Button(topic.isPinnedTopic() ? "📌 Unpin" : "📌 Pin");
            pinBtn.getStyleClass().add("ghost-button");
            pinBtn.setOnAction(e -> onTogglePin());
            topicActionsBox.getChildren().add(pinBtn);

            Button lockBtn = new Button(topic.isLocked() ? "🔓 Unlock" : "🔒 Lock");
            lockBtn.getStyleClass().add("ghost-button");
            lockBtn.setOnAction(e -> onToggleLock());
            topicActionsBox.getChildren().add(lockBtn);
        }
    }

    private void loadComments() {
        commentsContainer.getChildren().clear();
        List<ForumComment> topLevelComments = commentService.findTopLevelByTopic(topic.getId());

        if (topLevelComments.isEmpty()) {
            Label empty = new Label("No comments yet. Be the first to reply!");
            empty.getStyleClass().add("forum-empty-label");
            empty.setPadding(new Insets(20));
            commentsContainer.getChildren().add(empty);
        } else {
            for (ForumComment comment : topLevelComments) {
                commentsContainer.getChildren().add(createCommentCard(comment, false));
            }
        }
    }

    private VBox createCommentCard(ForumComment comment, boolean isReply) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.getStyleClass().add(isReply ? "forum-reply-card" : "forum-comment-card");
        if (comment.isIsAccepted()) {
            card.getStyleClass().add("forum-accepted-card");
        }

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        String authorName = commentService.getAuthorName(comment.getUser().getId());
        Label authorLabel = new Label(authorName);
        authorLabel.getStyleClass().add("forum-comment-author");

        if (comment.isIsTeacherResponse()) {
            Label teacherBadge = new Label("🎓 Teacher");
            teacherBadge.getStyleClass().add("forum-teacher-badge");
            header.getChildren().add(teacherBadge);
        }
        if (comment.isIsAccepted()) {
            Label acceptedBadge = new Label("✅ Accepted Answer");
            acceptedBadge.getStyleClass().add("forum-accepted-badge");
            header.getChildren().add(acceptedBadge);
        }

        String date = comment.getCreatedAt() != null ? dateFormat.format(comment.getCreatedAt()) : "";
        Label dateLabel = new Label(date);
        dateLabel.getStyleClass().add("forum-item-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(authorLabel, spacer, dateLabel);

        // Content
        Label contentLabel = new Label(comment.getContent());
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("forum-comment-content");

        // Actions
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        // Like/dislike
        int likes = reactionService.countByType(comment.getId(), "like");
        int dislikes = reactionService.countByType(comment.getId(), "dislike");

        Button likeBtn = new Button("👍 " + likes);
        likeBtn.getStyleClass().add("reaction-button");
        ForumCommentReaction userReaction = reactionService.findUserReaction(currentUserId, comment.getId());
        if (userReaction != null && userReaction.isLike()) {
            likeBtn.getStyleClass().add("reaction-active");
        }
        likeBtn.setOnAction(e -> onReact(comment, "like"));

        Button dislikeBtn = new Button("👎 " + dislikes);
        dislikeBtn.getStyleClass().add("reaction-button");
        if (userReaction != null && userReaction.isDislike()) {
            dislikeBtn.getStyleClass().add("reaction-active");
        }
        dislikeBtn.setOnAction(e -> onReact(comment, "dislike"));

        actions.getChildren().addAll(likeBtn, dislikeBtn);

        // Reply button
        if (!topic.isLocked() && !isReply) {
            Button replyBtn = new Button("↩ Reply");
            replyBtn.getStyleClass().add("ghost-button");
            replyBtn.setOnAction(e -> onReply(comment, card));
            actions.getChildren().add(replyBtn);
        }

        // Accept answer (topic author or teacher, not own comment)
        boolean isTopicAuthor = topic.getUser().getId() == currentUserId;
        boolean isTeacher = currentUserRole.contains("TEACHER") || currentUserRole.contains("TRAINER");
        boolean isOwnComment = comment.getUser().getId() == currentUserId;

        if ((isTopicAuthor || isTeacher) && !isOwnComment) {
            if (comment.isIsAccepted() && isTopicAuthor) {
                Button unacceptBtn = new Button("❌ Unaccept");
                unacceptBtn.getStyleClass().add("ghost-button");
                unacceptBtn.setOnAction(e -> onToggleAccept(comment));
                actions.getChildren().add(unacceptBtn);
            } else if (!comment.isIsAccepted()) {
                Button acceptBtn = new Button("✅ Accept");
                acceptBtn.getStyleClass().add("ghost-button");
                acceptBtn.setOnAction(e -> onToggleAccept(comment));
                actions.getChildren().add(acceptBtn);
            }
        }

        // Edit (author only)
        if (isOwnComment) {
            Button editBtn = new Button("✏️");
            editBtn.getStyleClass().add("ghost-button");
            editBtn.setOnAction(e -> AppNavigator.showForumEditComment(comment));
            actions.getChildren().add(editBtn);
        }

        // Delete (admin only)
        if (currentUserRole.contains("ADMIN")) {
            Button delBtn = new Button("🗑");
            delBtn.getStyleClass().add("danger-button");
            delBtn.setOnAction(e -> onDeleteComment(comment));
            actions.getChildren().add(delBtn);
        }

        card.getChildren().addAll(header, contentLabel, actions);

        // Load replies
        if (!isReply) {
            List<ForumComment> replies = commentService.findReplies(comment.getId());
            if (!replies.isEmpty()) {
                VBox repliesBox = new VBox(6);
                repliesBox.setPadding(new Insets(0, 0, 0, 24));
                for (ForumComment reply : replies) {
                    repliesBox.getChildren().add(createCommentCard(reply, true));
                }
                card.getChildren().add(repliesBox);
            }
        }

        return card;
    }

    private void onReact(ForumComment comment, String type) {
        ForumCommentReaction existing = reactionService.findUserReaction(currentUserId, comment.getId());
        if (existing != null) {
            if (existing.getType().equals(type)) {
                reactionService.delete(existing);
            } else {
                existing.setType(type);
                reactionService.update(existing);
            }
        } else {
            ForumCommentReaction reaction = new ForumCommentReaction();
            reaction.setUser(createUserRef(currentUserId));
            reaction.setForumComment(comment);
            reaction.setType(type);
            reaction.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            reactionService.add(reaction);
        }
        loadComments();
    }

    private void onReply(ForumComment parent, VBox parentCard) {
        // Check if reply box already exists
        if (parentCard.lookup("#replyInput") != null) return;

        VBox replyBox = new VBox(8);
        replyBox.setPadding(new Insets(8, 0, 0, 24));

        TextArea replyInput = new TextArea();
        replyInput.setId("replyInput");
        replyInput.setPromptText("Write your reply...");
        replyInput.setPrefRowCount(3);
        replyInput.getStyleClass().add("forum-text-area");

        HBox replyActions = new HBox(8);
        Button sendBtn = new Button("Send Reply");
        sendBtn.getStyleClass().add("primary-button");
        sendBtn.setOnAction(e -> {
            String content = replyInput.getText().trim();
            if (content.isEmpty() || content.length() < 3) {
                showAlert("Reply must be at least 3 characters.");
                return;
            }
            ForumComment reply = new ForumComment();
            reply.setContent(content);
            reply.setUser(createUserRef(currentUserId));
            reply.setForumTopic(topic);
            reply.setForumComment(parent);
            reply.setIsTeacherResponse(isStaffRole());
            reply.setIsAccepted(false);
            commentService.add(reply);

            // Update last activity
            topic.setLastActivityAt(new Timestamp(System.currentTimeMillis()));
            topicService.update(topic);

            loadComments();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("ghost-button");
        cancelBtn.setOnAction(e -> parentCard.getChildren().remove(replyBox));

        replyActions.getChildren().addAll(sendBtn, cancelBtn);
        replyBox.getChildren().addAll(replyInput, replyActions);
        parentCard.getChildren().add(replyBox);
    }

    private void onToggleAccept(ForumComment comment) {
        boolean newState = !comment.isIsAccepted();
        commentService.toggleAccepted(comment.getId(), newState);

        // Update topic status
        if (newState) {
            if (topic.isOpen()) {
                topic.setStatus(TopicStatus.SOLVED.getValue());
                topicService.update(topic);
            }
        } else {
            if (!commentService.hasAcceptedAnswer(topic.getId())) {
                topic.setStatus(TopicStatus.OPEN.getValue());
                topicService.update(topic);
            }
        }

        // Refresh
        this.topic = topicService.getById(topic.getId());
        renderTopic();
        loadComments();
    }

    private void onTogglePin() {
        byte newPinned = (byte) (topic.isPinnedTopic() ? 0 : 1);
        topic.setIsPinned(newPinned);
        topicService.update(topic);
        renderTopic();
    }

    private void onToggleLock() {
        if (topic.isLocked()) {
            topic.setStatus(TopicStatus.OPEN.getValue());
        } else {
            topic.setStatus(TopicStatus.LOCKED.getValue());
        }
        topicService.update(topic);
        this.topic = topicService.getById(topic.getId());
        renderTopic();
        loadComments();
    }

    private void onDeleteTopic() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this topic and all its comments?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Topic");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            topicService.delete(topic);
            AppNavigator.showForum();
        }
    }

    private void onDeleteComment(ForumComment comment) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this comment?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Comment");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            commentService.delete(comment);

            // Update solved status
            if (!commentService.hasAcceptedAnswer(topic.getId()) && topic.isSolved()) {
                topic.setStatus(TopicStatus.OPEN.getValue());
                topicService.update(topic);
                this.topic = topicService.getById(topic.getId());
                renderTopic();
            }
            loadComments();
        }
    }

    @FXML
    private void onSubmitComment() {
        String content = commentInput.getText().trim();
        if (content.isEmpty()) {
            showAlert("Comment cannot be empty.");
            return;
        }
        if (content.length() < 3) {
            showAlert("Comment must be at least 3 characters.");
            return;
        }

        ForumComment comment = new ForumComment();
        comment.setContent(content);
        comment.setUser(createUserRef(currentUserId));
        comment.setForumTopic(topic);
        comment.setForumComment(null); // top-level
        comment.setIsTeacherResponse(isStaffRole());
        comment.setIsAccepted(false);
        commentService.add(comment);

        // Update last activity
        topic.setLastActivityAt(new Timestamp(System.currentTimeMillis()));
        topicService.update(topic);

        commentInput.clear();
        loadComments();
    }

    @FXML
    private void onBackToCategory() {
        if (topic.getForumCategory() != null) {
            AppNavigator.showForumCategory(topic.getForumCategory());
        } else {
            AppNavigator.showForum();
        }
    }

    @FXML
    private void onBackToForum() {
        AppNavigator.showForum();
    }

    private User createUserRef(int userId) {
        User u = new User();
        u.setId(userId);
        return u;
    }

    private boolean isStaffRole() {
        return currentUserRole.contains("ADMIN") || currentUserRole.contains("TEACHER") || currentUserRole.contains("TRAINER");
    }

    private String getCurrentUserRole() {
        return UserSession.getCurrentUserId().map(id -> {
            try (var stmt = Utils.MyDatabase.getInstance().getConnection().prepareStatement("SELECT role FROM user WHERE id = ?")) {
                stmt.setInt(1, id);
                var rs = stmt.executeQuery();
                if (rs.next()) return rs.getString("role") != null ? rs.getString("role").toUpperCase() : "";
            } catch (Exception e) { e.printStackTrace(); }
            return "";
        }).orElse("");
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Forum");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
