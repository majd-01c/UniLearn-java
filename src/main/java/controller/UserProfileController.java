package controller;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import security.UserSession;
import service.faceid.CameraCaptureResult;
import service.faceid.CameraService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import service.faceid.FaceEnrollmentResult;
import service.faceid.FaceRecognitionService;
import service.UserService;
import util.AppNavigator;

import com.github.sarxos.webcam.Webcam;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.net.URL;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class UserProfileController implements Initializable {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final DateTimeFormatter FACE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private Label roleLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField nameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField locationField;

    @FXML
    private Button saveProfileButton;

    @FXML
    private Button backToDashboardButton;

    @FXML
    private Button logoutButton;

    @FXML
    private CheckBox faceIdEnabledCheckBox;

    @FXML
    private Label faceEnrollmentStatusLabel;

    @FXML
    private Label faceEnrolledAtLabel;

    @FXML
    private Button faceEnrollUploadButton;

    @FXML
    private Button faceEnrollCameraButton;

    @FXML
    private Button faceRemoveEnrollmentButton;

    @FXML
    private ProgressIndicator faceProcessingIndicator;

    @FXML
    private Label faceMessageLabel;

    @FXML
    private Label messageLabel;

    private final UserService userService = new UserService();
    private final FaceRecognitionService faceRecognitionService = new FaceRecognitionService();
    private final CameraService cameraService = new CameraService();

    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messageLabel.setText("");
        if (faceMessageLabel != null) {
            faceMessageLabel.setText("");
        }
        loadCurrentUserFromSession();
    }

    public void setCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            loadCurrentUserFromSession();
            return;
        }

        currentUser = userService.getUserById(user.getId().longValue()).orElse(user);
        bindUser();
        bindFaceSettings();
    }

    @FXML
    private void handleSaveProfile() {
        if (currentUser == null || currentUser.getId() == null) {
            setMessage("No active session user. Please login again.", true);
            return;
        }

        String name = normalize(nameField.getText());
        String email = normalize(emailField.getText());
        String phone = normalize(phoneField.getText());
        String location = normalize(locationField.getText());

        if (name == null) {
            setMessage("Name is required.", true);
            return;
        }

        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            setMessage("A valid email is required.", true);
            return;
        }

        Optional<User> existing = userService.getUserByEmail(email);
        if (existing.isPresent() && !existing.get().getId().equals(currentUser.getId())) {
            setMessage("This email is already used by another account.", true);
            return;
        }

        try {
            currentUser.setName(name);
            currentUser.setEmail(email.toLowerCase());
            currentUser.setPhone(blankToNull(phone));
            currentUser.setLocation(blankToNull(location));

            currentUser = userService.updateUser(currentUser);
            UserSession.setCurrentUser(currentUser);

            setMessage("Profile updated successfully.", false);
            bindUser();
        } catch (Exception exception) {
            setMessage(safeErrorMessage(exception), true);
        }
    }

    @FXML
    private void handleBackToDashboard() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleLogout() {
        AppNavigator.logout();
    }

    @FXML
    private void handleFaceIdToggle() {
        if (!ensureCurrentUserForFace()) {
            return;
        }

        try {
            boolean enable = faceIdEnabledCheckBox != null && faceIdEnabledCheckBox.isSelected();
            currentUser = faceRecognitionService.setFaceIdEnabled(currentUser, enable);
            UserSession.setCurrentUser(currentUser);

            if (enable && !faceRecognitionService.isFaceEnrolled(currentUser)) {
                setFaceMessage("Face ID enabled. Please enroll your face to use it during login.", false);
            } else {
                setFaceMessage(enable ? "Face ID enabled for your account." : "Face ID disabled for your account.", false);
            }

            bindFaceSettings();
        } catch (Exception exception) {
            setFaceMessage(safeErrorMessage(exception), true);
            bindFaceSettings();
        }
    }

    @FXML
    private void handleEnrollFaceWithUpload() {
        if (!ensureCurrentUserForFace()) {
            return;
        }

        File imageFile = chooseFaceImageFile("Select face image for enrollment");
        if (imageFile == null) {
            return;
        }

        setFaceProcessing(true);
        enrollFaceFromFile(imageFile);
    }

    @FXML
    private void handleEnrollFaceWithCamera() {
        if (!ensureCurrentUserForFace()) {
            return;
        }

        // Show an interactive camera preview so users can confirm before capture.
        try {
            showCameraPreviewAndCapture();
        } catch (Exception e) {
            // Fallback to single-frame capture if preview cannot be opened
            setFaceProcessing(true);
            CameraCaptureResult captureResult = cameraService.captureToTempImage();

            if (!captureResult.success() || captureResult.imagePath() == null) {
                setFaceMessage(captureResult.reason(), true);
                setFaceProcessing(false);
                return;
            }

            enrollFaceFromFile(
                    captureResult.imagePath().toFile(),
                    () -> cameraService.cleanupCapturedFile(captureResult.imagePath())
            );
        }
    }

    /**
     * Opens a modal with live camera preview and allows the user to capture an image.
     * Captured image is stored to a temporary file and enrolled using existing flow.
     */
    private void showCameraPreviewAndCapture() {
        List<Webcam> webcams = null;
        try {
            webcams = Webcam.getWebcams();
        } catch (Throwable ignored) {}

        if (webcams == null || webcams.isEmpty()) {
            throw new IllegalStateException("No camera available for preview");
        }

        Webcam webcam = webcams.get(0);
        // Try to configure a reasonable resolution if supported
        try {
            if (webcam.getViewSizes() != null && webcam.getViewSizes().length > 0) {
                webcam.setViewSize(webcam.getViewSizes()[0]);
            }
        } catch (Throwable ignored) {}

        final ImageView preview = new ImageView();
        preview.setFitWidth(640);
        preview.setFitHeight(480);
        preview.setPreserveRatio(true);

        Button captureBtn = new Button("Capture");
        Button cancelBtn = new Button("Cancel");

        HBox actions = new HBox(10, captureBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER);

        VBox root = new VBox(8, preview, actions);
        root.setStyle("-fx-padding: 12; -fx-background-color: #111;");

        Scene scene = new Scene(root);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Camera Preview — Align your face and press Capture");
        stage.setScene(scene);

        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        try {
            webcam.open(true);

            // Periodically grab frames and update the ImageView
            Runnable grabFrame = () -> {
                try {
                    BufferedImage img = webcam.getImage();
                    if (img != null) {
                        WritableImage fxImg = SwingFXUtils.toFXImage(img, null);
                        Platform.runLater(() -> preview.setImage(fxImg));
                    }
                } catch (Throwable t) {
                    // ignore individual frame errors
                }
            };

            executor.scheduleAtFixedRate(grabFrame, 0, 100, TimeUnit.MILLISECONDS);

            captureBtn.setOnAction(evt -> {
                setFaceProcessing(true);
                try {
                    BufferedImage img = webcam.getImage();
                    if (img == null) {
                        setFaceMessage("Failed to capture image from camera.", true);
                        return;
                    }

                    Path tempFile = Files.createTempFile("unilearn-face-capture-", ".png");
                    ImageIO.write(img, "png", tempFile.toFile());

                    // Close preview and executor before continuing
                    try { executor.shutdownNow(); } catch (Exception ignored) {}
                    try { if (webcam.isOpen()) webcam.close(); } catch (Exception ignored) {}
                    stage.close();

                    enrollFaceFromFile(tempFile.toFile(), () -> {
                        try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                    });
                } catch (Exception ex) {
                    setFaceMessage(safeErrorMessage(new Exception(ex)), true);
                    setFaceProcessing(false);
                }
            });

            cancelBtn.setOnAction(evt -> {
                try { executor.shutdownNow(); } catch (Exception ignored) {}
                try { if (webcam.isOpen()) webcam.close(); } catch (Exception ignored) {}
                stage.close();
                setFaceProcessing(false);
            });

            // Show preview modal
            stage.showAndWait();
        } catch (Exception e) {
            try { executor.shutdownNow(); } catch (Exception ignored) {}
            try { if (webcam.isOpen()) webcam.close(); } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void handleRemoveFaceEnrollment() {
        if (!ensureCurrentUserForFace()) {
            return;
        }

        setFaceProcessing(true);
        try {
            currentUser = faceRecognitionService.clearEnrollment(currentUser);
            UserSession.setCurrentUser(currentUser);
            setFaceMessage("Face enrollment removed. Password login remains available.", false);
            bindFaceSettings();
        } catch (Exception exception) {
            setFaceMessage(safeErrorMessage(exception), true);
        } finally {
            setFaceProcessing(false);
        }
    }

    private void loadCurrentUserFromSession() {
        Optional<Integer> userId = UserSession.getCurrentUserId();
        if (userId.isEmpty()) {
            setMessage("No active session user. Please login.", true);
            disableForm(true);
            return;
        }

        currentUser = userService.getUserById(userId.get().longValue()).orElse(null);
        if (currentUser == null) {
            setMessage("Session user not found. Please login again.", true);
            disableForm(true);
            return;
        }

        disableForm(false);
        bindUser();
        bindFaceSettings();
    }

    private void bindUser() {
        if (currentUser == null) {
            return;
        }

        roleLabel.setText(formatRole(currentUser.getRole()));
        boolean active = currentUser.getIsActive() == (byte) 1;
        statusLabel.setText(active ? "Active" : "Inactive");
        statusLabel.getStyleClass().removeAll("user-profile-status-active", "user-profile-status-inactive");
        statusLabel.getStyleClass().add(active ? "user-profile-status-active" : "user-profile-status-inactive");

        nameField.setText(safeText(currentUser.getName()));
        emailField.setText(safeText(currentUser.getEmail()));
        phoneField.setText(safeText(currentUser.getPhone()));
        locationField.setText(safeText(currentUser.getLocation()));
    }

    private void bindFaceSettings() {
        if (faceIdEnabledCheckBox == null || currentUser == null) {
            return;
        }

        boolean enrolled = faceRecognitionService.isFaceEnrolled(currentUser);
        boolean enabled = currentUser.isFaceIdEnabled();

        faceIdEnabledCheckBox.setSelected(enabled);

        if (faceEnrollmentStatusLabel != null) {
            faceEnrollmentStatusLabel.getStyleClass().removeAll("user-faceid-status-enrolled", "user-faceid-status-missing");
            faceEnrollmentStatusLabel.setText(enrolled ? "Enrolled" : "Not enrolled");
            faceEnrollmentStatusLabel.getStyleClass().add(enrolled ? "user-faceid-status-enrolled" : "user-faceid-status-missing");
        }

        if (faceEnrolledAtLabel != null) {
            faceEnrolledAtLabel.setText(formatTimestamp(currentUser.getFaceEnrolledAt()));
        }

        if (faceRemoveEnrollmentButton != null) {
            faceRemoveEnrollmentButton.setDisable(!enrolled);
        }

        if (faceEnrollCameraButton != null) {
            faceEnrollCameraButton.setText(cameraService.isCameraAvailable()
                    ? "Enroll/Re-enroll (Camera)"
                    : "Camera Unavailable (Use Upload)");
        }
    }

    private void disableForm(boolean disable) {
        nameField.setDisable(disable);
        emailField.setDisable(disable);
        phoneField.setDisable(disable);
        locationField.setDisable(disable);
        saveProfileButton.setDisable(disable);

        if (faceIdEnabledCheckBox != null) {
            faceIdEnabledCheckBox.setDisable(disable);
        }
        if (faceEnrollUploadButton != null) {
            faceEnrollUploadButton.setDisable(disable);
        }
        if (faceEnrollCameraButton != null) {
            faceEnrollCameraButton.setDisable(disable);
        }
        if (faceRemoveEnrollmentButton != null) {
            faceRemoveEnrollmentButton.setDisable(disable || !faceRecognitionService.isFaceEnrolled(currentUser));
        }
    }

    private boolean ensureCurrentUserForFace() {
        if (currentUser != null && currentUser.getId() != null) {
            return true;
        }

        setFaceMessage("No active session user. Please login again.", true);
        return false;
    }

    private void enrollFaceFromFile(File imageFile) {
        enrollFaceFromFile(imageFile, null);
    }

    private void enrollFaceFromFile(File imageFile, Runnable cleanupAction) {
        setFaceProcessing(true);

        Task<FaceEnrollmentResult> task = new Task<>() {
            @Override
            protected FaceEnrollmentResult call() throws Exception {
                return faceRecognitionService.enrollFace(currentUser, imageFile);
            }
        };

        task.setOnSucceeded(evt -> {
            FaceEnrollmentResult result = task.getValue();
            if (!result.success() || result.user() == null) {
                String reason = result.reason() == null ? "Enrollment failed" : result.reason();

                // If the failure is due to quality gate and bypass is allowed, offer retry or continue anyway
                boolean qualityFailure = reason.toLowerCase().contains("face quality");
                boolean allowBypass = faceRecognitionService.isQualityGateBypassAllowed();

                if (qualityFailure && allowBypass) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Photo Quality Check Failed");
                        alert.setHeaderText("Photo did not pass the quality checks");
                        alert.setContentText(reason + "\n\nWould you like to try another photo or continue anyway?");

                        ButtonType retryBtn = new ButtonType("Try Another Photo", ButtonBar.ButtonData.CANCEL_CLOSE);
                        ButtonType continueBtn = new ButtonType("Continue Anyway", ButtonBar.ButtonData.OK_DONE);
                        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                        alert.getButtonTypes().setAll(retryBtn, continueBtn, cancelBtn);

                        ButtonType choice = alert.showAndWait().orElse(cancelBtn);
                        if (choice == continueBtn) {
                            // Force enrollment skipping quality gate
                            Task<FaceEnrollmentResult> forceTask = new Task<>() {
                                @Override
                                protected FaceEnrollmentResult call() throws Exception {
                                    return faceRecognitionService.enrollFaceSkippingQuality(currentUser, imageFile);
                                }
                            };

                            forceTask.setOnSucceeded(e2 -> {
                                FaceEnrollmentResult r2 = forceTask.getValue();
                                if (!r2.success() || r2.user() == null) {
                                    setFaceMessage(r2.reason(), true);
                                } else {
                                    currentUser = r2.user();
                                    if (!currentUser.isFaceIdEnabled()) {
                                        currentUser = faceRecognitionService.setFaceIdEnabled(currentUser, true);
                                    }
                                    UserSession.setCurrentUser(currentUser);
                                    setFaceMessage("Face enrollment successful. Face ID is now enabled.", false);
                                    bindFaceSettings();
                                }
                                cleanupEnrollmentImage(cleanupAction);
                                setFaceProcessing(false);
                            });

                            forceTask.setOnFailed(e2 -> {
                                setFaceMessage(safeErrorMessage(new Exception(forceTask.getException())), true);
                                cleanupEnrollmentImage(cleanupAction);
                                setFaceProcessing(false);
                            });

                            new Thread(forceTask).start();
                        } else if (choice == retryBtn) {
                            // Let user try another photo; simply clear message
                            setFaceMessage("Please select another photo and try again.", false);
                            cleanupEnrollmentImage(cleanupAction);
                            setFaceProcessing(false);
                        } else {
                            cleanupEnrollmentImage(cleanupAction);
                            setFaceProcessing(false);
                        }
                    });
                } else {
                    setFaceMessage(reason, true);
                    cleanupEnrollmentImage(cleanupAction);
                    setFaceProcessing(false);
                }

                return;
            }

            currentUser = result.user();
            if (!currentUser.isFaceIdEnabled()) {
                currentUser = faceRecognitionService.setFaceIdEnabled(currentUser, true);
            }

            UserSession.setCurrentUser(currentUser);
            setFaceMessage("Face enrollment successful. Face ID is now enabled.", false);
            bindFaceSettings();
            cleanupEnrollmentImage(cleanupAction);
            setFaceProcessing(false);
        });

        task.setOnFailed(evt -> {
            setFaceMessage(safeErrorMessage(new Exception(task.getException())), true);
            cleanupEnrollmentImage(cleanupAction);
            setFaceProcessing(false);
        });

        new Thread(task).start();
    }

    private void cleanupEnrollmentImage(Runnable cleanupAction) {
        if (cleanupAction == null) {
            return;
        }

        try {
            cleanupAction.run();
        } catch (Exception exception) {
            setFaceMessage("Enrollment finished, but temporary camera image cleanup failed.", true);
        }
    }

    private void setFaceProcessing(boolean processing) {
        if (faceProcessingIndicator != null) {
            faceProcessingIndicator.setManaged(processing);
            faceProcessingIndicator.setVisible(processing);
        }

        if (faceIdEnabledCheckBox != null) {
            faceIdEnabledCheckBox.setDisable(processing);
        }
        if (faceEnrollUploadButton != null) {
            faceEnrollUploadButton.setDisable(processing);
        }
        if (faceEnrollCameraButton != null) {
            faceEnrollCameraButton.setDisable(processing);
        }
        if (faceRemoveEnrollmentButton != null) {
            faceRemoveEnrollmentButton.setDisable(processing || !faceRecognitionService.isFaceEnrolled(currentUser));
        }
    }

    private File chooseFaceImageFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        return chooser.showOpenDialog(saveProfileButton != null && saveProfileButton.getScene() != null
                ? saveProfileButton.getScene().getWindow()
                : null);
    }

    private String formatRole(String role) {
        if (role == null || role.isBlank()) {
            return "-";
        }

        String normalized = role.toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
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

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return FACE_TIME_FORMATTER.format(timestamp.toLocalDateTime());
    }

    private void setMessage(String message, boolean error) {
        messageLabel.getStyleClass().removeAll("form-feedback-error", "form-feedback-success");
        messageLabel.getStyleClass().add(error ? "form-feedback-error" : "form-feedback-success");
        messageLabel.setText(message);
    }

    private void setFaceMessage(String message, boolean error) {
        if (faceMessageLabel == null) {
            return;
        }

        faceMessageLabel.getStyleClass().removeAll("form-feedback-error", "form-feedback-success");
        faceMessageLabel.getStyleClass().add(error ? "form-feedback-error" : "form-feedback-success");
        faceMessageLabel.setText(safeText(message));
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Unable to update profile" : message;
    }
}
