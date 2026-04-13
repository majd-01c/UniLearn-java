package controller.lms;

import dto.lms.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import service.lms.ClassDeliveryService;
import util.AppNavigator;

import java.net.URL;
import java.util.*;

public class StudentCourseViewController implements Initializable {
    @FXML private Label breadcrumb, courseTitle, emptyLabel;
    @FXML private VBox contentContainer;

    private final ClassDeliveryService cdSvc = new ClassDeliveryService();

    @Override public void initialize(URL u, ResourceBundle r) {}

    public void setClasseCourse(CourseRowDto cc) {
        breadcrumb.setText(cc.getTitle());
        courseTitle.setText(cc.getTitle());

        List<ContenuRowDto> visible = cdSvc.getVisibleContenuForClasseCourseDto(cc.getClasseCourseId());
        if (visible.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }

        for (int i = 0; i < visible.size(); i++) {
            ContenuRowDto c = visible.get(i);
            int idx = i;

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("lms-card");
            row.setPadding(new javafx.geometry.Insets(12));

            Label num = new Label(String.valueOf(i + 1));
            num.getStyleClass().add("stat-value");
            num.setMinWidth(40);
            num.setAlignment(Pos.CENTER);

            VBox info = new VBox(2);
            Label title = new Label(c.getTitle());
            title.getStyleClass().add("card-title");

            Label type = new Label(c.getType());
            type.getStyleClass().addAll("badge", "badge-" + c.getType().toLowerCase());

            info.getChildren().addAll(title, type);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button viewBtn = new Button("View ▶");
            viewBtn.getStyleClass().add("primary-button");

            viewBtn.setOnAction(e -> AppNavigator.showStudentContenuView(c, visible, idx));

            row.getChildren().addAll(num, info, viewBtn);
            contentContainer.getChildren().add(row);
        }
    }

    @FXML private void onBackToLearning() {
        AppNavigator.showStudentLearning();
    }
}
