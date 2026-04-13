package controller.forum;

import entities.User;
import entities.forum.ForumCategory;
import entities.forum.ForumTopic;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import security.UserSession;
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

    private final ServiceForumCategory categoryService = new ServiceForumCategory();
    private final ServiceForumTopic topicService = new ServiceForumTopic();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        List<ForumCategory> categories = categoryService.findAllActive();
        categoryComboBox.getItems().addAll(categories);

        clearErrors();
    }

    @FXML
    private void onSaveTopic() {
        clearErrors();
        boolean valid = true;

        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();
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

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Forum");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
