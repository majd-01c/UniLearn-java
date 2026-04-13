package util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.function.Consumer;

public class ViewRouter {

    public record LoadedView(Parent root, Object controller) {
    }

    private StackPane contentHost;

    public ViewRouter(StackPane contentHost) {
        this.contentHost = contentHost;
    }

    public void setContentHost(StackPane contentHost) {
        this.contentHost = contentHost;
    }

    public LoadedView navigate(String fxmlPath, Consumer<Object> controllerInitializer) throws IOException {
        if (contentHost == null) {
            throw new IllegalStateException("Content host is not configured");
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent content = loader.load();

        Object controller = loader.getController();
        if (controllerInitializer != null) {
            controllerInitializer.accept(controller);
        }

        contentHost.getChildren().setAll(content);
        return new LoadedView(content, controller);
    }
}
