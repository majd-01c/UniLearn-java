package controller.forum;

import entities.User;
import entities.forum.ForumCategory;
import entities.forum.ForumTopic;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import security.UserSession;
import services.forum.ForumAiAssistantService;
import services.forum.ServiceForumCategory;
import services.forum.ServiceForumTopic;
import util.AppNavigator;

import java.net.URL;
import java.sql.Timestamp;
import java.util.List;
import java.util.ResourceBundle;

public class ForumNewTopicController implements Initializable {

    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private ComboBox<ForumCategory> categoryComboBox;
    @FXML private Label titleErrorLabel;
    @FXML private Label contentErrorLabel;
    @FXML private Label categoryErrorLabel;
    @FXML private VBox aiSuggestionsBox;
    @FXML private VBox aiResultsBox;
    @FXML private ProgressIndicator aiLoadingIndicator;
    @FXML private Label aiStatusLabel;

    private final ServiceForumCategory categoryService = new ServiceForumCategory();
    private final ServiceForumTopic topicService = new ServiceForumTopic();
    private final ForumAiAssistantService aiAssistantService = new ForumAiAssistantService();
    private PauseTransition aiSuggestionsDelay;
    private int aiRequestVersion = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        List<ForumCategory> categories = categoryService.findAllActive();
        categoryComboBox.getItems().addAll(categories);

        setupAiSuggestions();
        clearErrors();
    }

    @FXML
    private void onSaveTopic() {
        clearErrors();
        boolean valid = true;

        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        String content = contentArea.getText() == null ? "" : contentArea.getText().trim();
        ForumCategory category = categoryComboBox.getValue();

        if (title.isEmpty() || title.length() < 5) {
            titleErrorLabel.setText("Title must be at least 5 characters");
            valid = false;
        }
        if (content.isEmpty()) {
            contentErrorLabel.setText("Content is required");
            valid = false;
        }
        if (category == null) {
            categoryErrorLabel.setText("Please select a category");
            valid = false;
        }

        if (!valid) return;

        int userId = UserSession.getCurrentUserId().orElse(0);
        if (userId == 0) {
            showAlert("You must be logged in to create a topic.");
            return;
        }

        User author = new User();
        author.setId(userId);

        Timestamp now = new Timestamp(System.currentTimeMillis());
        ForumTopic topic = new ForumTopic();
        topic.setTitle(title);
        topic.setContent(content);
        topic.setUser(author);
        topic.setForumCategory(category);
        topic.setStatus("open");
        topic.setIsPinned((byte) 0);
        topic.setViewCount(0);
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        topic.setLastActivityAt(now);

        topicService.add(topic);

        AppNavigator.showForumTopic(topic);
    }

    @FXML
    private void onCancel() {
        AppNavigator.showForum();
    }

    private void clearErrors() {
        titleErrorLabel.setText("");
        contentErrorLabel.setText("");
        categoryErrorLabel.setText("");
    }

    private void setupAiSuggestions() {
        if (aiSuggestionsBox == null) {
            return;
        }

        aiSuggestionsDelay = new PauseTransition(Duration.millis(1500));
        aiSuggestionsDelay.setOnFinished(event -> runAiSuggestionsSearch());

        titleField.textProperty().addListener((observable, oldValue, newValue) -> scheduleAiSuggestions());
        contentArea.textProperty().addListener((observable, oldValue, newValue) -> scheduleAiSuggestions());
        categoryComboBox.valueProperty().addListener((observable, oldValue, newValue) -> scheduleAiSuggestions());
        hideAiSuggestions();
    }

    private void scheduleAiSuggestions() {
        if (aiSuggestionsDelay == null) {
            return;
        }

        String question = buildQuestion();
        if (question.length() < 3) {
            hideAiSuggestions();
            return;
        }

        aiSuggestionsBox.setVisible(true);
        aiSuggestionsBox.setManaged(true);
        aiStatusLabel.setText("Waiting for you to pause...");
        aiSuggestionsDelay.playFromStart();
    }

    private void runAiSuggestionsSearch() {
        String question = buildQuestion();
        if (question.length() < 3) {
            hideAiSuggestions();
            return;
        }

        Integer categoryId = categoryComboBox.getValue() == null ? null : categoryComboBox.getValue().getId();
        int requestVersion = ++aiRequestVersion;
        setAiLoading(true, "Analyzing your question...");

        Task<ForumAiAssistantService.SimilarTopicsResult> task = new Task<>() {
            @Override
            protected ForumAiAssistantService.SimilarTopicsResult call() {
                return aiAssistantService.getSimilarTopics(question, categoryId);
            }
        };

        task.setOnSucceeded(event -> {
            if (requestVersion != aiRequestVersion) {
                return;
            }
            displayAiSuggestions(task.getValue());
        });
        task.setOnFailed(event -> {
            if (requestVersion != aiRequestVersion) {
                return;
            }
            setAiLoading(false, "AI suggestions unavailable.");
        });

        Thread thread = new Thread(task, "forum-ai-suggestions");
        thread.setDaemon(true);
        thread.start();
    }

    private void displayAiSuggestions(ForumAiAssistantService.SimilarTopicsResult result) {
        setAiLoading(false, result.fromCache() ? "From cache" : "");
        aiResultsBox.getChildren().clear();

        boolean hasAnswer = result.directAnswer() != null && !result.directAnswer().isBlank();
        boolean hasAdvice = result.aiAdvice() != null && !result.aiAdvice().isBlank();
        boolean hasTopics = result.topics() != null && !result.topics().isEmpty();

        if (!hasAnswer && !hasAdvice && !hasTopics) {
            hideAiSuggestions();
            return;
        }

        if (hasAnswer) {
            aiResultsBox.getChildren().add(createAiTextBlock("AI Answer", result.directAnswer(), "forum-ai-answer"));
        }
        if (hasAdvice && !hasAnswer) {
            aiResultsBox.getChildren().add(createAiTextBlock("AI Advice", result.aiAdvice(), "forum-ai-advice"));
        }
        if (hasTopics) {
            Label topicsTitle = new Label("Similar topics");
            topicsTitle.getStyleClass().add("forum-ai-section-title");
            aiResultsBox.getChildren().add(topicsTitle);
            for (ForumAiAssistantService.TopicSuggestion topic : result.topics()) {
                aiResultsBox.getChildren().add(createAiTopicRow(topic));
            }
        }

        aiSuggestionsBox.setVisible(true);
        aiSuggestionsBox.setManaged(true);
    }

    private VBox createAiTextBlock(String title, String body, String styleClass) {
        VBox box = new VBox(4);
        box.getStyleClass().add(styleClass);
        box.setPadding(new Insets(10));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("forum-ai-section-title");

        Label bodyLabel = new Label(body);
        bodyLabel.setWrapText(true);
        bodyLabel.getStyleClass().add("forum-ai-body");

        box.getChildren().addAll(titleLabel, bodyLabel);
        return box;
    }

    private HBox createAiTopicRow(ForumAiAssistantService.TopicSuggestion suggestion) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 10, 9, 10));
        row.getStyleClass().add("forum-ai-topic-row");
        row.setCursor(Cursor.HAND);

        VBox info = new VBox(2);
        Label title = new Label(suggestion.title());
        title.setWrapText(true);
        title.getStyleClass().add("forum-item-title");

        String category = suggestion.categoryName() == null || suggestion.categoryName().isBlank()
                ? "General"
                : suggestion.categoryName();
        Label meta = new Label(category + " | " + suggestion.commentsCount() + " answers");
        meta.getStyleClass().add("forum-item-subtitle");
        info.getChildren().addAll(title, meta);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label status = new Label(suggestion.hasAcceptedAnswers() ? "Solved" : suggestion.status());
        status.getStyleClass().addAll("forum-status-badge", suggestion.hasAcceptedAnswers() ? "status-solved" : "status-open");

        row.getChildren().addAll(info, status);
        row.setOnMouseClicked(event -> {
            ForumTopic topic = topicService.getById(suggestion.id());
            if (topic != null) {
                AppNavigator.showForumTopic(topic);
            }
        });
        return row;
    }

    private void setAiLoading(boolean loading, String statusText) {
        aiSuggestionsBox.setVisible(true);
        aiSuggestionsBox.setManaged(true);
        aiLoadingIndicator.setVisible(loading);
        aiLoadingIndicator.setManaged(loading);
        aiStatusLabel.setText(statusText == null ? "" : statusText);
    }

    private void hideAiSuggestions() {
        aiRequestVersion++;
        aiSuggestionsBox.setVisible(false);
        aiSuggestionsBox.setManaged(false);
        aiLoadingIndicator.setVisible(false);
        aiLoadingIndicator.setManaged(false);
        aiStatusLabel.setText("");
        aiResultsBox.getChildren().clear();
    }

    private String buildQuestion() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        String content = contentArea.getText() == null ? "" : contentArea.getText().trim();
        return (title + " " + content).trim();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Forum");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
