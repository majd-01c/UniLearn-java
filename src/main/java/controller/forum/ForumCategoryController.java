package controller.forum;

import entities.forum.ForumCategory;
import entities.forum.ForumTopic;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import services.forum.ServiceForumTopic;
import util.AppNavigator;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;

public class ForumCategoryController implements Initializable {

    @FXML private Label categoryTitleLabel;
    @FXML private Label categoryDescLabel;
    @FXML private TextField searchField;
    @FXML private VBox topicsContainer;
    @FXML private Label pageInfoLabel;
    @FXML private Button prevButton;
    @FXML private Button nextButton;

    private final ServiceForumTopic topicService = new ServiceForumTopic();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

    private ForumCategory category;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 15;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    public void setCategory(ForumCategory category) {
        this.category = category;
        categoryTitleLabel.setText(category.getName());
        categoryDescLabel.setText(category.getDescription() != null ? category.getDescription() : "");
        currentPage = 1;
        loadTopics();
    }

    private void loadTopics() {
        String search = searchField.getText();
        if (search != null && search.isBlank()) search = null;

        List<ForumTopic> topics = topicService.findByCategory(category.getId(), search, currentPage, PAGE_SIZE);
        int total = topicService.countByCategory(category.getId(), search);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));

        topicsContainer.getChildren().clear();
        if (topics.isEmpty()) {
            Label empty = new Label("No topics found in this category.");
            empty.getStyleClass().add("forum-empty-label");
            empty.setPadding(new Insets(20));
            topicsContainer.getChildren().add(empty);
        } else {
            for (ForumTopic topic : topics) {
                topicsContainer.getChildren().add(createTopicRow(topic));
            }
        }

        pageInfoLabel.setText("Page " + currentPage + " of " + totalPages);
        prevButton.setDisable(currentPage <= 1);
        nextButton.setDisable(currentPage >= totalPages);
    }

    private HBox createTopicRow(ForumTopic topic) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.getStyleClass().add("forum-list-item");

        VBox badges = new VBox(4);
        badges.setAlignment(Pos.CENTER);
        badges.setMinWidth(70);
        if (topic.isPinnedTopic()) {
            Label pin = new Label("📌");
            badges.getChildren().add(pin);
        }
        Label statusBadge = new Label(topic.getTopicStatus().label());
        statusBadge.getStyleClass().addAll("forum-status-badge", "status-" + topic.getTopicStatus().getValue());
        badges.getChildren().add(statusBadge);

        VBox info = new VBox(2);
        Label titleLabel = new Label(topic.getTitle());
        titleLabel.getStyleClass().add("forum-item-title");
        String authorName = topicService.getAuthorName(topic.getUser().getId());
        String created = topic.getCreatedAt() != null ? dateFormat.format(topic.getCreatedAt()) : "";
        Label metaLabel = new Label("by " + authorName + " • " + created);
        metaLabel.getStyleClass().add("forum-item-subtitle");
        info.getChildren().addAll(titleLabel, metaLabel);
        HBox.setHgrow(info, Priority.ALWAYS);

        VBox counters = new VBox(2);
        counters.setAlignment(Pos.CENTER_RIGHT);
        int commentCount = topicService.getCommentCount(topic.getId());
        Label commLabel = new Label("💬 " + commentCount);
        Label viewLabel = new Label("👁 " + topic.getViewCount());
        commLabel.getStyleClass().add("forum-item-subtitle");
        viewLabel.getStyleClass().add("forum-item-subtitle");
        counters.getChildren().addAll(commLabel, viewLabel);

        row.getChildren().addAll(badges, info, counters);
        row.setOnMouseClicked(e -> AppNavigator.showForumTopic(topic));
        row.setCursor(javafx.scene.Cursor.HAND);
        return row;
    }

    @FXML
    private void onSearch() {
        currentPage = 1;
        loadTopics();
    }

    @FXML
    private void onPrevPage() {
        if (currentPage > 1) {
            currentPage--;
            loadTopics();
        }
    }

    @FXML
    private void onNextPage() {
        currentPage++;
        loadTopics();
    }

    @FXML
    private void onBackToForum() {
        AppNavigator.showForum();
    }

    @FXML
    private void onNewTopic() {
        AppNavigator.showForumNewTopic();
    }
}
