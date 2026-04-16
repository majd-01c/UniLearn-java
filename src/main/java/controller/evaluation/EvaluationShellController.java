package controller.evaluation;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import security.UserSession;

import java.io.IOException;

public class EvaluationShellController {

    @FXML
    private StackPane roleContentHost;

    private String initialStudentSection = "GRADES";
    private String resolvedRole = "STUDENT";
    private boolean initialized;

    public void setInitialStudentSection(String sectionKey) {
        if (sectionKey == null || sectionKey.isBlank()) {
            return;
        }
        initialStudentSection = sectionKey.trim().toUpperCase();
        if (initialized && "STUDENT".equals(resolvedRole)) {
            openRoleForSession(resolvedRole);
        }
    }

    @FXML
    public void initialize() {
        String currentRole = UserSession.getCurrentUserRole();
        resolvedRole = currentRole == null ? "STUDENT" : currentRole.trim().toUpperCase();
        initialized = true;
        openRoleForSession(resolvedRole);
    }

    private void openRoleForSession(String normalizedRole) {
        String viewPath;
        if ("ADMIN".equals(normalizedRole)) {
            viewPath = "/view/evaluation/admin/module.fxml";
        } else if ("TEACHER".equals(normalizedRole) || "TRAINER".equals(normalizedRole)) {
            viewPath = "/view/evaluation/teacher/module.fxml";
        } else {
            viewPath = "/view/evaluation/student/module.fxml";
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(viewPath));
            Node view = loader.load();

            if ("STUDENT".equals(normalizedRole) && loader.getController() instanceof EvaluationStudentController studentController) {
                studentController.openSection(initialStudentSection);
            }

            if (view instanceof Parent parent) {
                String cssPath = getClass().getResource("/view/styles/evaluation.css").toExternalForm();
                if (!parent.getStylesheets().contains(cssPath)) {
                    parent.getStylesheets().add(cssPath);
                }
            }
            roleContentHost.getChildren().setAll(view);
        } catch (IOException exception) {
            Label error = new Label("Unable to load role view: " + exception.getMessage());
            roleContentHost.getChildren().setAll(error);
        }
    }
}
