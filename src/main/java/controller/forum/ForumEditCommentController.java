package controller.forum;

import entities.forum.ForumComment;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import services.forum.ServiceForumComment;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;

public class ForumEditCommentController implements Initializable {

    @FXML private TextArea contentArea;
    @FXML private Label errorLabel;

    private final ServiceForumComment commentService = new ServiceForumComment();
    private ForumComment comment;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        errorLabel.setText("");
    }

    public void setComment(ForumComment comment) {
        this.comment = commentService.getById(comment.getId());
        if (this.comment == null) this.comment = comment;
        contentArea.setText(this.comment.getContent());
    }

    @FXML
    private void onSave() {
        errorLabel.setText("");
        String content = contentArea.getText().trim();

        if (content.isEmpty() || content.length() < 3) {
            errorLabel.setText("Comment must be at least 3 characters.");
            return;
        }

        comment.setContent(content);
        commentService.update(comment);

        // Navigate back to topic
        if (comment.getForumTopic() != null) {
            AppNavigator.showForumTopic(comment.getForumTopic());
        } else {
            AppNavigator.showForum();
        }
    }

    @FXML
    private void onCancel() {
        if (comment != null && comment.getForumTopic() != null) {
            AppNavigator.showForumTopic(comment.getForumTopic());
        } else {
            AppNavigator.showForum();
        }
    }
}
