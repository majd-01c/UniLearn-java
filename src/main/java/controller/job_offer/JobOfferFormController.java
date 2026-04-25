package controller.job_offer;

import entities.User;
import entities.job_offer.JobOffer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import javafx.util.StringConverter;
import netscape.javascript.JSObject;
import service.UserService;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ResourceBundle;

public class JobOfferFormController implements Initializable {

    @FXML
    private VBox rootContainer;

    @FXML
    private TextField titleField;

    @FXML
    private ComboBox<String> typeCombo;

    @FXML
    private TextField locationField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private TextArea requirementsArea;

    @FXML
    private TextField skillsField;

    @FXML
    private TextField preferredSkillsField;

    @FXML
    private Spinner<Integer> experienceSpinner;

    @FXML
    private ComboBox<String> educationCombo;

    @FXML
    private TextField languagesField;

    @FXML
    private DatePicker publishedDatePicker;

    @FXML
    private Label publishedDateError;

    @FXML
    private DatePicker expiresDatePicker;

    @FXML
    private Label expiresDateError;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Label formSubtitleLabel;

    @FXML
    private Label modeBadgeLabel;

    @FXML
    private VBox partnerSelectionSection;

    @FXML
    private ComboBox<User> partnerCombo;

    @FXML
    private WebView locationMapView;

    /** Keeps a strong reference to avoid garbage-collection of the JS bridge */
    private JavaBridge javaBridge;

    private JobOffer jobOffer;
    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private UserService userService;
    private boolean isNewOffer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobOffer = new ServiceJobOffer();
        userService = new UserService();
        isNewOffer = true;

        setupTypeCombo();
        setupEducationCombo();
        setupExperienceSpinner();
        setupDatePickers();
        setupPartnerCombo();
        setupMap();
    }

    public void setJobOffer(JobOffer offer) {
        this.jobOffer = offer;
        this.isNewOffer = (offer == null);
        updateFormModeLabels();
        if (offer != null) {
            populateForm(offer);
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        configurePartnerVisibility();
    }

    private void updateFormModeLabels() {
        if (saveButton == null) {
            return;
        }

        if (isNewOffer) {
            saveButton.setText("Create Offer");
            if (formTitleLabel != null) {
                formTitleLabel.setText("Create Job Offer");
            }
            if (formSubtitleLabel != null) {
                formSubtitleLabel.setText("Build a polished offer card that fits the partner dashboard and feels like the website version.");
            }
            if (modeBadgeLabel != null) {
                modeBadgeLabel.setText("NEW OFFER");
            }
        } else {
            saveButton.setText("Update Offer");
            if (formTitleLabel != null) {
                formTitleLabel.setText("Edit Job Offer");
            }
            if (formSubtitleLabel != null) {
                formSubtitleLabel.setText("Update the offer details while keeping the same visual style.");
            }
            if (modeBadgeLabel != null) {
                modeBadgeLabel.setText("EDIT MODE");
            }
        }
    }

    private void setupTypeCombo() {
        typeCombo.getItems().addAll("INTERNSHIP", "APPRENTICESHIP", "JOB");
        if (jobOffer != null && jobOffer.getType() != null) {
            typeCombo.setValue(jobOffer.getType());
        } else {
            typeCombo.setValue("JOB");
        }
    }

    private void setupEducationCombo() {
        educationCombo.getItems().addAll("HIGH_SCHOOL", "BACHELOR", "MASTER", "PHD", "NOT_REQUIRED");
        if (jobOffer != null && jobOffer.getMinEducation() != null) {
            educationCombo.setValue(jobOffer.getMinEducation());
        } else {
            educationCombo.setValue("BACHELOR");
        }
    }

    private void setupExperienceSpinner() {
        SpinnerValueFactory<Integer> factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 0);
        experienceSpinner.setValueFactory(factory);
        if (jobOffer != null && jobOffer.getMinExperienceYears() != null) {
            experienceSpinner.getValueFactory().setValue(jobOffer.getMinExperienceYears());
        }
    }

    private void setupDatePickers() {
        // ── Disable past days in the calendar popup ─────────────────────────
        Callback<DatePicker, DateCell> pastDayFactory = dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null && item.isBefore(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #aaaaaa;");
                }
            }
        };

        if (publishedDatePicker != null) {
            publishedDatePicker.setDayCellFactory(pastDayFactory);
            // Real-time listener
            publishedDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
                validatePublishedDate(newVal);
                // Re-validate expiry in case publish date changed
                if (expiresDatePicker != null) {
                    validateExpiresDate(expiresDatePicker.getValue(), newVal);
                }
            });
        }

        if (expiresDatePicker != null) {
            expiresDatePicker.setDayCellFactory(pastDayFactory);
            // Real-time listener
            expiresDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
                LocalDate pub = publishedDatePicker != null ? publishedDatePicker.getValue() : null;
                validateExpiresDate(newVal, pub);
            });
        }

        // Populate values when editing an existing offer
        if (jobOffer != null) {
            if (jobOffer.getPublishedAt() != null && publishedDatePicker != null) {
                publishedDatePicker.setValue(jobOffer.getPublishedAt().toLocalDateTime().toLocalDate());
            }
            if (jobOffer.getExpiresAt() != null && expiresDatePicker != null) {
                expiresDatePicker.setValue(jobOffer.getExpiresAt().toLocalDateTime().toLocalDate());
            }
        }
    }

    /** Shows/hides the inline error label for the publication date. Returns true if valid. */
    private boolean validatePublishedDate(LocalDate date) {
        if (publishedDatePicker == null || publishedDateError == null) return true;
        if (date != null && date.isBefore(LocalDate.now())) {
            publishedDateError.setText("⚠ La date de publication ne peut pas être dans le passé.");
            publishedDateError.setVisible(true);
            publishedDateError.setManaged(true);
            publishedDatePicker.getStyleClass().add("date-picker-error");
            return false;
        }
        publishedDateError.setVisible(false);
        publishedDateError.setManaged(false);
        publishedDatePicker.getStyleClass().remove("date-picker-error");
        return true;
    }

    /** Shows/hides the inline error label for the expiration date. Returns true if valid. */
    private boolean validateExpiresDate(LocalDate expiryDate, LocalDate publishDate) {
        if (expiresDatePicker == null || expiresDateError == null) return true;
        if (expiryDate != null) {
            if (expiryDate.isBefore(LocalDate.now())) {
                expiresDateError.setText("⚠ La date d'expiration ne peut pas être dans le passé.");
                expiresDateError.setVisible(true);
                expiresDateError.setManaged(true);
                expiresDatePicker.getStyleClass().add("date-picker-error");
                return false;
            }
            if (publishDate != null && expiryDate.isBefore(publishDate)) {
                expiresDateError.setText("⚠ La date d'expiration doit être après la date de publication.");
                expiresDateError.setVisible(true);
                expiresDateError.setManaged(true);
                expiresDatePicker.getStyleClass().add("date-picker-error");
                return false;
            }
        }
        expiresDateError.setVisible(false);
        expiresDateError.setManaged(false);
        expiresDatePicker.getStyleClass().remove("date-picker-error");
        return true;
    }

    private void populateForm(JobOffer offer) {
        titleField.setText(offer.getTitle() != null ? offer.getTitle() : "");
        typeCombo.setValue(offer.getType() != null ? offer.getType() : "JOB");
        locationField.setText(offer.getLocation() != null ? offer.getLocation() : "");
        descriptionArea.setText(offer.getDescription() != null ? offer.getDescription() : "");
        requirementsArea.setText(offer.getRequirements() != null ? offer.getRequirements() : "");
        skillsField.setText(offer.getRequiredSkills() != null ? offer.getRequiredSkills() : "");
        preferredSkillsField.setText(offer.getPreferredSkills() != null ? offer.getPreferredSkills() : "");
        educationCombo.setValue(offer.getMinEducation() != null ? offer.getMinEducation() : "BACHELOR");
        if (offer.getMinExperienceYears() != null) {
            experienceSpinner.getValueFactory().setValue(offer.getMinExperienceYears());
        }
        languagesField.setText(offer.getRequiredLanguages() != null ? offer.getRequiredLanguages() : "");
        if (publishedDatePicker != null) {
            publishedDatePicker.setValue(offer.getPublishedAt() != null
                    ? offer.getPublishedAt().toLocalDateTime().toLocalDate()
                    : null);
        }
        if (expiresDatePicker != null) {
            expiresDatePicker.setValue(offer.getExpiresAt() != null
                    ? offer.getExpiresAt().toLocalDateTime().toLocalDate()
                    : null);
        }

        // Pre-fill map if a location is stored
        if (offer.getLocation() != null && !offer.getLocation().isBlank() && locationMapView != null) {
            WebEngine engine = locationMapView.getEngine();
            // If the page is already loaded, geocode immediately; if still loading, wait.
            Runnable geocode = () -> {
                String safe = offer.getLocation().replace("'", "\\'").replace("\\", "\\\\");
                engine.executeScript("geocodeFromJava('" + safe + "')");
            };
            if (engine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                geocode.run();
            } else {
                engine.getLoadWorker().stateProperty().addListener((obs, oldSt, newSt) -> {
                    if (newSt == Worker.State.SUCCEEDED) {
                        Platform.runLater(geocode);
                    }
                });
            }
        }
    }

    @FXML
    private void onSave() {
        if (!validate()) {
            return;
        }

        try {
            if (isNewOffer) {
                // Determine the owner of the offer
                User offerOwner;
                if (isAdminUser() && partnerCombo != null && partnerCombo.getValue() != null) {
                    // Admin is creating on behalf of a partner
                    offerOwner = partnerCombo.getValue();
                } else if (currentUser != null && currentUser.getId() > 0) {
                    // Partner creating their own offer
                    offerOwner = currentUser;
                } else {
                    showError("Validation Error", "Current partner account is missing. Please login again.");
                    return;
                }
                jobOffer = new JobOffer();
                jobOffer.setUser(offerOwner);
                jobOffer.setStatus("PENDING");
                jobOffer.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
            } else if (jobOffer == null || jobOffer.getId() <= 0) {
                showError("Validation Error", "Cannot update this offer because its ID is invalid.");
                return;
            }

            // If admin is editing an existing offer, allow reassigning to another partner
            if (!isNewOffer && isAdminUser() && partnerCombo != null && partnerCombo.getValue() != null) {
                jobOffer.setUser(partnerCombo.getValue());
            }

            jobOffer.setTitle(titleField.getText());
            jobOffer.setType(typeCombo.getValue());
            jobOffer.setLocation(locationField.getText());
            jobOffer.setDescription(descriptionArea.getText());
            jobOffer.setRequirements(requirementsArea.getText());
            jobOffer.setRequiredSkills(skillsField.getText());
            jobOffer.setPreferredSkills(preferredSkillsField.getText());
            jobOffer.setMinExperienceYears(experienceSpinner.getValue());
            jobOffer.setMinEducation(educationCombo.getValue());
            jobOffer.setRequiredLanguages(languagesField.getText());
            jobOffer.setPublishedAt(toTimestamp(publishedDatePicker != null ? publishedDatePicker.getValue() : null));
            jobOffer.setExpiresAt(toTimestamp(expiresDatePicker != null ? expiresDatePicker.getValue() : null));
            jobOffer.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));

            if (isNewOffer) {
                serviceJobOffer.add(jobOffer);
                showInfo("Success", "Job offer created successfully!");
            } else {
                serviceJobOffer.update(jobOffer);
                showInfo("Success", "Job offer updated successfully!");
            }

            AppNavigator.showJobOffers();
        } catch (Exception e) {
            String causeMessage = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage()
                    : e.getMessage();
            showError("Error", "Failed to save job offer: " + causeMessage);
        }
    }

    @FXML
    private void onCancel() {
        AppNavigator.showJobOffers();
    }

    private boolean validate() {
        // Partner selection is required when admin creates/edits an offer
        if (isAdminUser() && partnerCombo != null && partnerCombo.getValue() == null) {
            showError("Validation Error", "Please select a partner to assign this offer to.");
            return false;
        }

        if (titleField.getText().trim().isEmpty()) {
            showError("Validation Error", "Title is required");
            return false;
        }
        if (descriptionArea.getText().trim().isEmpty()) {
            showError("Validation Error", "Description is required");
            return false;
        }
        if (typeCombo.getValue() == null) {
            showError("Validation Error", "Type is required");
            return false;
        }

        // Date validations (also trigger real-time labels so the user sees them)
        LocalDate pub    = publishedDatePicker != null ? publishedDatePicker.getValue() : null;
        LocalDate expiry = expiresDatePicker   != null ? expiresDatePicker.getValue()   : null;

        boolean pubOk    = validatePublishedDate(pub);
        boolean expiryOk = validateExpiresDate(expiry, pub);

        if (!pubOk) {
            showError("Validation Error", "La date de publication ne peut pas être dans le passé.");
            return false;
        }
        if (!expiryOk) {
            // The inline label already describes the exact problem
            showError("Validation Error", expiresDateError != null && expiresDateError.getText() != null
                    ? expiresDateError.getText().replaceFirst("^⚠ ", "")
                    : "Date d'expiration invalide.");
            return false;
        }

        return true;
    }

    private Timestamp toTimestamp(LocalDate date) {
        if (date == null) {
            return null;
        }
        return Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ── Partner selection helpers ──────────────────────────────────────

    private boolean isAdminUser() {
        return currentUser != null && RoleGuard.isAdmin(currentUser);
    }

    /**
     * Configures the partner ComboBox with a StringConverter so that
     * each partner is displayed by name + email.
     */
    private void setupPartnerCombo() {
        if (partnerCombo == null) return;

        partnerCombo.setConverter(new StringConverter<User>() {
            @Override
            public String toString(User user) {
                if (user == null) return "";
                String name = user.getName() != null && !user.getName().isBlank()
                        ? user.getName() : "(no name)";
                return name + "  —  " + user.getEmail();
            }

            @Override
            public User fromString(String string) {
                return null; // not needed for non-editable combo
            }
        });
    }

    /**
     * Shows the partner selector only when the current user is an admin.
     * Loads partner list from DB and, when editing, pre-selects the offer owner.
     */
    private void configurePartnerVisibility() {
        if (partnerSelectionSection == null || partnerCombo == null) return;

        boolean showPartnerSelector = isAdminUser();
        partnerSelectionSection.setVisible(showPartnerSelector);
        partnerSelectionSection.setManaged(showPartnerSelector);

        if (showPartnerSelector) {
            try {
                List<User> partners = userService.getPartnerUsers();
                partnerCombo.setItems(FXCollections.observableArrayList(partners));

                // When editing, pre-select the current offer owner
                if (jobOffer != null && jobOffer.getUser() != null) {
                    int ownerId = jobOffer.getUser().getId();
                    partners.stream()
                            .filter(p -> p.getId().equals(ownerId))
                            .findFirst()
                            .ifPresent(partnerCombo::setValue);
                }
            } catch (Exception e) {
                showError("Error", "Failed to load partner list: " + e.getMessage());
            }
        }
    }

    // ── Map setup ─────────────────────────────────────────────────────────

    /**
     * Loads the Leaflet map HTML into the WebView and registers a Java bridge
     * so that clicking on the map auto-fills the locationField text field.
     */
    private void setupMap() {
        if (locationMapView == null) return;

        WebEngine engine = locationMapView.getEngine();

        // Allow JS to call back into Java
        engine.setJavaScriptEnabled(true);

        // Load the map HTML from resources
        URL mapUrl = getClass().getResource("/view/job_offer/job-offer-map.html");
        if (mapUrl == null) {
            System.err.println("[Map] Could not find job-offer-map.html in classpath");
            return;
        }

        // Inject the JS bridge after the page finishes loading
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                javaBridge = new JavaBridge();
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaConnector", javaBridge);

                // Sync: when the user types in locationField and presses Enter, search on map
                if (locationField != null) {
                    locationField.setOnAction(e -> {
                        String text = locationField.getText().trim();
                        if (!text.isEmpty()) {
                            String safe = text.replace("'", "\\'").replace("\\", "\\\\");
                            engine.executeScript("geocodeFromJava('" + safe + "')");
                        }
                    });
                }
            }
        });

        engine.load(mapUrl.toExternalForm());
    }

    /**
     * Bridge object exposed to JavaScript as <code>window.javaConnector</code>.
     * Methods on this class can be invoked from JS running inside the WebView.
     * IMPORTANT: Must be kept as a strong reference on the Java side.
     */
    public class JavaBridge {
        /**
         * Called by the map when the user clicks or drags the pin.
         *
         * @param lat     latitude
         * @param lng     longitude
         * @param address human-readable address from Nominatim
         */
        public void onLocationSelected(double lat, double lng, String address) {
            Platform.runLater(() -> {
                if (locationField != null) {
                    locationField.setText(address != null && !address.isBlank()
                            ? address
                            : lat + ", " + lng);
                }
            });
        }
    }
}
