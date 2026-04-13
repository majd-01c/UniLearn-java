package controller.job_offer;

import entities.User;
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
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.sql.Timestamp;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class JobOfferListController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> filterStatus;

    @FXML
    private ComboBox<String> filterType;

    @FXML
    private Button createJobOfferButton;

    @FXML
    private ListView<JobOffer> jobOffersListView;

    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private ObservableList<JobOffer> allJobOffers;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobOffer = new ServiceJobOffer();
        allJobOffers = FXCollections.observableArrayList();

        setupFilters();
        setupListView();
        setupSearchAndFilters();

        Platform.runLater(this::loadJobOffers);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        setupUI();
        loadJobOffers();
    }

    private void setupUI() {
        if (currentUser == null) {
            createJobOfferButton.setDisable(true);
            return;
        }

        // Only partners and admins can create job offers
        boolean canCreate = !RoleGuard.isStudent(currentUser);
        createJobOfferButton.setDisable(!canCreate);
    }

    private void setupFilters() {
        filterStatus.setItems(FXCollections.observableArrayList(
                "All", "PENDING", "ACTIVE", "REJECTED", "CLOSED"
        ));
        filterStatus.setValue("All");

        filterType.setItems(FXCollections.observableArrayList(
                "All", "INTERNSHIP", "APPRENTICESHIP", "JOB"
        ));
        filterType.setValue("All");
    }

    private void setupListView() {
        jobOffersListView.setCellFactory(param -> new JobOfferListCell());
    }

    private void setupSearchAndFilters() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        filterStatus.setOnAction(e -> applyFilters());
        filterType.setOnAction(e -> applyFilters());
    }

    private void loadJobOffers() {
        Thread thread = new Thread(() -> {
            try {
                List<JobOffer> offers = serviceJobOffer.getALL();
                Platform.runLater(() -> {
                    allJobOffers.setAll(offers);
                    applyFilters();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Error loading job offers", e.getMessage()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase().trim();
        String selectStatus = filterStatus.getValue();
        String selectType = filterType.getValue();

        List<JobOffer> filtered = allJobOffers.stream()
                .filter(offer -> {
                    if (!searchText.isEmpty()) {
                        return offer.getTitle().toLowerCase().contains(searchText) ||
                                (offer.getDescription() != null && offer.getDescription().toLowerCase().contains(searchText));
                    }
                    return true;
                })
                .filter(offer -> "All".equals(selectStatus) || selectStatus.equals(offer.getStatus()))
                .filter(offer -> "All".equals(selectType) || selectType.equals(offer.getType()))
                .collect(Collectors.toList());

        jobOffersListView.setItems(FXCollections.observableArrayList(filtered));
    }

    @FXML
    private void onCreateJobOffer() {
        AppNavigator.showJobOfferForm(null);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Custom ListCell to display JobOffer with click handling
     */
    private class JobOfferListCell extends ListCell<JobOffer> {
        @Override
        protected void updateItem(JobOffer offer, boolean empty) {
            super.updateItem(offer, empty);
            if (empty || offer == null) {
                setGraphic(null);
                return;
            }

            VBox cellContent = new VBox(8);
            cellContent.setStyle("-fx-padding: 10; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");

            HBox titleBar = new HBox(10);
            titleBar.setAlignment(Pos.CENTER_LEFT);

            Label titleLabel = new Label(offer.getTitle() != null ? offer.getTitle() : "Untitled");
            titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

            Label typeLabel = new Label(offer.getType() != null ? offer.getType() : "");
            typeLabel.setStyle("-fx-padding: 2 8; -fx-background-color: #e0e0e0; -fx-border-radius: 3;");

            Label statusLabel = new Label(offer.getStatus() != null ? offer.getStatus() : "");
            String statusColor = getStatusColor(offer.getStatus());
            statusLabel.setStyle("-fx-padding: 2 8; -fx-background-color: " + statusColor + "; -fx-text-fill: white; -fx-border-radius: 3;");

            titleBar.getChildren().addAll(titleLabel, typeLabel, statusLabel);

            Label locationLabel = new Label((offer.getLocation() != null ? "📍 " + offer.getLocation() : "Location not specified"));
            locationLabel.setStyle("-fx-text-fill: #666;");

            Label descLabel = new Label(offer.getDescription() != null ? offer.getDescription() : "No description");
            descLabel.setStyle("-fx-text-fill: #444; -fx-wrap-text: true;");
            descLabel.setWrapText(true);

            cellContent.getChildren().addAll(titleBar, locationLabel, descLabel);

            setGraphic(cellContent);
            setOnMouseClicked(event -> AppNavigator.showJobOfferDetail(offer));
        }
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "ACTIVE" -> "#4CAF50";
            case "PENDING" -> "#FF9800";
            case "CLOSED" -> "#9E9E9E";
            case "REJECTED" -> "#f44336";
            default -> "#2196F3";
        };
    }
}
