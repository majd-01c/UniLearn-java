package controller;

import entities.Profile;
import entities.User;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import service.EmailService;
import service.PasswordManagementService;
import service.ProfileService;
import service.UserService;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class UserFormController implements Initializable {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+0-9()\\-\\s]{7,20}$");

    @FXML
    private TextField emailField;

    @FXML
    private ComboBox<String> roleComboBox;

    @FXML
    private TextField firstNameField;

    @FXML
    private TextField lastNameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private HBox tempPasswordRow;

    @FXML
    private TextField tempPasswordField;

    @FXML
    private CheckBox sendWelcomeEmailCheckBox;

    @FXML
    private HBox statusRow;

    @FXML
    private ComboBox<String> statusComboBox;

    private final UserService userService = new UserService();
    private final ProfileService profileService = new ProfileService();
    private final PasswordManagementService passwordManagementService = new PasswordManagementService();
    private final EmailService emailService = new EmailService();

    private boolean editMode;
    private User editingUser;
    private Profile editingProfile;
    private String generatedTempPassword;
    private Runnable onSaveSuccess;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        roleComboBox.setItems(FXCollections.observableArrayList("ADMIN", "TEACHER", "STUDENT", "PARTNER", "TRAINER"));
        statusComboBox.setItems(FXCollections.observableArrayList("Active", "Inactive"));
        setCreateMode();
    }

    public void setOnSaveSuccess(Runnable onSaveSuccess) {
        this.onSaveSuccess = onSaveSuccess;
    }

    public void setCreateMode() {
        editMode = false;
        editingUser = null;
        editingProfile = null;

        clearForm();
        emailField.setDisable(false);
        roleComboBox.getSelectionModel().clearSelection();
        statusComboBox.getSelectionModel().select("Active");
        sendWelcomeEmailCheckBox.setSelected(true);

        generateTempPassword();
        updateModeUi();
    }

    public void setEditMode(User user) {
        if (user == null) {
            throw new IllegalArgumentException("A user is required for edit mode");
        }

        editMode = true;
        generatedTempPassword = null;
        tempPasswordField.clear();
        sendWelcomeEmailCheckBox.setSelected(false);

        editingUser = resolveFreshUser(user);
        editingProfile = loadProfile(editingUser);

        emailField.setText(safeText(editingUser.getEmail()));
        roleComboBox.getSelectionModel().select(toDisplayRole(editingUser.getRole()));
        statusComboBox.getSelectionModel().select(editingUser.getIsActive() == (byte) 1 ? "Active" : "Inactive");

        firstNameField.setText(resolveFirstName(editingUser, editingProfile));
        lastNameField.setText(resolveLastName(editingUser, editingProfile));
        phoneField.setText(resolvePhone(editingUser, editingProfile));
        descriptionArea.setText(resolveDescription(editingUser, editingProfile));

        updateModeUi();
    }

    @FXML
    private void onGenerateTempPassword() {
        if (editMode) {
            return;
        }
        generateTempPassword();
    }

    @FXML
    private void onCopyTempPassword() {
        if (editMode) {
            showWarning("Not available", "Temporary password copy is only available in create mode.");
            return;
        }

        String password = normalize(tempPasswordField.getText());
        if (password == null) {
            showWarning("Nothing to copy", "Generate a temporary password first.");
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(password);
        Clipboard.getSystemClipboard().setContent(content);
        showInfo("Copied", "Temporary password copied to clipboard.");
    }

    @FXML
    private void onSave() {
        List<String> validationErrors = validateForm();
        if (!validationErrors.isEmpty()) {
            showValidationErrors(validationErrors);
            return;
        }

        try {
            if (editMode) {
                saveExistingUser();
            } else {
                saveNewUser();
            }

            if (onSaveSuccess != null) {
                onSaveSuccess.run();
            }

            showInfo("Success", editMode ? "User updated successfully." : "User created successfully.");
            closeWindow();
        } catch (Exception exception) {
            String message = normalize(exception.getMessage());
            if (message == null) {
                message = "An unexpected error occurred while saving the user.";
            }
            showError("Save failed", message);
        }
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private void saveNewUser() {
        String email = normalize(emailField.getText()).toLowerCase();
        String role = toStoredRole(roleComboBox.getValue());
        String firstName = normalize(firstNameField.getText());
        String lastName = normalize(lastNameField.getText());
        String phone = normalize(phoneField.getText());
        String description = normalize(descriptionArea.getText());

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setRole(role);
        newUser.setName(firstName + " " + lastName);
        newUser.setPhone(blankToNull(phone));
        newUser.setAbout(blankToNull(description));
        newUser.setFaceEnabled((byte) 0);

        User createdUser = userService.createUser(newUser, generatedTempPassword);
        profileService.createProfile(createdUser, firstName, lastName, phone, description);

        if (sendWelcomeEmailCheckBox.isSelected()) {
            emailService.sendWelcomeEmail(createdUser, generatedTempPassword);
        }
    }

    private void saveExistingUser() {
        if (editingUser == null || editingUser.getId() == null) {
            throw new IllegalStateException("No user is loaded for edit mode");
        }

        User userToUpdate = userService.getUserById(editingUser.getId().longValue())
                .orElseThrow(() -> new IllegalStateException("User no longer exists"));

        String firstName = normalize(firstNameField.getText());
        String lastName = normalize(lastNameField.getText());
        String phone = normalize(phoneField.getText());
        String description = normalize(descriptionArea.getText());

        userToUpdate.setRole(toStoredRole(roleComboBox.getValue()));
        userToUpdate.setName(firstName + " " + lastName);
        userToUpdate.setPhone(blankToNull(phone));
        userToUpdate.setAbout(blankToNull(description));
        userToUpdate.setIsActive("Active".equalsIgnoreCase(statusComboBox.getValue()) ? (byte) 1 : (byte) 0);

        User updatedUser = userService.updateUser(userToUpdate);
        upsertProfile(updatedUser, firstName, lastName, phone, description);

        editingUser = updatedUser;
    }

    private void upsertProfile(User user, String firstName, String lastName, String phone, String description) {
        Profile profile = editingProfile != null ? editingProfile : loadProfile(user);

        if (profile == null) {
            editingProfile = profileService.createProfile(user, firstName, lastName, phone, description);
            return;
        }

        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setPhone(blankToNull(phone));
        profile.setDescription(blankToNull(description));

        editingProfile = profileService.updateProfile(profile);
    }

    private List<String> validateForm() {
        List<String> errors = new ArrayList<>();

        String email = normalize(emailField.getText());
        if (email == null) {
            errors.add("Email is required.");
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            errors.add("Email format is invalid.");
        }

        if (!editMode && email != null) {
            Optional<User> existingUser = userService.getUserByEmail(email);
            if (existingUser.isPresent()) {
                errors.add("Email is already used by another account.");
            }
        }

        if (roleComboBox.getValue() == null || roleComboBox.getValue().isBlank()) {
            errors.add("Role is required.");
        }

        if (normalize(firstNameField.getText()) == null) {
            errors.add("First name is required.");
        }

        if (normalize(lastNameField.getText()) == null) {
            errors.add("Last name is required.");
        }

        String phone = normalize(phoneField.getText());
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            errors.add("Phone number is invalid. Use 7-20 characters with digits and +()- only.");
        }

        if (!editMode && normalize(tempPasswordField.getText()) == null) {
            errors.add("Temporary password is required in create mode.");
        }

        if (editMode && (statusComboBox.getValue() == null || statusComboBox.getValue().isBlank())) {
            errors.add("Status is required in edit mode.");
        }

        return errors;
    }

    private void showValidationErrors(List<String> errors) {
        StringBuilder message = new StringBuilder();
        for (String error : errors) {
            message.append("- ").append(error).append(System.lineSeparator());
        }

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation errors");
        alert.setHeaderText("Please fix the following fields:");
        alert.setContentText(message.toString());
        alert.showAndWait();
    }

    private void updateModeUi() {
        boolean createMode = !editMode;

        emailField.setDisable(editMode);

        tempPasswordRow.setManaged(createMode);
        tempPasswordRow.setVisible(createMode);

        sendWelcomeEmailCheckBox.setManaged(createMode);
        sendWelcomeEmailCheckBox.setVisible(createMode);

        statusRow.setManaged(editMode);
        statusRow.setVisible(editMode);
    }

    private void clearForm() {
        emailField.clear();
        roleComboBox.getSelectionModel().clearSelection();
        firstNameField.clear();
        lastNameField.clear();
        phoneField.clear();
        descriptionArea.clear();
        tempPasswordField.clear();
        statusComboBox.getSelectionModel().clearSelection();
    }

    private void generateTempPassword() {
        generatedTempPassword = passwordManagementService.generateTemporaryPassword();
        tempPasswordField.setText(generatedTempPassword);
    }

    private User resolveFreshUser(User user) {
        if (user.getId() == null) {
            return user;
        }

        return userService.getUserById(user.getId().longValue()).orElse(user);
    }

    private Profile loadProfile(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }

        try {
            return profileService.getProfileByUser(user);
        } catch (Exception exception) {
            return null;
        }
    }

    private String resolveFirstName(User user, Profile profile) {
        if (profile != null && profile.getFirstName() != null) {
            return profile.getFirstName();
        }

        String fullName = normalize(user.getName());
        if (fullName == null) {
            return "";
        }

        String[] parts = fullName.split("\\s+", 2);
        return parts[0];
    }

    private String resolveLastName(User user, Profile profile) {
        if (profile != null && profile.getLastName() != null) {
            return profile.getLastName();
        }

        String fullName = normalize(user.getName());
        if (fullName == null) {
            return "";
        }

        String[] parts = fullName.split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    private String resolvePhone(User user, Profile profile) {
        if (profile != null && profile.getPhone() != null) {
            return profile.getPhone();
        }
        return safeText(user.getPhone());
    }

    private String resolveDescription(User user, Profile profile) {
        if (profile != null && profile.getDescription() != null) {
            return profile.getDescription();
        }
        return safeText(user.getAbout());
    }

    private String toStoredRole(String uiRole) {
        if (uiRole == null || uiRole.isBlank()) {
            return null;
        }

        String normalizedRole = uiRole.trim().toUpperCase();
        return normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole;
    }

    private String toDisplayRole(String storedRole) {
        if (storedRole == null || storedRole.isBlank()) {
            return "";
        }

        String normalizedRole = storedRole.trim().toUpperCase();
        if (normalizedRole.startsWith("ROLE_")) {
            return normalizedRole.substring("ROLE_".length());
        }

        return normalizedRole;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToNull(String value) {
        return normalize(value);
    }

    private void closeWindow() {
        Stage stage = (Stage) emailField.getScene().getWindow();
        stage.close();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
