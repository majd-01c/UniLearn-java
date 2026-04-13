package mains;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import service.ThemeManager;

public class MainFx extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/app/app-shell.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1280, 800);
        addStylesheet(scene, "/view/styles/desktop-tokens.css");
        addStylesheet(scene, "/view/styles/desktop-shell.css");
        addStylesheet(scene, "/view/styles/desktop-components.css");
        addStylesheet(scene, "/view/styles/desktop-admin.css");
        addStylesheet(scene, "/view/styles/desktop-frontoffice.css");
        addStylesheet(scene, "/view/styles/desktop-animations.css");
        addStylesheet(scene, "/view/styles/unilearn-desktop.css");

        ThemeManager.getInstance().applySavedTheme(scene);

        stage.setTitle("UniLearn Desktop");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
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
