package controller.lms;

import dto.lms.*;
import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import service.lms.ClassDeliveryService;
import util.AppNavigator;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class StudentClasseViewController implements Initializable {
    @FXML private Label breadcrumb, classTitle, classInfo, emptyLabel;
    @FXML private VBox modulesContainer;

    private final ClassDeliveryService cdSvc = new ClassDeliveryService();

    @Override public void initialize(URL u, ResourceBundle r) {}

    public void init(StudentClasseRowDto c, User student) {
        breadcrumb.setText(c.getClasseName());
        classTitle.setText(c.getClasseName());
        classInfo.setText(c.getLevel() + " • " + c.getSpecialty() + " • Program: " + c.getProgramName());

        List<ModuleRowDto> modules = cdSvc.getModulesForClasseDto(c.getClasseId());
        if (modules.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }

        for (ModuleRowDto cm : modules) {
            VBox card = new VBox(8);
            card.getStyleClass().add("lms-card");
            card.setPadding(new Insets(14));

            Label name = new Label(cm.getModuleName());
            name.getStyleClass().add("card-title");

            Label dur = new Label("Duration: " + cm.getDurationLabel());
            dur.getStyleClass().add("card-text");

            // Show visible courses directly
            List<CourseRowDto> courses = cdSvc.getVisibleCoursesForClasseModuleDto(cm.getClasseModuleId());
            VBox courseList = new VBox(4);

            for (CourseRowDto cc : courses) {
                Button courseBtn = new Button("▶ " + cc.getTitle());
                courseBtn.getStyleClass().add("ghost-button");
                courseBtn.setMaxWidth(Double.MAX_VALUE);
                courseBtn.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                courseBtn.setOnAction(e -> AppNavigator.showStudentCourseView(cc));
                courseList.getChildren().add(courseBtn);
            }

            if (courses.isEmpty()) {
                Label noCourses = new Label("No courses available yet.");
                noCourses.getStyleClass().add("card-text");
                courseList.getChildren().add(noCourses);
            }

            card.getChildren().addAll(name, dur, new Separator(), courseList);
            modulesContainer.getChildren().add(card);
        }
    }

    @FXML private void onBackToLearning() {
        AppNavigator.showStudentLearning();
    }
}
