package controller.job_offer;

import entities.User;
import entities.job_offer.JobOffer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import services.job_offer.ServiceJobOffer;
import util.AppNavigator;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
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
    private Button saveButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Label formSubtitleLabel;

    @FXML
    private Label modeBadgeLabel;

    private JobOffer jobOffer;
    private User currentUser;
    private ServiceJobOffer serviceJobOffer;
    private boolean isNewOffer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        serviceJobOffer = new ServiceJobOffer();
        isNewOffer = true;

        setupTypeCombo();
        setupEducationCombo();
        setupExperienceSpinner();
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
    }

    @FXML
    private void onSave() {
        if (!validate()) {
            return;
        }

        try {
            if (isNewOffer) {
                if (currentUser == null || currentUser.getId() <= 0) {
                    showError("Validation Error", "Current partner account is missing. Please login again.");
                    return;
                }
                jobOffer = new JobOffer();
                jobOffer.setUser(currentUser);
                jobOffer.setStatus("PENDING");
                jobOffer.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
            } else if (jobOffer == null || jobOffer.getId() <= 0) {
                showError("Validation Error", "Cannot update this offer because its ID is invalid.");
                return;
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
}
