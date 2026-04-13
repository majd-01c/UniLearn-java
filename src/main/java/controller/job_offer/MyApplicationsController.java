package controller.job_offer;

import entities.User;
import entities.job_offer.JobApplication;
import entities.job_offer.JobOffer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import services.job_offer.ServiceJobApplication;
import util.AppNavigator;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MyApplicationsController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private ComboBox<String> filterStatus;

    @FXML
    private ListView<JobApplication> applicationListView;

    @FXML
    private Label noAppsLabel;

    private User currentUser;
    private ServiceJobApplication serviceJobApplication;
    private ObservableList<JobApplication> allApplications;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobApplication = new ServiceJobApplication();
        allApplications = FXCollections.observableArrayList();

        setupFilters();
        setupListView();
        filterStatus.setOnAction(e -> applyFilters());

        Platform.runLater(this::loadApplications);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loadApplications();
    }

    private void setupFilters() {
        filterStatus.setItems(FXCollections.observableArrayList(
                "All", "SUBMITTED", "REVIEWED", "ACCEPTED", "REJECTED"
        ));
        filterStatus.setValue("All");
    }

    private void setupListView() {
        applicationListView.setCellFactory(param -> new ApplicationListCell());
    }

    private void loadApplications() {
        Thread thread = new Thread(() -> {
            try {
                // TODO: Fetch only applications for current user
                List<JobApplication> applications = serviceJobApplication.getALL();
                List<JobApplication> userApps = applications.stream()
                        .filter(app -> app.getUser().getId().equals(currentUser.getId()))
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    allApplications.setAll(userApps);
                    applyFilters();
                    noAppsLabel.setVisible(userApps.isEmpty());
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error loading applications", e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void applyFilters() {
        String selectStatus = filterStatus.getValue();

        List<JobApplication> filtered = allApplications.stream()
                .filter(app -> "All".equals(selectStatus) || selectStatus.equals(app.getStatus()))
                .collect(Collectors.toList());

        applicationListView.setItems(FXCollections.observableArrayList(filtered));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void onBack() {
        AppNavigator.showJobOffers();
    }

    /**
     * Custom ListCell for JobApplications
     */
    private class ApplicationListCell extends ListCell<JobApplication> {
        @Override
        protected void updateItem(JobApplication app, boolean empty) {
            super.updateItem(app, empty);
            if (empty || app == null) {
                setGraphic(null);
                return;
            }

            VBox cellContent = new VBox(8);
            cellContent.setStyle("-fx-padding: 10; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");

            HBox titleBar = new HBox(10);
            titleBar.setAlignment(Pos.CENTER_LEFT);

            JobOffer offer = app.getJobOffer();
            String jobTitle = offer != null && offer.getTitle() != null ? offer.getTitle() : "Unknown Offer";
            Label titleLabel = new Label(jobTitle);
            titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

            Label statusLabel = new Label(app.getStatus() != null ? app.getStatus() : "");
            String statusColor = getStatusColor(app.getStatus());
            statusLabel.setStyle("-fx-padding: 2 8; -fx-background-color: " + statusColor + "; -fx-text-fill: white; -fx-border-radius: 3;");

            titleBar.getChildren().addAll(titleLabel, statusLabel);

            Label appliedLabel = new Label("Applied: " + (app.getCreatedAt() != null ? app.getCreatedAt().toString() : "Unknown"));
            appliedLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");

            if (app.getScore() != null) {
                Label scoreLabel = new Label("Score: " + app.getScore() + "/100");
                scoreLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
                cellContent.getChildren().addAll(titleBar, appliedLabel, scoreLabel);
            } else {
                cellContent.getChildren().addAll(titleBar, appliedLabel);
            }

            setGraphic(cellContent);
            setOnMouseClicked(event -> AppNavigator.showJobApplicationReview(app));
        }
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "ACCEPTED" -> "#4CAF50";
            case "SUBMITTED" -> "#2196F3";
            case "REVIEWED" -> "#FF9800";
            case "REJECTED" -> "#f44336";
            default -> "#9E9E9E";
        };
    }
}
