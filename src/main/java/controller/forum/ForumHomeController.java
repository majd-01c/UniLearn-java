package controller.forum;

import entities.User;
import entities.forum.ForumCategory;
import entities.forum.ForumTopic;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import security.UserSession;
import services.forum.ServiceForumCategory;
import services.forum.ServiceForumTopic;
import util.AppNavigator;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;

public class ForumHomeController implements Initializable {

    @FXML private VBox categoriesContainer;
    @FXML private VBox recentTopicsContainer;
    @FXML private VBox unansweredContainer;
    @FXML private Label statCategoriesLabel;
    @FXML private Label statTopicsLabel;
    @FXML private Label statUnansweredLabel;
    @FXML private Button newTopicButton;
    @FXML private VBox adminToolsBox;

    private final ServiceForumCategory categoryService = new ServiceForumCategory();
    private final ServiceForumTopic topicService = new ServiceForumTopic();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadData();
    }

    private void loadData() {
        List<ForumCategory> categories = categoryService.findAllActive();
        List<ForumTopic> recentTopics = topicService.findRecent(5);
        List<ForumTopic> unansweredTopics = topicService.findUnanswered(5);

        // Stats
        statCategoriesLabel.setText(String.valueOf(categories.size()));
        statTopicsLabel.setText(String.valueOf(recentTopics.size()));
        statUnansweredLabel.setText(String.valueOf(unansweredTopics.size()));

        // Admin tools visibility
        boolean isAdmin = isCurrentUserAdmin();
        adminToolsBox.setVisible(isAdmin);
        adminToolsBox.setManaged(isAdmin);

        // Render categories
        categoriesContainer.getChildren().clear();
        if (categories.isEmpty()) {
            categoriesContainer.getChildren().add(createEmptyLabel("No categories available yet."));
        } else {
            for (ForumCategory cat : categories) {
                categoriesContainer.getChildren().add(createCategoryCard(cat));
            }
        }

        // Render recent topics
        recentTopicsContainer.getChildren().clear();
        if (recentTopics.isEmpty()) {
            recentTopicsContainer.getChildren().add(createEmptyLabel("No discussions yet. Be the first to start one!"));
        } else {
            for (ForumTopic topic : recentTopics) {
                recentTopicsContainer.getChildren().add(createTopicRow(topic));
            }
        }

        // Render unanswered
        unansweredContainer.getChildren().clear();
        if (unansweredTopics.isEmpty()) {
            unansweredContainer.getChildren().add(createEmptyLabel("All questions answered!"));
        } else {
            for (ForumTopic topic : unansweredTopics) {
                unansweredContainer.getChildren().add(createUnansweredRow(topic));
            }
        }
    }

    private HBox createCategoryCard(ForumCategory cat) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));
        row.getStyleClass().add("forum-list-item");

        // Icon
        Label iconLabel = new Label(cat.getIcon() != null ? cat.getIcon() : "💬");
        iconLabel.getStyleClass().add("forum-category-icon");

        // Info
        VBox info = new VBox(2);
        Label nameLabel = new Label(cat.getName());
        nameLabel.getStyleClass().add("forum-item-title");
        info.getChildren().add(nameLabel);

        if (cat.getDescription() != null && !cat.getDescription().isEmpty()) {
            Label descLabel = new Label(cat.getDescription());
            descLabel.getStyleClass().add("forum-item-subtitle");
            descLabel.setWrapText(true);
            info.getChildren().add(descLabel);
        }
        HBox.setHgrow(info, Priority.ALWAYS);

        // Stats
        VBox stats = new VBox(2);
        stats.setAlignment(Pos.CENTER_RIGHT);
        int topicCount = categoryService.getTopicsCount(cat.getId());
        int commentCount = categoryService.getCommentsCount(cat.getId());
        Label topicCountLabel = new Label(topicCount + " topics");
        topicCountLabel.getStyleClass().add("forum-badge-primary");
        Label commentCountLabel = new Label(commentCount + " comments");
        commentCountLabel.getStyleClass().add("forum-item-subtitle");
        stats.getChildren().addAll(topicCountLabel, commentCountLabel);

        row.getChildren().addAll(iconLabel, info, stats);
        row.setOnMouseClicked(e -> AppNavigator.showForumCategory(cat));
        row.setCursor(javafx.scene.Cursor.HAND);
        return row;
    }

    private HBox createTopicRow(ForumTopic topic) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.getStyleClass().add("forum-list-item");

        VBox badges = new VBox(4);
        badges.setAlignment(Pos.CENTER);
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
        String categoryName = topicService.getCategoryName(topic.getForumCategory().getId());
        String created = topic.getCreatedAt() != null ? dateFormat.format(topic.getCreatedAt()) : "";
        Label metaLabel = new Label("by " + authorName + " in " + categoryName + " • " + created);
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

    private HBox createUnansweredRow(ForumTopic topic) {
        HBox row = new HBox(8);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.getStyleClass().add("forum-list-item");
        row.setAlignment(Pos.CENTER_LEFT);

        String title = topic.getTitle();
        if (title.length() > 40) title = title.substring(0, 40) + "...";
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("forum-item-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        String created = topic.getCreatedAt() != null ? new SimpleDateFormat("MMM dd").format(topic.getCreatedAt()) : "";
        Label dateLabel = new Label(created);
        dateLabel.getStyleClass().add("forum-item-subtitle");

        row.getChildren().addAll(titleLabel, dateLabel);
        row.setOnMouseClicked(e -> AppNavigator.showForumTopic(topic));
        row.setCursor(javafx.scene.Cursor.HAND);
        return row;
    }

    private Label createEmptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("forum-empty-label");
        label.setPadding(new Insets(20));
        return label;
    }

    private boolean isCurrentUserAdmin() {
        return UserSession.getCurrentUserId().map(id -> {
            String sql = "SELECT role FROM user WHERE id = ?";
            try (var stmt = Utils.MyDatabase.getInstance().getConnection().prepareStatement(sql)) {
                stmt.setInt(1, id);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    String role = rs.getString("role");
                    return role != null && role.toUpperCase().contains("ADMIN");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }).orElse(false);
    }

    private boolean isCurrentUserStaff() {
        return UserSession.getCurrentUserId().map(id -> {
            String sql = "SELECT role FROM user WHERE id = ?";
            try (var stmt = Utils.MyDatabase.getInstance().getConnection().prepareStatement(sql)) {
                stmt.setInt(1, id);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    String role = rs.getString("role");
                    if (role == null) return false;
                    role = role.toUpperCase();
                    return role.contains("ADMIN") || role.contains("TEACHER") || role.contains("TRAINER");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }).orElse(false);
    }

    @FXML
    private void onNewTopic() {
        AppNavigator.showForumNewTopic();
    }

    @FXML
    private void onManageCategories() {
        AppNavigator.showForumAdminCategories();
    }
}
