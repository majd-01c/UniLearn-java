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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import services.job_offer.ServiceJobApplication;
import util.AppNavigator;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MyApplicationsController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

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
        Label placeholder = new Label("No applications to display.");
        placeholder.getStyleClass().add("job-offer-empty-label");
        applicationListView.setPlaceholder(placeholder);
    }

    private void loadApplications() {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
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
                setText(null);
                setOnMouseClicked(null);
                getStyleClass().remove("job-offer-list-cell");
                return;
            }

            if (!getStyleClass().contains("job-offer-list-cell")) {
                getStyleClass().add("job-offer-list-cell");
            }

            VBox cellContent = new VBox(9);
            cellContent.getStyleClass().add("job-offer-card");

            HBox titleBar = new HBox(10);
            titleBar.setAlignment(Pos.CENTER_LEFT);

            JobOffer offer = app.getJobOffer();
            String jobTitle = offer != null && offer.getTitle() != null ? offer.getTitle() : "Unknown Offer";
            Label titleLabel = new Label(jobTitle);
            titleLabel.getStyleClass().add("job-offer-card-title");
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            Label statusLabel = new Label(app.getStatus() != null ? app.getStatus() : "");
            applyApplicationStatusStyle(statusLabel, app.getStatus());

            titleBar.getChildren().addAll(titleLabel, statusLabel);

            HBox metaBar = new HBox(14);
            String appliedAt = app.getCreatedAt() != null
                    ? app.getCreatedAt().toLocalDateTime().format(DATE_FORMATTER)
                    : "Unknown";
            Label appliedLabel = new Label("Applied: " + appliedAt);
            appliedLabel.getStyleClass().add("job-offer-card-meta");
            metaBar.getChildren().add(appliedLabel);

            if (app.getScore() != null) {
                Label scoreLabel = new Label("Score: " + app.getScore() + "/100");
                scoreLabel.getStyleClass().add("job-offer-score-badge");
                metaBar.getChildren().add(scoreLabel);
                cellContent.getChildren().addAll(titleBar, metaBar);
            } else {
                cellContent.getChildren().addAll(titleBar, metaBar);
            }

            setGraphic(cellContent);
            setText(null);
            setOnMouseClicked(event -> AppNavigator.showJobApplicationReview(app));
        }
    }

    private void applyApplicationStatusStyle(Label statusLabel, String status) {
        statusLabel.getStyleClass().add("job-offer-status-chip");
        if (status == null) {
            return;
        }
        String normalized = status.trim().toUpperCase();
        switch (normalized) {
            case "ACCEPTED" -> statusLabel.getStyleClass().add("job-offer-status-active");
            case "SUBMITTED" -> statusLabel.getStyleClass().add("job-offer-status-info");
            case "REVIEWED" -> statusLabel.getStyleClass().add("job-offer-status-pending");
            case "REJECTED" -> statusLabel.getStyleClass().add("job-offer-status-rejected");
            default -> {
            }
        }
    }
}
