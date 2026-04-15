package controller.forum;

import entities.forum.ForumCategory;
import entities.forum.ForumTopic;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import services.forum.ServiceForumCategory;
import services.forum.ServiceForumTopic;
import util.AppNavigator;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ForumEditTopicController implements Initializable {

    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private ComboBox<ForumCategory> categoryComboBox;
    @FXML private Label titleErrorLabel;
    @FXML private Label contentErrorLabel;

    private final ServiceForumCategory categoryService = new ServiceForumCategory();
    private final ServiceForumTopic topicService = new ServiceForumTopic();

    private ForumTopic topic;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        List<ForumCategory> categories = categoryService.findAllActive();
        categoryComboBox.getItems().addAll(categories);
        titleErrorLabel.setText("");
        contentErrorLabel.setText("");
    }

    public void setTopic(ForumTopic topic) {
        this.topic = topicService.getById(topic.getId());
        if (this.topic == null) this.topic = topic;

        titleField.setText(this.topic.getTitle());
        contentArea.setText(this.topic.getContent());

        // Select current category
        ForumCategory current = categoryService.getById(this.topic.getForumCategory().getId());
        if (current != null) {
            for (ForumCategory cat : categoryComboBox.getItems()) {
                if (cat.getId() == current.getId()) {
                    categoryComboBox.setValue(cat);
                    break;
                }
            }
        }
    }

    @FXML
    private void onSaveTopic() {
        titleErrorLabel.setText("");
        contentErrorLabel.setText("");
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

        if (!valid) return;

        topic.setTitle(title);
        topic.setContent(content);
        if (category != null) {
            topic.setForumCategory(category);
        }
        topicService.update(topic);

        AppNavigator.showForumTopic(topic);
    }

    @FXML
    private void onCancel() {
        if (topic != null) {
            AppNavigator.showForumTopic(topic);
        } else {
            AppNavigator.showForum();
        }
    }
}
