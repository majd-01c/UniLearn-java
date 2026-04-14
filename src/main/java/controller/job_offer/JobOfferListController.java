package controller.job_offer;

import entities.User;
import entities.job_offer.JobOffer;
import entities.job_offer.JobOfferStatus;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class JobOfferListController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

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
    private Button myApplicationsButton;

    @FXML
    private Button partnerApplicationsButton;

    @FXML
    private Button myOffersButton;

    @FXML
    private Button otherOffersButton;

    @FXML
    private VBox adminModerationPanel;

    @FXML
    private Label adminPendingCountLabel;

    @FXML
    private Label adminActiveCountLabel;

    @FXML
    private Label adminRejectedCountLabel;

    @FXML
    private Label adminClosedCountLabel;

    @FXML
    private ScrollPane jobOffersScrollPane;

    @FXML
    private TilePane jobOffersGrid;

    @FXML
    private Label emptyStateLabel;

    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private ObservableList<JobOffer> allJobOffers;
    private PartnerOfferScope partnerOfferScope = PartnerOfferScope.ALL;

    private enum PartnerOfferScope {
        ALL,
        MY_OFFERS,
        OTHER_OFFERS
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobOffer = new ServiceJobOffer();
        allJobOffers = FXCollections.observableArrayList();

        setupFilters();
        setupGrid();
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
            createJobOfferButton.setVisible(false);
            createJobOfferButton.setManaged(false);
            if (myApplicationsButton != null) {
                myApplicationsButton.setVisible(false);
                myApplicationsButton.setManaged(false);
            }
            if (partnerApplicationsButton != null) {
                partnerApplicationsButton.setVisible(false);
                partnerApplicationsButton.setManaged(false);
            }
            if (myOffersButton != null) {
                myOffersButton.setVisible(false);
                myOffersButton.setManaged(false);
            }
            if (otherOffersButton != null) {
                otherOffersButton.setVisible(false);
                otherOffersButton.setManaged(false);
            }
            if (adminModerationPanel != null) {
                adminModerationPanel.setVisible(false);
                adminModerationPanel.setManaged(false);
            }
            return;
        }

        // Only partners and admins can create job offers
        boolean canCreate = !RoleGuard.isStudent(currentUser);
        createJobOfferButton.setDisable(!canCreate);
        createJobOfferButton.setVisible(canCreate);
        createJobOfferButton.setManaged(canCreate);

        boolean isStudent = RoleGuard.isStudent(currentUser);
        if (myApplicationsButton != null) {
            myApplicationsButton.setVisible(isStudent);
            myApplicationsButton.setManaged(isStudent);
        }

        boolean isPartner = !isStudent && !RoleGuard.isAdmin(currentUser);
        if (partnerApplicationsButton != null) {
            partnerApplicationsButton.setVisible(isPartner);
            partnerApplicationsButton.setManaged(isPartner);
        }
        if (myOffersButton != null) {
            myOffersButton.setVisible(isPartner);
            myOffersButton.setManaged(isPartner);
        }
        if (otherOffersButton != null) {
            otherOffersButton.setVisible(isPartner);
            otherOffersButton.setManaged(isPartner);
        }
        if (!isPartner) {
            partnerOfferScope = PartnerOfferScope.ALL;
        }

        boolean isAdmin = RoleGuard.isAdmin(currentUser);
        if (adminModerationPanel != null) {
            adminModerationPanel.setVisible(isAdmin);
            adminModerationPanel.setManaged(isAdmin);
        }

        if (isAdmin) {
            filterStatus.setValue("PENDING");
            // Hide filter and search for admin dashboard

            filterStatus.setVisible(false);
            filterStatus.setManaged(false);
        } else {
            filterStatus.setVisible(true);
            filterStatus.setManaged(true);
        }
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

    private void setupGrid() {
        if (jobOffersGrid != null) {
            jobOffersGrid.setPrefTileWidth(300);
            jobOffersGrid.setTileAlignment(Pos.TOP_LEFT);
        }
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
                    updateAdminModerationPanel();
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
        boolean isPartner = currentUser != null && !RoleGuard.isStudent(currentUser) && !RoleGuard.isAdmin(currentUser);

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
                .filter(offer -> {
                    if (!isPartner) {
                        return true;
                    }
                    if (offer == null || offer.getUser() == null || offer.getUser().getId() == null || currentUser == null) {
                        return false;
                    }

                    boolean isOwner = offer.getUser().getId().equals(currentUser.getId());
                    return switch (partnerOfferScope) {
                        case MY_OFFERS -> isOwner;
                        case OTHER_OFFERS -> !isOwner;
                        default -> true;
                    };
                })
                .collect(Collectors.toList());

        renderOfferCards(filtered);
    }

    private void updateAdminModerationPanel() {
        if (!RoleGuard.isAdmin(currentUser)) {
            return;
        }

        long pendingCount = countOffersByStatus("PENDING");
        long activeCount = countOffersByStatus("ACTIVE");
        long rejectedCount = countOffersByStatus("REJECTED");
        long closedCount = countOffersByStatus("CLOSED");

        adminPendingCountLabel.setText(String.valueOf(pendingCount));
        adminActiveCountLabel.setText(String.valueOf(activeCount));
        adminRejectedCountLabel.setText(String.valueOf(rejectedCount));
        adminClosedCountLabel.setText(String.valueOf(closedCount));
    }

    private long countOffersByStatus(String status) {
        return allJobOffers.stream()
                .filter(offer -> status.equalsIgnoreCase(valueOrDefault(offer.getStatus(), "")))
                .count();
    }

    @FXML
    private void onCreateJobOffer() {
        AppNavigator.showJobOfferForm(null);
    }

    @FXML
    private void onOpenMyApplications() {
        AppNavigator.showMyJobApplications();
    }

    @FXML
    private void onOpenPartnerApplications() {
        AppNavigator.showPartnerApplications();
    }

    @FXML
    private void onShowMyOffers() {
        partnerOfferScope = PartnerOfferScope.MY_OFFERS;
        applyFilters();
    }

    @FXML
    private void onShowOtherOffers() {
        partnerOfferScope = PartnerOfferScope.OTHER_OFFERS;
        applyFilters();
    }

    @FXML
    private void onAdminFilterAll() {
        filterStatus.setValue("All");
        applyFilters();
    }

    @FXML
    private void onAdminFilterPending() {
        filterStatus.setValue("PENDING");
        applyFilters();
    }

    @FXML
    private void onAdminFilterActive() {
        filterStatus.setValue("ACTIVE");
        applyFilters();
    }

    @FXML
    private void onAdminFilterRejected() {
        filterStatus.setValue("REJECTED");
        applyFilters();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void renderOfferCards(List<JobOffer> offers) {
        jobOffersGrid.getChildren().clear();

        boolean empty = offers == null || offers.isEmpty();
        emptyStateLabel.setManaged(empty);
        emptyStateLabel.setVisible(empty);

        if (empty) {
            return;
        }

        offers.forEach(offer -> jobOffersGrid.getChildren().add(buildOfferCard(offer)));
    }

    private VBox buildOfferCard(JobOffer offer) {
        VBox card = new VBox(12);
        card.getStyleClass().addAll("job-offer-card", "job-offer-grid-card");
        card.setPadding(new Insets(16));
        card.setFillWidth(true);
        card.setOnMouseClicked(event -> AppNavigator.showJobOfferDetail(offer));

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label(valueOrDefault(offer.getType(), "UNKNOWN"));
        typeLabel.getStyleClass().add("job-offer-chip");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusLabel = new Label(valueOrDefault(offer.getStatus(), "PENDING"));
        applyStatusChipStyle(statusLabel, offer.getStatus());

        topRow.getChildren().addAll(typeLabel, spacer, statusLabel);

        Label titleLabel = new Label(valueOrDefault(offer.getTitle(), "Untitled Position"));
        titleLabel.getStyleClass().add("job-offer-card-title");
        titleLabel.setWrapText(true);

        Label locationLabel = new Label("📍 " + valueOrDefault(offer.getLocation(), "Location not specified"));
        locationLabel.getStyleClass().add("job-offer-card-meta");

        Label descLabel = new Label(valueOrDefault(offer.getDescription(), "No description provided yet."));
        descLabel.getStyleClass().add("job-offer-card-description");
        descLabel.setWrapText(true);
        descLabel.setPrefHeight(66);
        descLabel.setMinHeight(66);

        VBox metaStack = new VBox(8);

        Label publishedLabel = new Label("🗓 " + formatPublishedDate(offer));
        publishedLabel.getStyleClass().add("job-offer-card-meta");

        metaStack.getChildren().addAll(locationLabel, publishedLabel);

        Button viewButton = new Button("View Details");
        viewButton.getStyleClass().addAll("primary-button", "job-offer-card-button");
        viewButton.setMaxWidth(Double.MAX_VALUE);
        viewButton.setOnAction(event -> {
            event.consume();
            AppNavigator.showJobOfferDetail(offer);
        });

        VBox actionsBox = new VBox(8);
        actionsBox.getChildren().add(viewButton);

        if (RoleGuard.isAdmin(currentUser)) {
            actionsBox.getChildren().add(buildAdminActions(offer));
        } else if (canPartnerCloseOffer(offer)) {
            actionsBox.getChildren().add(buildPartnerActions(offer));
        }

        card.getChildren().addAll(topRow, titleLabel, metaStack, descLabel, actionsBox);
        return card;
    }

    private HBox buildPartnerActions(JobOffer offer) {
        HBox partnerActions = new HBox(8);

        JobOfferStatus currentStatus = JobOfferStatus.fromString(offer.getStatus());
        if (currentStatus == JobOfferStatus.CLOSED) {
            Button reopenButton = new Button("Reopen Offer");
            reopenButton.getStyleClass().addAll("job-offer-card-button", "job-offer-neutral-button");
            reopenButton.setOnAction(event -> {
                event.consume();
                reopenOwnOffer(offer);
            });
            partnerActions.getChildren().add(reopenButton);
        } else {
            Button closeButton = new Button("Close Offer");
            closeButton.getStyleClass().addAll("job-offer-card-button", "job-offer-close-button");
            closeButton.setOnAction(event -> {
                event.consume();
                closeOwnOffer(offer);
            });
            partnerActions.getChildren().add(closeButton);
        }

        return partnerActions;
    }

    private VBox buildAdminActions(JobOffer offer) {
        VBox adminActions = new VBox(8);
        adminActions.getStyleClass().add("job-offer-admin-actions");

        Label adminLabel = new Label("Admin actions");
        adminLabel.getStyleClass().add("job-offer-admin-label");

        HBox primaryActions = new HBox(8);
        List<Button> primaryButtons = new ArrayList<>();
        JobOfferStatus currentStatus = JobOfferStatus.fromString(offer.getStatus());

        if (currentStatus == JobOfferStatus.PENDING) {
            primaryButtons.add(createAdminButton("Approve", "job-offer-approve-button",
                    event -> updateOfferStatus(offer, JobOfferStatus.ACTIVE)));
            primaryButtons.add(createAdminButton("Reject", "job-offer-reject-button",
                    event -> updateOfferStatus(offer, JobOfferStatus.REJECTED)));
        } else if (currentStatus == JobOfferStatus.ACTIVE) {
            primaryButtons.add(createAdminButton("Mark Pending", "job-offer-neutral-button",
                event -> updateOfferStatus(offer, JobOfferStatus.PENDING)));
            primaryButtons.add(createAdminButton("Close", "job-offer-close-button",
                    event -> updateOfferStatus(offer, JobOfferStatus.CLOSED)));
            primaryButtons.add(createAdminButton("Reject", "job-offer-reject-button",
                event -> updateOfferStatus(offer, JobOfferStatus.REJECTED)));
        } else if (currentStatus == JobOfferStatus.REJECTED) {
            primaryButtons.add(createAdminButton("Mark Pending", "job-offer-neutral-button",
                event -> updateOfferStatus(offer, JobOfferStatus.PENDING)));
            primaryButtons.add(createAdminButton("Approve", "job-offer-approve-button",
                event -> updateOfferStatus(offer, JobOfferStatus.ACTIVE)));
        } else if (currentStatus == JobOfferStatus.CLOSED) {
            primaryButtons.add(createAdminButton("Reopen", "job-offer-neutral-button",
                    event -> updateOfferStatus(offer, JobOfferStatus.PENDING)));
        }

        primaryActions.getChildren().addAll(primaryButtons);

        HBox secondaryActions = new HBox(8);
        Button editButton = createAdminButton("Edit", "job-offer-neutral-button",
                event -> AppNavigator.showJobOfferForm(offer));
        Button deleteButton = createAdminButton("Delete", "job-offer-delete-button",
                event -> deleteOffer(offer));
        secondaryActions.getChildren().addAll(editButton, deleteButton);

        adminActions.getChildren().add(adminLabel);
        if (!primaryButtons.isEmpty()) {
            adminActions.getChildren().add(primaryActions);
        }
        adminActions.getChildren().add(secondaryActions);
        return adminActions;
    }

    private Button createAdminButton(String text, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("job-offer-card-button", "job-offer-admin-button", styleClass);
        button.setOnAction(event -> {
            event.consume();
            action.handle(event);
        });
        return button;
    }

    private boolean canPartnerCloseOffer(JobOffer offer) {
        if (offer == null || currentUser == null || RoleGuard.isAdmin(currentUser) || RoleGuard.isStudent(currentUser)) {
            return false;
        }

        if (offer.getUser() == null || offer.getUser().getId() == null) {
            return false;
        }

        boolean isOwner = offer.getUser().getId().equals(currentUser.getId());
        return isOwner;
    }

    private void closeOwnOffer(JobOffer offer) {
        if (!canPartnerCloseOffer(offer)) {
            showError("Access denied", "You can close only your own non-closed offers.");
            return;
        }

        JobOfferStatus currentStatus = JobOfferStatus.fromString(offer.getStatus());
        if (currentStatus == JobOfferStatus.CLOSED) {
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Close job offer");
        confirmDialog.setHeaderText(null);
        confirmDialog.setContentText("Close \"" + valueOrDefault(offer.getTitle(), "this offer") + "\"?");

        if (confirmDialog.showAndWait().isPresent() && confirmDialog.getResult().getButtonData().isDefaultButton()) {
            try {
                offer.setStatus(JobOfferStatus.CLOSED.name());
                offer.setUpdatedAt(Timestamp.from(Instant.now()));
                serviceJobOffer.update(offer);
                loadJobOffers();
            } catch (Exception exception) {
                showError("Close failed", exception.getMessage());
            }
        }
    }

    private void reopenOwnOffer(JobOffer offer) {
        if (!canPartnerCloseOffer(offer)) {
            showError("Access denied", "You can reopen only your own offers.");
            return;
        }

        JobOfferStatus currentStatus = JobOfferStatus.fromString(offer.getStatus());
        if (currentStatus != JobOfferStatus.CLOSED) {
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Reopen job offer");
        confirmDialog.setHeaderText(null);
        confirmDialog.setContentText("Reopen \"" + valueOrDefault(offer.getTitle(), "this offer") + "\" as ACTIVE?");

        if (confirmDialog.showAndWait().isPresent() && confirmDialog.getResult().getButtonData().isDefaultButton()) {
            try {
                offer.setStatus(JobOfferStatus.ACTIVE.name());
                if (offer.getPublishedAt() == null) {
                    offer.setPublishedAt(Timestamp.from(Instant.now()));
                }
                offer.setUpdatedAt(Timestamp.from(Instant.now()));
                serviceJobOffer.update(offer);
                loadJobOffers();
            } catch (Exception exception) {
                showError("Reopen failed", exception.getMessage());
            }
        }
    }

    private void updateOfferStatus(JobOffer offer, JobOfferStatus targetStatus) {
        if (!RoleGuard.isAdmin(currentUser) || offer == null) {
            showError("Access denied", "Only administrators can manage offer status.");
            return;
        }

        try {
            JobOfferStatus currentStatus = JobOfferStatus.fromString(offer.getStatus());
            if (currentStatus == targetStatus) {
                return;
            }

            offer.setStatus(targetStatus.name());
            if (targetStatus == JobOfferStatus.ACTIVE && offer.getPublishedAt() == null) {
                offer.setPublishedAt(Timestamp.from(Instant.now()));
            }
            offer.setUpdatedAt(Timestamp.from(Instant.now()));
            serviceJobOffer.update(offer);
            loadJobOffers();
        } catch (Exception exception) {
            showError("Status update failed", exception.getMessage());
        }
    }

    private void deleteOffer(JobOffer offer) {
        if (!RoleGuard.isAdmin(currentUser) || offer == null) {
            showError("Access denied", "Only administrators can delete offers.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Delete job offer");
        confirmDialog.setHeaderText(null);
        confirmDialog.setContentText("Delete \"" + valueOrDefault(offer.getTitle(), "this offer") + "\"?");

        if (confirmDialog.showAndWait().isPresent() && confirmDialog.getResult().getButtonData().isDefaultButton()) {
            try {
                serviceJobOffer.delete(offer);
                loadJobOffers();
            } catch (Exception exception) {
                showError("Delete failed", exception.getMessage());
            }
        }
    }

    private String formatPublishedDate(JobOffer offer) {
        return offer.getPublishedAt() != null
                ? offer.getPublishedAt().toLocalDateTime().format(DATE_FORMATTER)
                : "Not published";
    }

    private void applyStatusChipStyle(Label statusLabel, String status) {
        statusLabel.getStyleClass().add("job-offer-status-chip");
        if (status == null) {
            return;
        }
        String normalized = status.trim().toUpperCase();
        switch (normalized) {
            case "ACTIVE" -> statusLabel.getStyleClass().add("job-offer-status-active");
            case "PENDING" -> statusLabel.getStyleClass().add("job-offer-status-pending");
            case "REJECTED" -> statusLabel.getStyleClass().add("job-offer-status-rejected");
            case "CLOSED" -> statusLabel.getStyleClass().add("job-offer-status-closed");
            default -> {
            }
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
