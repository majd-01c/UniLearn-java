package controller;

import entities.Profile;
import entities.User;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import service.AuthenticationService;
import service.ProfileService;
import service.UserDetailsService;
import service.UserService;
import util.AppNavigator;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class UserDetailsController implements Initializable {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private Label emailValueLabel;

    @FXML
    private Label roleValueLabel;

    @FXML
    private Label statusValueLabel;

    @FXML
    private Label verificationStatusValueLabel;

    @FXML
    private Label lastLoginValueLabel;

    @FXML
    private Label firstNameValueLabel;

    @FXML
    private Label lastNameValueLabel;

    @FXML
    private Label phoneValueLabel;

    @FXML
    private Label descriptionValueLabel;

    @FXML
    private Label createdAtValueLabel;

    @FXML
    private Label updatedAtValueLabel;

    @FXML
    private Label eventsCountLabel;

    @FXML
    private Label gradesCountLabel;

    @FXML
    private ListView<String> eventsListView;

    @FXML
    private ListView<String> gradesListView;

    @FXML
    private ImageView profileImageView;

    @FXML
    private Label profileImageStatusLabel;

    private final UserService userService = new UserService();
    private final ProfileService profileService = new ProfileService();
    private final UserDetailsService userDetailsService = new UserDetailsService();
    private final AuthenticationService authenticationService = new AuthenticationService();

    private User currentUser;
    private Profile currentProfile;
    private Runnable onDataChanged;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        eventsListView.setItems(FXCollections.observableArrayList());
        gradesListView.setItems(FXCollections.observableArrayList());
    }

    public void setOnDataChanged(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    public void setUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A valid user is required");
        }

        currentUser = userService.getUserById(user.getId().longValue())
                .orElseThrow(() -> new IllegalStateException("User was not found"));

        currentProfile = loadProfile(currentUser);
        bindUserData();
        loadRelatedData();
        loadProfilePicture();
    }

    @FXML
    private void onEdit() {
        if (currentUser == null) {
            showWarning("No user selected", "Cannot edit because user details are not loaded.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user/user-form.fxml"));
            Parent root = loader.load();

            UserFormController formController = loader.getController();
            formController.setOnSaveSuccess(() -> {
                reloadCurrentUser();
                if (onDataChanged != null) {
                    onDataChanged.run();
                }
            });
            formController.setEditMode(currentUser);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit User");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(emailValueLabel.getScene().getWindow());
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();
        } catch (IOException exception) {
            showError("Open form failed", "Could not open edit form: " + exception.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        if (currentUser == null || currentUser.getId() == null) {
            showWarning("No user selected", "Cannot delete because user details are not loaded.");
            return;
        }

        boolean confirmed = showConfirmation(
                "Delete user",
                "Delete selected user",
                "Are you sure you want to delete " + currentUser.getEmail() + "?"
        );
        if (!confirmed) {
            return;
        }

        try {
            userService.deleteUser(currentUser.getId().longValue());
            if (onDataChanged != null) {
                onDataChanged.run();
            }
            showInfo("Deleted", "User deleted successfully.");
            AppNavigator.showUsers();
        } catch (Exception exception) {
            showError("Delete failed", safeMessage(exception));
        }
    }

    @FXML
    private void onGoBack() {
        AppNavigator.showUsers();
    }

    private void reloadCurrentUser() {
        if (currentUser == null || currentUser.getId() == null) {
            return;
        }

        Optional<User> refreshed = userService.getUserById(currentUser.getId().longValue());
        if (refreshed.isEmpty()) {
            AppNavigator.showUsers();
            return;
        }

        currentUser = refreshed.get();
        currentProfile = loadProfile(currentUser);

        bindUserData();
        loadRelatedData();
        loadProfilePicture();
    }

    private void bindUserData() {
        emailValueLabel.setText(safeText(currentUser.getEmail()));
        roleValueLabel.setText(toDisplayRole(currentUser.getRole()));
        statusValueLabel.setText(currentUser.getIsActive() == (byte) 1 ? "Active" : "Inactive");
        verificationStatusValueLabel.setText(buildVerificationStatus());
        lastLoginValueLabel.setText(resolveLastLogin());

        firstNameValueLabel.setText(resolveFirstName());
        lastNameValueLabel.setText(resolveLastName());
        phoneValueLabel.setText(resolvePhone());
        descriptionValueLabel.setText(blankAsDash(resolveDescription()));

        createdAtValueLabel.setText(formatTimestamp(currentUser.getCreatedAt()));
        updatedAtValueLabel.setText(formatTimestamp(currentUser.getUpdatedAt()));
    }

    private void loadRelatedData() {
        Integer userId = currentUser == null ? null : currentUser.getId();

        List<String> eventSummaries = userDetailsService.findCreatedEventSummaries(userId, 25);
        List<String> gradeSummaries = userDetailsService.findGradeSummaries(userId, 25);

        eventsListView.setItems(FXCollections.observableArrayList(
                eventSummaries.isEmpty() ? List.of("No events created.") : eventSummaries
        ));

        gradesListView.setItems(FXCollections.observableArrayList(
                gradeSummaries.isEmpty() ? List.of("No grades found for this user.") : gradeSummaries
        ));

        long eventsCount = userDetailsService.countEventsCreated(userId);
        long gradesAsStudent = userDetailsService.countGradesAsStudent(userId);
        long gradesAsTeacher = userDetailsService.countGradesAsTeacher(userId);

        eventsCountLabel.setText("Events Created: " + eventsCount);
        gradesCountLabel.setText("Grades: student=" + gradesAsStudent + " | teacher=" + gradesAsTeacher);
    }

    private void loadProfilePicture() {
        String imageSource = resolveImageSource();
        if (imageSource == null) {
            profileImageView.setImage(null);
            profileImageStatusLabel.setText("No profile picture available");
            return;
        }

        Image image = tryLoadImage(imageSource);
        if (image == null || image.isError()) {
            profileImageView.setImage(null);
            profileImageStatusLabel.setText("Profile picture could not be loaded");
            return;
        }

        profileImageView.setImage(image);
        profileImageStatusLabel.setText("Profile picture loaded");
    }

    private String resolveImageSource() {
        String fromProfilePhoto = normalize(currentProfile == null ? null : currentProfile.getPhoto());
        if (fromProfilePhoto != null) {
            return fromProfilePhoto;
        }

        String fromAvatar = normalize(currentProfile == null ? null : currentProfile.getAvatarFilename());
        if (fromAvatar != null) {
            return fromAvatar;
        }

        return normalize(currentUser == null ? null : currentUser.getProfilePic());
    }

    private Image tryLoadImage(String source) {
        try {
            if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("file:")) {
                return new Image(source, true);
            }

            File directFile = new File(source);
            if (directFile.exists()) {
                return new Image(directFile.toURI().toString(), true);
            }

            String classpathSource = source.startsWith("/") ? source : "/" + source;
            URL resource = getClass().getResource(classpathSource);
            if (resource != null) {
                return new Image(resource.toExternalForm(), true);
            }

            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String resolveFirstName() {
        if (currentProfile != null && normalize(currentProfile.getFirstName()) != null) {
            return currentProfile.getFirstName();
        }

        String fullName = normalize(currentUser == null ? null : currentUser.getName());
        if (fullName == null) {
            return "-";
        }

        String[] parts = fullName.split("\\s+", 2);
        return parts[0];
    }

    private String resolveLastName() {
        if (currentProfile != null && normalize(currentProfile.getLastName()) != null) {
            return currentProfile.getLastName();
        }

        String fullName = normalize(currentUser == null ? null : currentUser.getName());
        if (fullName == null) {
            return "-";
        }

        String[] parts = fullName.split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "-";
    }

    private String resolvePhone() {
        String profilePhone = normalize(currentProfile == null ? null : currentProfile.getPhone());
        if (profilePhone != null) {
            return profilePhone;
        }

        return blankAsDash(currentUser == null ? null : currentUser.getPhone());
    }

    private String resolveDescription() {
        String profileDescription = normalize(currentProfile == null ? null : currentProfile.getDescription());
        if (profileDescription != null) {
            return profileDescription;
        }

        return normalize(currentUser == null ? null : currentUser.getAbout());
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

    private String buildVerificationStatus() {
        if (currentUser == null) {
            return "-";
        }

        String verification = currentUser.getIsVerified() == (byte) 1 ? "Verified" : "Not Verified";
        String pending = currentUser.getNeedsVerification() == (byte) 1 ? "Pending verification" : "Verification not pending";
        String verifiedAt = formatTimestamp(currentUser.getEmailVerifiedAt());

        return verification + " | " + pending + " | Verified at: " + verifiedAt;
    }

    private String resolveLastLogin() {
        if (currentUser == null || currentUser.getEmail() == null) {
            return "Not available";
        }

        Optional<Instant> lastLoginAttempt = authenticationService.getLastLoginAttempt(currentUser.getEmail());
        if (lastLoginAttempt.isPresent()) {
            return DATE_TIME_FORMATTER.format(lastLoginAttempt.get().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        }

        return "Not available";
    }

    private String toDisplayRole(String role) {
        if (role == null || role.isBlank()) {
            return "-";
        }

        String normalizedRole = role.trim().toUpperCase();
        if (normalizedRole.startsWith("ROLE_")) {
            return normalizedRole.substring("ROLE_".length());
        }

        return normalizedRole;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }

        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankAsDash(String value) {
        String normalized = normalize(value);
        return normalized == null ? "-" : normalized;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Unexpected error while processing user details"
                : message;
    }

    private boolean showConfirmation(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
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
