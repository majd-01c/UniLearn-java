package mains;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class EvaluationModuleApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/evaluation/module-shell.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1280, 820);
        addStylesheet(scene, "/view/styles/evaluation.css");

        stage.setTitle("UniLearn - Evaluation Module");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.show();
    }

    private void addStylesheet(Scene scene, String classpathPath) {
        var resource = getClass().getResource(classpathPath);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
