package controller.job_offer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import entities.CustomSkill;
import entities.User;
import entities.job_offer.JobOffer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import javafx.util.StringConverter;
import repository.job_offer.CustomSkillRepository;
import service.UserService;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;
import util.RoleGuard;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class JobOfferFormController implements Initializable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int DEFAULT_EXPERIENCE_WEIGHT = 15;
    private static final int DEFAULT_EDUCATION_WEIGHT = 20;
    private static final int DEFAULT_SKILLS_WEIGHT = 55;
    private static final int DEFAULT_LANGUAGES_WEIGHT = 10;

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
    private FlowPane reqSourcePane;

    @FXML
    private FlowPane reqSelectedPane;

    @FXML
    private TextField reqSearchField;

    @FXML
    private Button reqAddSkillButton;

    @FXML
    private FlowPane prefSourcePane;

    @FXML
    private FlowPane prefSelectedPane;

    @FXML
    private TextField prefSearchField;

    @FXML
    private Button prefAddSkillButton;

    @FXML
    private Slider expWeightSlider;

    @FXML
    private Slider eduWeightSlider;

    @FXML
    private Slider skillsWeightSlider;

    @FXML
    private Slider langWeightSlider;

    @FXML
    private Label expWeightVal;

    @FXML
    private Label eduWeightVal;

    @FXML
    private Label skillsWeightVal;

    @FXML
    private Label langWeightVal;

    @FXML
    private Label weightTotalLabel;

    @FXML
    private Button toggleWeightsButton;

    @FXML
    private VBox weightsContainer;

    @FXML
    private HBox weightBarRow;

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
    private Button toggleMapButton;

    @FXML
    private VBox mapContainer;

    @FXML
    private WebView locationMapView;

    private final CustomSkillRepository skillRepo = new CustomSkillRepository();
    private JavaBridge javaBridge;

    private JobOffer jobOffer;
    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private UserService userService;
    private boolean isNewOffer;
    private boolean mapLoaded;

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
        configureMapVisibility(false);
        setupSkillPicker();
        setupWeightSliders();
        configureWeightsVisibility(false);
    }

    public void setJobOffer(JobOffer offer) {
        this.jobOffer = offer;
        this.isNewOffer = offer == null;
        updateFormModeLabels();
        if (offer != null) {
            populateForm(offer);
        } else {
            resetWeightSlidersToDefaults();
        }
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        configurePartnerVisibility();
        refreshSkillSources();
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
        typeCombo.setValue("JOB");
    }

    private void setupEducationCombo() {
        educationCombo.getItems().addAll("bac", "bac+2", "licence", "master", "ingenieur", "doctorat");
        educationCombo.setValue("licence");
    }

    private void setupExperienceSpinner() {
        SpinnerValueFactory<Integer> factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 0);
        experienceSpinner.setValueFactory(factory);
    }

    private void setupDatePickers() {
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
            publishedDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
                validatePublishedDate(newVal);
                if (expiresDatePicker != null) {
                    validateExpiresDate(expiresDatePicker.getValue(), newVal);
                }
            });
        }

        if (expiresDatePicker != null) {
            expiresDatePicker.setDayCellFactory(pastDayFactory);
            expiresDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
                LocalDate pub = publishedDatePicker != null ? publishedDatePicker.getValue() : null;
                validateExpiresDate(newVal, pub);
            });
        }
    }

    private boolean validatePublishedDate(LocalDate date) {
        if (publishedDatePicker == null || publishedDateError == null) {
            return true;
        }
        if (date != null && date.isBefore(LocalDate.now())) {
            publishedDateError.setText("⚠ La date de publication ne peut pas être dans le passé.");
            publishedDateError.setVisible(true);
            publishedDateError.setManaged(true);
            if (!publishedDatePicker.getStyleClass().contains("date-picker-error")) {
                publishedDatePicker.getStyleClass().add("date-picker-error");
            }
            return false;
        }
        publishedDateError.setVisible(false);
        publishedDateError.setManaged(false);
        publishedDatePicker.getStyleClass().remove("date-picker-error");
        return true;
    }

    private boolean validateExpiresDate(LocalDate expiryDate, LocalDate publishDate) {
        if (expiresDatePicker == null || expiresDateError == null) {
            return true;
        }
        if (expiryDate != null) {
            if (expiryDate.isBefore(LocalDate.now())) {
                expiresDateError.setText("⚠ La date d'expiration ne peut pas être dans le passé.");
                expiresDateError.setVisible(true);
                expiresDateError.setManaged(true);
                if (!expiresDatePicker.getStyleClass().contains("date-picker-error")) {
                    expiresDatePicker.getStyleClass().add("date-picker-error");
                }
                return false;
            }
            if (publishDate != null && expiryDate.isBefore(publishDate)) {
                expiresDateError.setText("⚠ La date d'expiration doit être après la date de publication.");
                expiresDateError.setVisible(true);
                expiresDateError.setManaged(true);
                if (!expiresDatePicker.getStyleClass().contains("date-picker-error")) {
                    expiresDatePicker.getStyleClass().add("date-picker-error");
                }
                return false;
            }
        }
        expiresDateError.setVisible(false);
        expiresDateError.setManaged(false);
        expiresDatePicker.getStyleClass().remove("date-picker-error");
        return true;
    }

    private void populateForm(JobOffer offer) {
        titleField.setText(safe(offer.getTitle()));
        typeCombo.setValue(offer.getType() != null ? offer.getType() : "JOB");
        locationField.setText(safe(offer.getLocation()));
        descriptionArea.setText(safe(offer.getDescription()));
        requirementsArea.setText(safe(offer.getRequirements()));
        educationCombo.setValue(offer.getMinEducation() != null ? offer.getMinEducation() : "licence");
        languagesField.setText(safe(offer.getRequiredLanguages()));

        if (offer.getMinExperienceYears() != null) {
            experienceSpinner.getValueFactory().setValue(offer.getMinExperienceYears());
        }

        reqSelectedPane.getChildren().clear();
        prefSelectedPane.getChildren().clear();
        populateSkillChips(offer.getRequiredSkills(), reqSelectedPane, "required");
        populateSkillChips(offer.getPreferredSkills(), prefSelectedPane, "preferred");

        if (offer.getScoreConfig() != null && !offer.getScoreConfig().isBlank()) {
            parseScoreConfig(offer.getScoreConfig());
        } else {
            resetWeightSlidersToDefaults();
        }

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

        if (offer.getLocation() != null && !offer.getLocation().isBlank() && locationMapView != null && mapLoaded) {
            WebEngine engine = locationMapView.getEngine();
            Runnable geocode = () -> engine.executeScript("geocodeFromJava('" + escapeForJavascript(offer.getLocation()) + "')");
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

        refreshSkillSources();
    }

    @FXML
    private void onSave() {
        if (!validate()) {
            return;
        }

        try {
            if (isNewOffer) {
                User offerOwner;
                if (isAdminUser() && partnerCombo != null && partnerCombo.getValue() != null) {
                    offerOwner = partnerCombo.getValue();
                } else if (currentUser != null && currentUser.getId() != null && currentUser.getId() > 0) {
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

            if (!isNewOffer && isAdminUser() && partnerCombo != null && partnerCombo.getValue() != null) {
                jobOffer.setUser(partnerCombo.getValue());
            }

            jobOffer.setTitle(titleField.getText().trim());
            jobOffer.setType(typeCombo.getValue());
            jobOffer.setLocation(trimToNull(locationField.getText()));
            jobOffer.setDescription(descriptionArea.getText().trim());
            jobOffer.setRequirements(trimToNull(requirementsArea.getText()));
            jobOffer.setRequiredSkills(getSelectedNames(reqSelectedPane));
            jobOffer.setPreferredSkills(getSelectedNames(prefSelectedPane));
            jobOffer.setMinExperienceYears(experienceSpinner.getValue());
            jobOffer.setMinEducation(educationCombo.getValue());
            jobOffer.setRequiredLanguages(trimToNull(languagesField.getText()));
            jobOffer.setScoreConfig(buildScoreConfigJson());
            jobOffer.setPublishedAt(toTimestamp(publishedDatePicker != null ? publishedDatePicker.getValue() : null));
            jobOffer.setExpiresAt(toTimestamp(expiresDatePicker != null ? expiresDatePicker.getValue() : null));
            jobOffer.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));

            if (isNewOffer) {
                serviceJobOffer.add(jobOffer);
                showInfo("Success", "Job offer created successfully.");
            } else {
                serviceJobOffer.update(jobOffer);
                showInfo("Success", "Job offer updated successfully.");
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
        if (isAdminUser() && partnerCombo != null && partnerCombo.getValue() == null) {
            showError("Validation Error", "Please select a partner to assign this offer to.");
            return false;
        }

        if (titleField.getText().trim().isEmpty()) {
            showError("Validation Error", "Title is required.");
            return false;
        }
        if (descriptionArea.getText().trim().isEmpty()) {
            showError("Validation Error", "Description is required.");
            return false;
        }
        if (typeCombo.getValue() == null) {
            showError("Validation Error", "Type is required.");
            return false;
        }

        LocalDate pub = publishedDatePicker != null ? publishedDatePicker.getValue() : null;
        LocalDate expiry = expiresDatePicker != null ? expiresDatePicker.getValue() : null;

        boolean pubOk = validatePublishedDate(pub);
        boolean expiryOk = validateExpiresDate(expiry, pub);

        if (!pubOk) {
            showError("Validation Error", "La date de publication ne peut pas être dans le passé.");
            return false;
        }
        if (!expiryOk) {
            showError("Validation Error", expiresDateError != null && expiresDateError.getText() != null
                    ? expiresDateError.getText().replaceFirst("^⚠ ", "")
                    : "Date d'expiration invalide.");
            return false;
        }

        return true;
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

    private Timestamp toTimestamp(LocalDate date) {
        if (date == null) {
            return null;
        }
        return Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private boolean isAdminUser() {
        return currentUser != null && RoleGuard.isAdmin(currentUser);
    }

    private void setupPartnerCombo() {
        if (partnerCombo == null) {
            return;
        }

        partnerCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(User user) {
                if (user == null) {
                    return "";
                }
                String name = user.getName() != null && !user.getName().isBlank() ? user.getName() : "(no name)";
                return name + "  —  " + user.getEmail();
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        });

        partnerCombo.valueProperty().addListener((obs, oldVal, newVal) -> refreshSkillSources());
    }

    private void configurePartnerVisibility() {
        if (partnerSelectionSection == null || partnerCombo == null) {
            return;
        }

        boolean showPartnerSelector = isAdminUser();
        partnerSelectionSection.setVisible(showPartnerSelector);
        partnerSelectionSection.setManaged(showPartnerSelector);

        if (!showPartnerSelector) {
            return;
        }

        try {
            List<User> partners = userService.getPartnerUsers();
            partnerCombo.setItems(FXCollections.observableArrayList(partners));

            if (jobOffer != null && jobOffer.getUser() != null) {
                Integer ownerId = jobOffer.getUser().getId();
                partners.stream()
                        .filter(partner -> partner.getId() != null && partner.getId().equals(ownerId))
                        .findFirst()
                        .ifPresent(partnerCombo::setValue);
            }
        } catch (Exception e) {
            showError("Error", "Failed to load partner list: " + e.getMessage());
        }
    }

    private void setupMap() {
        if (locationMapView == null) {
            return;
        }

        WebEngine engine = locationMapView.getEngine();
        engine.setJavaScriptEnabled(true);

        URL mapUrl = getClass().getResource("/view/job_offer/job-offer-map.html");
        if (mapUrl == null) {
            System.err.println("[Map] Could not find job-offer-map.html in classpath");
            return;
        }

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                javaBridge = new JavaBridge();
                exposeJavaConnector(engine, javaBridge);

                if (locationField != null) {
                    locationField.setOnAction(e -> {
                        String text = locationField.getText().trim();
                        if (!text.isEmpty()) {
                            engine.executeScript("geocodeFromJava('" + escapeForJavascript(text) + "')");
                        }
                    });
                }

                if (jobOffer != null && jobOffer.getLocation() != null && !jobOffer.getLocation().isBlank()) {
                    Platform.runLater(() -> engine.executeScript("geocodeFromJava('" + escapeForJavascript(jobOffer.getLocation()) + "')"));
                }
            }
        });

        engine.load(mapUrl.toExternalForm());
        mapLoaded = true;
    }

    @FXML
    private void onToggleMap() {
        boolean showMap = mapContainer == null || !mapContainer.isVisible();
        configureMapVisibility(showMap);
        if (showMap && !mapLoaded) {
            setupMap();
        }
    }

    private void configureMapVisibility(boolean visible) {
        if (mapContainer != null) {
            mapContainer.setVisible(visible);
            mapContainer.setManaged(visible);
        }
        if (toggleMapButton != null) {
            toggleMapButton.setText(visible ? "Hide map picker" : "Open map picker");
        }
    }

    @FXML
    private void onToggleWeights() {
        boolean showWeights = weightsContainer == null || !weightsContainer.isVisible();
        configureWeightsVisibility(showWeights);
    }

    private void configureWeightsVisibility(boolean visible) {
        if (weightsContainer != null) {
            weightsContainer.setVisible(visible);
            weightsContainer.setManaged(visible);
        }
        if (toggleWeightsButton != null) {
            toggleWeightsButton.setText(visible ? "Hide ATS scoring weights" : "Open ATS scoring weights");
        }
    }

    private void setupSkillPicker() {
        setupSkillSearchField(reqSearchField, reqSourcePane, reqSelectedPane, prefSelectedPane, "required");
        setupSkillSearchField(prefSearchField, prefSourcePane, prefSelectedPane, reqSelectedPane, "preferred");
        refreshSkillSources();
    }

    private void setupSkillSearchField(TextField searchField, FlowPane sourcePane, FlowPane selectedPane,
                                       FlowPane oppositePane, String kind) {
        if (searchField == null) {
            return;
        }

        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshSkillSourcePane(sourcePane, selectedPane, kind, newVal));
        searchField.setOnAction(event -> handleSkillEntry(searchField, sourcePane, selectedPane, oppositePane, kind));
    }

    @FXML
    private void onAddRequiredSkill() {
        handleSkillEntry(reqSearchField, reqSourcePane, reqSelectedPane, prefSelectedPane, "required");
    }

    @FXML
    private void onAddPreferredSkill() {
        handleSkillEntry(prefSearchField, prefSourcePane, prefSelectedPane, reqSelectedPane, "preferred");
    }

    private void handleSkillEntry(TextField searchField, FlowPane sourcePane, FlowPane selectedPane,
                                  FlowPane oppositePane, String kind) {
        String skillName = normalizeSkillName(searchField != null ? searchField.getText() : null);
        if (skillName == null) {
            return;
        }

        persistSkillForFuture(skillName);
        addSkillToSelection(skillName, selectedPane, oppositePane, kind);

        if (searchField != null) {
            searchField.clear();
        }
        refreshSkillSourcePane(sourcePane, selectedPane, kind, "");
    }

    private void refreshSkillSources() {
        refreshSkillSourcePane(reqSourcePane, reqSelectedPane, "required", reqSearchField != null ? reqSearchField.getText() : "");
        refreshSkillSourcePane(prefSourcePane, prefSelectedPane, "preferred", prefSearchField != null ? prefSearchField.getText() : "");
    }

    private void refreshSkillSourcePane(FlowPane sourcePane, FlowPane selectedPane, String kind, String searchText) {
        if (sourcePane == null) {
            return;
        }

        sourcePane.getChildren().clear();
        List<String> candidateSkills = loadSkillSuggestions(searchText);

        LinkedHashSet<String> visibleSkills = new LinkedHashSet<>(candidateSkills);
        String typedSkill = normalizeSkillName(searchText);
        if (typedSkill != null) {
            visibleSkills.add(typedSkill);
        }

        for (String skillName : visibleSkills) {
            Button chip = buildSourceChip(skillName, selectedPane, kind);
            sourcePane.getChildren().add(chip);
        }
    }

    private List<String> loadSkillSuggestions(String searchText) {
        try {
            Integer partnerId = resolveSkillSourcePartnerId();
            List<CustomSkill> skills;

            if (partnerId != null) {
                skills = hasText(searchText)
                        ? skillRepo.searchPartnerSkills(partnerId, searchText)
                        : skillRepo.findByPartnerId(partnerId);
            } else {
                skills = hasText(searchText)
                        ? skillRepo.search(searchText)
                        : skillRepo.findAll();
            }

            return skills.stream()
                    .map(CustomSkill::getName)
                    .map(this::normalizeSkillName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private void persistSkillForFuture(String skillName) {
        Integer partnerId = resolveSkillSourcePartnerId();
        User skillOwner = resolveSkillOwner();

        if (partnerId == null || skillOwner == null || skillName == null) {
            return;
        }

        try {
            if (skillRepo.existsByNameAndPartnerId(partnerId, skillName)) {
                return;
            }

            CustomSkill skill = new CustomSkill();
            skill.setUser(skillOwner);
            skill.setName(skillName);
            skill.setCategory("JOB_OFFER");
            skill.setDescription("Saved from the job offer form");
            Timestamp now = Timestamp.from(Instant.now());
            skill.setCreatedAt(now);
            skill.setUpdatedAt(now);
            skillRepo.save(skill);
        } catch (Exception ignored) {
            // The offer form should stay usable even if saving the reusable skill fails.
        }
    }

    private Integer resolveSkillSourcePartnerId() {
        if (isAdminUser()) {
            if (partnerCombo != null && partnerCombo.getValue() != null && partnerCombo.getValue().getId() != null) {
                return partnerCombo.getValue().getId();
            }
            if (jobOffer != null && jobOffer.getUser() != null && jobOffer.getUser().getId() != null) {
                return jobOffer.getUser().getId();
            }
            return null;
        }

        if (currentUser != null && currentUser.getId() != null && currentUser.getId() > 0) {
            return currentUser.getId();
        }

        return null;
    }

    private User resolveSkillOwner() {
        if (isAdminUser()) {
            if (partnerCombo != null && partnerCombo.getValue() != null) {
                return partnerCombo.getValue();
            }
            if (jobOffer != null) {
                return jobOffer.getUser();
            }
            return null;
        }
        return currentUser;
    }

    private Button buildSourceChip(String skillName, FlowPane selectedPane, String kind) {
        Button chip = new Button(skillName);
        chip.getStyleClass().add("skill-source-chip");

        boolean alreadySelected = containsSkill(selectedPane, skillName);
        if (alreadySelected) {
            chip.getStyleClass().add("skill-source-chip-selected");
            chip.setDisable(true);
        } else {
            chip.setOnAction(event -> {
                FlowPane oppositePane = "required".equals(kind) ? prefSelectedPane : reqSelectedPane;
                addSkillToSelection(skillName, selectedPane, oppositePane, kind);
                refreshSkillSources();
            });
        }

        return chip;
    }

    private void addSkillToSelection(String skillName, FlowPane targetPane, FlowPane oppositePane, String kind) {
        String normalizedSkill = normalizeSkillName(skillName);
        if (normalizedSkill == null) {
            return;
        }

        removeSkillFromPane(oppositePane, normalizedSkill);
        if (containsSkill(targetPane, normalizedSkill)) {
            return;
        }

        Button chip = buildSelectedChip(normalizedSkill, targetPane, kind);
        targetPane.getChildren().add(chip);
        refreshSkillSources();
    }

    private Button buildSelectedChip(String skillName, FlowPane parentPane, String kind) {
        Button chip = new Button(skillName + "  ×");
        chip.setUserData(skillName);
        chip.getStyleClass().addAll("skill-chip", "required".equals(kind) ? "skill-chip-required" : "skill-chip-preferred");
        chip.setOnAction(event -> {
            parentPane.getChildren().remove(chip);
            refreshSkillSources();
        });
        return chip;
    }

    private void populateSkillChips(String rawValue, FlowPane targetPane, String kind) {
        if (targetPane == null || rawValue == null || rawValue.isBlank()) {
            return;
        }

        for (String item : splitSkills(rawValue)) {
            if (!containsSkill(targetPane, item)) {
                targetPane.getChildren().add(buildSelectedChip(item, targetPane, kind));
            }
        }
    }

    private List<String> splitSkills(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Collections.emptyList();
        }
        String cleaned = rawValue.trim();

        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            try {
                List<String> jsonSkills = OBJECT_MAPPER.readValue(cleaned, new TypeReference<List<String>>() { });
                return jsonSkills.stream()
                        .map(this::normalizeSkillName)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
                // Fall back to simple split below.
            }
        }

        String[] parts = cleaned.split("[,;\\n]+");
        List<String> skills = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalizeSkillName(part);
            if (normalized != null) {
                skills.add(normalized);
            }
        }
        return skills;
    }

    private String getSelectedNames(FlowPane pane) {
        if (pane == null) {
            return null;
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        pane.getChildren().forEach(node -> {
            if (node instanceof Button button && button.getUserData() instanceof String value) {
                names.add(value);
            }
        });

        if (names.isEmpty()) {
            return null;
        }
        return String.join(", ", names);
    }

    private boolean containsSkill(FlowPane pane, String skillName) {
        if (pane == null || skillName == null) {
            return false;
        }
        return pane.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .map(button -> button.getUserData() instanceof String ? (String) button.getUserData() : button.getText())
                .anyMatch(name -> skillName.equalsIgnoreCase(name.replace("×", "").trim()));
    }

    private void removeSkillFromPane(FlowPane pane, String skillName) {
        if (pane == null || skillName == null) {
            return;
        }

        List<javafx.scene.Node> toRemove = pane.getChildren().stream()
                .filter(Button.class::isInstance)
                .filter(node -> {
                    Button button = (Button) node;
                    Object userData = button.getUserData();
                    return userData instanceof String && skillName.equalsIgnoreCase((String) userData);
                })
                .collect(Collectors.toList());
        pane.getChildren().removeAll(toRemove);
    }

    private void setupWeightSliders() {
        configureWeightSlider(expWeightSlider, expWeightVal);
        configureWeightSlider(eduWeightSlider, eduWeightVal);
        configureWeightSlider(skillsWeightSlider, skillsWeightVal);
        configureWeightSlider(langWeightSlider, langWeightVal);
        resetWeightSlidersToDefaults();
    }

    private void configureWeightSlider(Slider slider, Label valueLabel) {
        if (slider == null || valueLabel == null) {
            return;
        }

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int rounded = (int) Math.round(newVal.doubleValue());
            if (Math.abs(slider.getValue() - rounded) > 0.001) {
                slider.setValue(rounded);
                return;
            }
            valueLabel.setText(Integer.toString(rounded));
            updateWeightSummary();
        });
    }

    private void resetWeightSlidersToDefaults() {
        if (expWeightSlider != null) {
            expWeightSlider.setValue(DEFAULT_EXPERIENCE_WEIGHT);
        }
        if (eduWeightSlider != null) {
            eduWeightSlider.setValue(DEFAULT_EDUCATION_WEIGHT);
        }
        if (skillsWeightSlider != null) {
            skillsWeightSlider.setValue(DEFAULT_SKILLS_WEIGHT);
        }
        if (langWeightSlider != null) {
            langWeightSlider.setValue(DEFAULT_LANGUAGES_WEIGHT);
        }
        updateWeightSummary();
    }

    private void updateWeightSummary() {
        int experience = getRoundedSliderValue(expWeightSlider, DEFAULT_EXPERIENCE_WEIGHT);
        int education = getRoundedSliderValue(eduWeightSlider, DEFAULT_EDUCATION_WEIGHT);
        int skills = getRoundedSliderValue(skillsWeightSlider, DEFAULT_SKILLS_WEIGHT);
        int languages = getRoundedSliderValue(langWeightSlider, DEFAULT_LANGUAGES_WEIGHT);
        int total = experience + education + skills + languages;

        if (expWeightVal != null) {
            expWeightVal.setText(Integer.toString(experience));
        }
        if (eduWeightVal != null) {
            eduWeightVal.setText(Integer.toString(education));
        }
        if (skillsWeightVal != null) {
            skillsWeightVal.setText(Integer.toString(skills));
        }
        if (langWeightVal != null) {
            langWeightVal.setText(Integer.toString(languages));
        }
        if (weightTotalLabel != null) {
            weightTotalLabel.setText("Total: " + total + " pts");
            weightTotalLabel.getStyleClass().removeAll("ats-weight-total-ok", "ats-weight-total-warn");
            weightTotalLabel.getStyleClass().add(total == 100 ? "ats-weight-total-ok" : "ats-weight-total-warn");
        }

        if (weightBarRow != null) {
            weightBarRow.getChildren().setAll(
                    buildWeightBarSegment(experience, "ats-weight-bar-exp"),
                    buildWeightBarSegment(education, "ats-weight-bar-edu"),
                    buildWeightBarSegment(skills, "ats-weight-bar-skills"),
                    buildWeightBarSegment(languages, "ats-weight-bar-lang")
            );
        }
    }

    private Region buildWeightBarSegment(int value, String styleClass) {
        Region region = new Region();
        region.getStyleClass().addAll("ats-weight-bar-segment", styleClass);
        region.setPrefWidth(Math.max(value * 2.4, value == 0 ? 2 : 8));
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private String buildScoreConfigJson() {
        Map<String, Integer> scoreConfig = new LinkedHashMap<>();
        scoreConfig.put("experienceWeight", getRoundedSliderValue(expWeightSlider, DEFAULT_EXPERIENCE_WEIGHT));
        scoreConfig.put("educationWeight", getRoundedSliderValue(eduWeightSlider, DEFAULT_EDUCATION_WEIGHT));
        scoreConfig.put("skillsWeight", getRoundedSliderValue(skillsWeightSlider, DEFAULT_SKILLS_WEIGHT));
        scoreConfig.put("languagesWeight", getRoundedSliderValue(langWeightSlider, DEFAULT_LANGUAGES_WEIGHT));

        try {
            return OBJECT_MAPPER.writeValueAsString(scoreConfig);
        } catch (Exception exception) {
            return String.format(
                    "{\"experienceWeight\":%d,\"educationWeight\":%d,\"skillsWeight\":%d,\"languagesWeight\":%d}",
                    scoreConfig.get("experienceWeight"),
                    scoreConfig.get("educationWeight"),
                    scoreConfig.get("skillsWeight"),
                    scoreConfig.get("languagesWeight")
            );
        }
    }

    private void parseScoreConfig(String rawConfig) {
        try {
            Map<String, Object> config = OBJECT_MAPPER.readValue(rawConfig, new TypeReference<Map<String, Object>>() { });
            expWeightSlider.setValue(resolveWeightValue(config, DEFAULT_EXPERIENCE_WEIGHT, "experienceWeight", "expW", "experience"));
            eduWeightSlider.setValue(resolveWeightValue(config, DEFAULT_EDUCATION_WEIGHT, "educationWeight", "eduW", "education"));
            skillsWeightSlider.setValue(resolveWeightValue(config, DEFAULT_SKILLS_WEIGHT, "skillsWeight", "skillsW", "skills"));
            langWeightSlider.setValue(resolveWeightValue(config, DEFAULT_LANGUAGES_WEIGHT, "languagesWeight", "langW", "languages"));
        } catch (Exception exception) {
            resetWeightSlidersToDefaults();
        }
        updateWeightSummary();
    }

    private int resolveWeightValue(Map<String, Object> config, int fallback, String... keys) {
        for (String key : keys) {
            Object value = config.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String stringValue) {
                try {
                    return Integer.parseInt(stringValue.trim());
                } catch (NumberFormatException ignored) {
                    // Continue to the next supported key.
                }
            }
        }
        return fallback;
    }

    private int getRoundedSliderValue(Slider slider, int fallback) {
        return slider == null ? fallback : (int) Math.round(slider.getValue());
    }

    private String normalizeSkillName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escapeForJavascript(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void exposeJavaConnector(WebEngine engine, Object connector) {
        Object window = engine.executeScript("window");
        try {
            window.getClass()
                    .getMethod("setMember", String.class, Object.class)
                    .invoke(window, "javaConnector", connector);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to expose Java connector to map view.", exception);
        }
    }

    public class JavaBridge {
        public void onLocationSelected(double lat, double lng, String address) {
            Platform.runLater(() -> {
                if (locationField != null) {
                    locationField.setText(address != null && !address.isBlank() ? address : lat + ", " + lng);
                }
            });
        }
    }

}
