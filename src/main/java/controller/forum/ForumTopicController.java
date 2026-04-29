package controller.forum;

import entities.User;
import entities.forum.ForumComment;
import entities.forum.ForumCommentReaction;
import entities.forum.ForumTopic;
import entities.forum.TopicStatus;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
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
import services.forum.ForumAiAssistantService;
import util.AppNavigator;
import javafx.util.Duration;

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
    @FXML private VBox aiAnswerBox;
    @FXML private Button aiAnswerButton;
    @FXML private VBox aiAnswerContentBox;
    @FXML private HBox aiAnswerLoadingBox;
    @FXML private Label aiAnswerLabel;
    @FXML private HBox toxicityCheckingBox;
    @FXML private VBox toxicityWarningBox;
    @FXML private Label toxicityReasonLabel;

    private final ServiceForumTopic topicService = new ServiceForumTopic();
    private final ServiceForumComment commentService = new ServiceForumComment();
    private final ServiceForumCommentReaction reactionService = new ServiceForumCommentReaction();
    private final ForumAiAssistantService aiAssistantService = new ForumAiAssistantService();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm");

    private ForumTopic topic;
    private int currentUserId;
    private String currentUserRole = "";
    private PauseTransition toxicityDelay;
    private String lastCheckedCommentText = "";
    private boolean highSeverityToxicity = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentUserId = UserSession.getCurrentUserId().orElse(0);
        currentUserRole = getCurrentUserRole();
        setupToxicityCheck();
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
        resetAiAnswer();
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

        Button aiRateBtn = new Button("AI Rate");
        aiRateBtn.getStyleClass().add("reaction-button");
        aiRateBtn.setOnAction(e -> onRateCommentQuality(comment, aiRateBtn));
        actions.getChildren().add(aiRateBtn);

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
        String content = commentInput.getText() == null ? "" : commentInput.getText().trim();
        if (highSeverityToxicity) {
            showAlert("Please revise the comment before posting.");
            return;
        }
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
        hideToxicityWarning();
        loadComments();
    }

    @FXML
    private void onGenerateAiAnswer() {
        if (!aiAssistantService.isAvailable()) {
            showAiAnswerText("AI service is not configured. Set GROQ_API_KEY or groq.local.properties.");
            return;
        }

        setAiAnswerLoading(true);
        aiAnswerButton.setDisable(true);
        aiAnswerButton.setText("Generating...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                List<ForumComment> comments = commentService.findTopLevelByTopic(topic.getId());
                return aiAssistantService.generateTopicAnswer(topic, comments);
            }
        };

        task.setOnSucceeded(event -> {
            setAiAnswerLoading(false);
            String answer = task.getValue();
            if (answer == null || answer.isBlank()) {
                showAiAnswerText("AI could not generate an answer right now.");
                aiAnswerButton.setDisable(false);
                aiAnswerButton.setText("Try Again");
            } else {
                showAiAnswerText(answer);
                aiAnswerButton.setText("AI Answer Generated");
            }
        });
        task.setOnFailed(event -> {
            setAiAnswerLoading(false);
            showAiAnswerText("AI answer failed. Please try again.");
            aiAnswerButton.setDisable(false);
            aiAnswerButton.setText("Try Again");
        });

        Thread thread = new Thread(task, "forum-ai-answer");
        thread.setDaemon(true);
        thread.start();
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

    private void setupToxicityCheck() {
        if (commentInput == null || toxicityWarningBox == null) {
            return;
        }

        toxicityDelay = new PauseTransition(Duration.millis(1500));
        toxicityDelay.setOnFinished(event -> runToxicityCheck());

        commentInput.textProperty().addListener((observable, oldValue, newValue) -> {
            highSeverityToxicity = false;
            submitCommentButton.setDisable(false);
            String text = newValue == null ? "" : newValue.trim();
            if (text.length() < 10 || !aiAssistantService.isAvailable()) {
                hideToxicityWarning();
                return;
            }

            toxicityDelay.playFromStart();
        });
    }

    private void runToxicityCheck() {
        String text = commentInput.getText() == null ? "" : commentInput.getText().trim();
        if (text.length() < 10 || text.equals(lastCheckedCommentText)) {
            return;
        }
        lastCheckedCommentText = text;
        setToxicityChecking(true);

        Task<ForumAiAssistantService.ToxicityResult> task = new Task<>() {
            @Override
            protected ForumAiAssistantService.ToxicityResult call() {
                return aiAssistantService.checkToxicity(text);
            }
        };

        task.setOnSucceeded(event -> {
            setToxicityChecking(false);
            String current = commentInput.getText() == null ? "" : commentInput.getText().trim();
            if (!text.equals(current)) {
                return;
            }
            displayToxicityResult(task.getValue());
        });
        task.setOnFailed(event -> {
            setToxicityChecking(false);
            hideToxicityWarning();
        });

        Thread thread = new Thread(task, "forum-toxicity-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void displayToxicityResult(ForumAiAssistantService.ToxicityResult result) {
        if (result == null || !result.isToxic()) {
            hideToxicityWarning();
            return;
        }

        String severity = result.severity() == null ? "medium" : result.severity().toLowerCase();
        highSeverityToxicity = "high".equals(severity);
        submitCommentButton.setDisable(highSeverityToxicity);

        toxicityReasonLabel.setText(result.reason() == null || result.reason().isBlank()
                ? "This comment may contain inappropriate content."
                : result.reason());
        toxicityWarningBox.getStyleClass().setAll("forum-toxicity-warning", "toxicity-" + severity);
        toxicityWarningBox.setVisible(true);
        toxicityWarningBox.setManaged(true);
    }

    private void hideToxicityWarning() {
        highSeverityToxicity = false;
        if (submitCommentButton != null) {
            submitCommentButton.setDisable(false);
        }
        if (toxicityWarningBox != null) {
            toxicityWarningBox.setVisible(false);
            toxicityWarningBox.setManaged(false);
        }
        if (toxicityReasonLabel != null) {
            toxicityReasonLabel.setText("");
        }
        setToxicityChecking(false);
    }

    private void setToxicityChecking(boolean checking) {
        if (toxicityCheckingBox == null) {
            return;
        }
        toxicityCheckingBox.setVisible(checking);
        toxicityCheckingBox.setManaged(checking);
    }

    private void onRateCommentQuality(ForumComment comment, Button rateButton) {
        if (!aiAssistantService.isAvailable()) {
            rateButton.setText("AI off");
            rateButton.setTooltip(new Tooltip("AI service is not configured."));
            return;
        }

        rateButton.setDisable(true);
        rateButton.setText("Rating...");

        String question = topic.getTitle() + "\n" + truncate(topic.getContent(), 500);
        String answer = truncate(comment.getContent(), 500);

        Task<ForumAiAssistantService.QualityRatingResult> task = new Task<>() {
            @Override
            protected ForumAiAssistantService.QualityRatingResult call() {
                return aiAssistantService.rateAnswerQuality(question, answer);
            }
        };

        task.setOnSucceeded(event -> {
            ForumAiAssistantService.QualityRatingResult result = task.getValue();
            if (result == null || result.score() <= 0) {
                rateButton.setText("N/A");
                rateButton.setDisable(false);
                return;
            }

            rateButton.setText(result.score() + "/5 " + result.label());
            rateButton.setTooltip(new Tooltip(result.reason()));
            rateButton.setDisable(true);
        });
        task.setOnFailed(event -> {
            rateButton.setText("Error");
            rateButton.setDisable(false);
        });

        Thread thread = new Thread(task, "forum-ai-rate-comment");
        thread.setDaemon(true);
        thread.start();
    }

    private void resetAiAnswer() {
        if (aiAnswerContentBox == null) {
            return;
        }
        aiAnswerContentBox.setVisible(false);
        aiAnswerContentBox.setManaged(false);
        aiAnswerLabel.setText("");
        aiAnswerButton.setDisable(false);
        aiAnswerButton.setText("Get AI Answer");
        setAiAnswerLoading(false);
    }

    private void setAiAnswerLoading(boolean loading) {
        if (aiAnswerContentBox == null || aiAnswerLoadingBox == null) {
            return;
        }
        aiAnswerContentBox.setVisible(true);
        aiAnswerContentBox.setManaged(true);
        aiAnswerLoadingBox.setVisible(loading);
        aiAnswerLoadingBox.setManaged(loading);
    }

    private void showAiAnswerText(String text) {
        if (aiAnswerContentBox == null) {
            return;
        }
        aiAnswerContentBox.setVisible(true);
        aiAnswerContentBox.setManaged(true);
        aiAnswerLabel.setText(text == null ? "" : text);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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
