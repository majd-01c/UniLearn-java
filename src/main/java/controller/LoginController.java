package controller;

import entities.User;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import security.UserSession;
import service.AuthenticationService;
import service.UserService;
import service.faceid.CameraCaptureResult;
import service.faceid.CameraService;
import service.faceid.FaceRecognitionService;
import service.faceid.FaceVerificationResult;
import util.AppNavigator;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.util.Duration;

public class LoginController implements Initializable {

    private static final String PREF_NODE = "unilearn.desktop.login";
    private static final String PREF_REMEMBERED_EMAIL = "rememberedEmail";
    private static final double TABLET_BREAKPOINT = 980.0;
    private static final double MOBILE_BREAKPOINT = 860.0;
    private static final double DESKTOP_MAX_CARD_WIDTH = 960.0;
    private static final double TABLET_MAX_CARD_WIDTH = 900.0;
    private static final double TABLET_MIN_CARD_WIDTH = 760.0;
    private static final double SIDE_PANEL_WIDTH = 338.0;
    private static final double SIDE_PANEL_TABLET_WIDTH = 300.0;
    private static final String LAYOUT_DESKTOP = "layout-desktop";
    private static final String LAYOUT_TABLET = "layout-tablet";
    private static final String LAYOUT_MOBILE = "layout-mobile";
    private static final String INPUT_ERROR_CLASS = "input-error";

    @FXML
    private BorderPane loginRoot;

    @FXML
    private StackPane loginStage;

    @FXML
    private HBox loginShell;

    @FXML
    private VBox loginFormPanel;

    @FXML
    private VBox loginSidePanel;

    @FXML
    private Region loginDivider;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField passwordVisibleField;

    @FXML
    private Button loginButton;

    @FXML
    private Button faceCameraLoginButton;

    @FXML
    private Button faceUploadLoginButton;

    @FXML
    private Button continuePasswordButton;

    @FXML
    private Button passwordToggleButton;

    @FXML
    private CheckBox rememberMeCheckBox;

    @FXML
    private Hyperlink forgotPasswordLink;

    @FXML
    private Label messageLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();
    private final UserService userService = new UserService();
    private final FaceRecognitionService faceRecognitionService = new FaceRecognitionService();
    private final CameraService cameraService = new CameraService();
    private final Preferences preferences = Preferences.userRoot().node(PREF_NODE);

    private boolean passwordVisible;
    private boolean loading;
    private boolean enterAnimationPlayed;

    private final ChangeListener<Number> responsiveSizeListener =
            (observable, oldValue, newValue) -> applyResponsiveLayout();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        clearMessage();
        loadRememberedEmail();

        if (passwordVisibleField != null && passwordField != null) {
            passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        }

        setPasswordVisibility(false);
        loginButton.setDefaultButton(true);
        installMicroInteractions();
        installResponsiveBehavior();
        playEnterAnimation();
        clearInputErrors();

        emailField.textProperty().addListener((obs, oldValue, newValue) -> {
            clearFieldError(emailField);
            clearMessage();
        });
        passwordField.textProperty().addListener((obs, oldValue, newValue) -> {
            clearFieldError(passwordField);
            clearFieldError(passwordVisibleField);
            clearMessage();
        });
        passwordVisibleField.textProperty().addListener((obs, oldValue, newValue) -> {
            clearFieldError(passwordField);
            clearFieldError(passwordVisibleField);
            clearMessage();
        });
    }

    @FXML
    private void handleLogin() {
        if (loading) {
            return;
        }

        String email = normalize(emailField.getText());
        String password = normalize(resolvePasswordInput());

        if (!validateInputs(email, password)) {
            return;
        }

        setLoading(true);
        try {
            User authenticated = authenticate(email, password);
            UserSession.setCurrentUser(authenticated);
            persistRememberedEmail(email);
            setMessage("Welcome " + safeText(authenticated.getEmail()), false);
            routeByRole(authenticated);
        } catch (Exception exception) {
            setMessage(safeErrorMessage(exception), true);
        } finally {
            setLoading(false);
        }
    }

    @FXML
    private void handleTogglePasswordVisibility() {
        setPasswordVisibility(!passwordVisible);
    }

    @FXML
    private void handleForgotPassword() {
        AppNavigator.showPasswordResetRequest();
    }

    @FXML
    private void handleLoginWithFaceCamera() {
        if (loading) {
            return;
        }

        Optional<User> userOptional = resolveFaceLoginUser();
        if (userOptional.isEmpty()) {
            return;
        }

        if (!cameraService.isCameraAvailable()) {
            setMessage("Camera unavailable, use upload image instead.", true);
            return;
        }

        setLoading(true);
        CameraCaptureResult captureResult = cameraService.captureToTempImage();
        Path tempImagePath = captureResult.imagePath();

        try {
            if (!captureResult.success() || tempImagePath == null) {
                setMessage(captureResult.reason(), true);
                return;
            }

            FaceVerificationResult verification = faceRecognitionService.verifyFace(userOptional.get(), tempImagePath.toFile());
            handleFaceVerificationResult(userOptional.get(), verification);
        } finally {
            cameraService.cleanupCapturedFile(tempImagePath);
            setLoading(false);
        }
    }

    @FXML
    private void handleLoginWithFaceUpload() {
        if (loading) {
            return;
        }

        Optional<User> userOptional = resolveFaceLoginUser();
        if (userOptional.isEmpty()) {
            return;
        }

        File selectedImage = chooseFaceImageFile("Select face image for login");
        if (selectedImage == null) {
            return;
        }

        setLoading(true);
        try {
            FaceVerificationResult verification = faceRecognitionService.verifyFace(userOptional.get(), selectedImage);
            handleFaceVerificationResult(userOptional.get(), verification);
        } finally {
            setLoading(false);
        }
    }

    @FXML
    private void handleContinueWithPassword() {
        if (passwordVisible) {
            passwordVisibleField.requestFocus();
            return;
        }

        passwordField.requestFocus();
    }

    private void setMessage(String text, boolean isError) {
        messageLabel.getStyleClass().removeAll("form-feedback-error", "form-feedback-success");
        messageLabel.getStyleClass().add(isError ? "form-feedback-error" : "form-feedback-success");
        messageLabel.setText(text);
    }

    private boolean validateInputs(String email, String password) {
        clearInputErrors();

        if (email == null || password == null) {
            setMessage("Email and password are required.", true);

            if (email == null) {
                markFieldError(emailField);
            }
            if (password == null) {
                markFieldError(passwordField);
                markFieldError(passwordVisibleField);
            }
            return false;
        }

        if (!email.contains("@") || !email.contains(".")) {
            setMessage("Enter a valid email address.", true);
            markFieldError(emailField);
            return false;
        }

        return true;
    }

    private User authenticate(String email, String password) {
        return authenticationService.authenticate(email, password);
    }

    private void routeByRole(User authenticatedUser) {
        AppNavigator.loginSuccess(authenticatedUser);
    }

    private String resolvePasswordInput() {
        return passwordVisible ? passwordVisibleField.getText() : passwordField.getText();
    }

    private void setPasswordVisibility(boolean visible) {
        passwordVisible = visible;

        if (passwordField != null) {
            passwordField.setManaged(!visible);
            passwordField.setVisible(!visible);
        }
        if (passwordVisibleField != null) {
            passwordVisibleField.setManaged(visible);
            passwordVisibleField.setVisible(visible);
        }
        if (passwordToggleButton != null) {
            passwordToggleButton.setText(visible ? "Hide" : "Show");
            passwordToggleButton.setAccessibleText(visible
                    ? "Hide password"
                    : "Show password");
        }
    }

    private void setLoading(boolean isLoading) {
        loading = isLoading;

        if (loginButton != null) {
            loginButton.setDisable(isLoading);
            updateStyleClass(loginButton.getStyleClass(), "is-loading", isLoading);
            loginButton.setText(isLoading ? "Signing in..." : "Sign In");
        }

        if (emailField != null) {
            emailField.setDisable(isLoading);
        }
        if (passwordField != null) {
            passwordField.setDisable(isLoading);
        }
        if (passwordVisibleField != null) {
            passwordVisibleField.setDisable(isLoading);
        }
        if (rememberMeCheckBox != null) {
            rememberMeCheckBox.setDisable(isLoading);
        }
        if (passwordToggleButton != null) {
            passwordToggleButton.setDisable(isLoading);
        }
        if (faceCameraLoginButton != null) {
            faceCameraLoginButton.setDisable(isLoading);
        }
        if (faceUploadLoginButton != null) {
            faceUploadLoginButton.setDisable(isLoading);
        }
        if (continuePasswordButton != null) {
            continuePasswordButton.setDisable(isLoading);
        }
        if (forgotPasswordLink != null) {
            forgotPasswordLink.setDisable(isLoading);
        }
    }

    private void clearMessage() {
        if (messageLabel != null) {
            messageLabel.setText("");
            messageLabel.getStyleClass().removeAll("form-feedback-error", "form-feedback-success");
        }
    }

    private void markFieldError(TextInputControl field) {
        if (field == null) {
            return;
        }

        ObservableList<String> styleClasses = field.getStyleClass();
        if (!styleClasses.contains(INPUT_ERROR_CLASS)) {
            styleClasses.add(INPUT_ERROR_CLASS);
        }
    }

    private void clearFieldError(TextInputControl field) {
        if (field != null) {
            field.getStyleClass().remove(INPUT_ERROR_CLASS);
        }
    }

    private void clearInputErrors() {
        clearFieldError(emailField);
        clearFieldError(passwordField);
        clearFieldError(passwordVisibleField);
    }

    private void installResponsiveBehavior() {
        if (loginRoot == null) {
            return;
        }

        loginRoot.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.widthProperty().removeListener(responsiveSizeListener);
                oldScene.heightProperty().removeListener(responsiveSizeListener);
            }

            if (newScene != null) {
                newScene.widthProperty().addListener(responsiveSizeListener);
                newScene.heightProperty().addListener(responsiveSizeListener);
                applyResponsiveLayout(newScene);
            }
        });
    }

    private void applyResponsiveLayout() {
        if (loginRoot == null || loginRoot.getScene() == null) {
            return;
        }

        applyResponsiveLayout(loginRoot.getScene());
    }

    private void applyResponsiveLayout(Scene scene) {
        if (scene == null || loginShell == null || loginFormPanel == null || loginSidePanel == null) {
            return;
        }

        double sceneWidth = Math.max(scene.getWidth(), 360.0);
        double cardWidth;
        String modeClass;

        if (sceneWidth < MOBILE_BREAKPOINT) {
            modeClass = LAYOUT_MOBILE;
            cardWidth = Math.max(312.0, sceneWidth - 32.0);
            setSecondaryPanelVisible(false);
        } else if (sceneWidth < TABLET_BREAKPOINT) {
            modeClass = LAYOUT_TABLET;
            cardWidth = Math.max(TABLET_MIN_CARD_WIDTH, Math.min(TABLET_MAX_CARD_WIDTH, sceneWidth - 32.0));
            setSecondaryPanelVisible(true);
        } else {
            modeClass = LAYOUT_DESKTOP;
            cardWidth = Math.max(920.0, Math.min(DESKTOP_MAX_CARD_WIDTH, sceneWidth - 32.0));
            setSecondaryPanelVisible(true);
        }

        applyLayoutClass(loginShell, modeClass);
        applyLayoutClass(loginStage, modeClass);

        loginShell.setPrefWidth(cardWidth);
        loginShell.setMaxWidth(cardWidth);

        if (modeClass.equals(LAYOUT_DESKTOP)) {
            loginShell.setPrefHeight(560.0);
            double formWidth = Math.max(560.0, cardWidth - SIDE_PANEL_WIDTH - 1.0);
            loginFormPanel.setPrefWidth(formWidth);
            loginSidePanel.setPrefWidth(SIDE_PANEL_WIDTH);
        } else if (modeClass.equals(LAYOUT_TABLET)) {
            loginShell.setPrefHeight(Region.USE_COMPUTED_SIZE);
            double formWidth = Math.max(440.0, cardWidth - SIDE_PANEL_TABLET_WIDTH - 1.0);
            loginFormPanel.setPrefWidth(formWidth);
            loginSidePanel.setPrefWidth(SIDE_PANEL_TABLET_WIDTH);
        } else {
            loginShell.setPrefHeight(Region.USE_COMPUTED_SIZE);
            loginFormPanel.setPrefWidth(cardWidth);
            loginSidePanel.setPrefWidth(0.0);
        }
    }

    private void applyLayoutClass(Region region, String modeClass) {
        if (region == null) {
            return;
        }

        ObservableList<String> styleClasses = region.getStyleClass();
        styleClasses.removeAll(LAYOUT_DESKTOP, LAYOUT_TABLET, LAYOUT_MOBILE);
        if (!styleClasses.contains(modeClass)) {
            styleClasses.add(modeClass);
        }
    }

    private void setDividerVisible(boolean visible) {
        if (loginDivider != null) {
            loginDivider.setManaged(visible);
            loginDivider.setVisible(visible);
        }
    }

    private void setSecondaryPanelVisible(boolean visible) {
        setDividerVisible(visible);
        if (loginSidePanel != null) {
            loginSidePanel.setManaged(visible);
            loginSidePanel.setVisible(visible);
        }
    }

    private void installMicroInteractions() {
        if (loginButton == null) {
            return;
        }

        loginButton.setOnMouseEntered(event -> animateLoginButtonScale(1.015));
        loginButton.setOnMouseExited(event -> animateLoginButtonScale(1.0));
    }

    private void animateLoginButtonScale(double scaleTarget) {
        if (loginButton == null || loading) {
            return;
        }

        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(180), loginButton);
        scaleTransition.setToX(scaleTarget);
        scaleTransition.setToY(scaleTarget);
        scaleTransition.play();
    }

    private void playEnterAnimation() {
        if (loginShell == null || loginRoot == null) {
            return;
        }

        loginRoot.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null || enterAnimationPlayed) {
                return;
            }

            enterAnimationPlayed = true;

            loginShell.setOpacity(0.0);
            loginShell.setTranslateY(10.0);

            FadeTransition fade = new FadeTransition(Duration.millis(220), loginShell);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);

            TranslateTransition slide = new TranslateTransition(Duration.millis(220), loginShell);
            slide.setFromY(10.0);
            slide.setToY(0.0);

            ParallelTransition enter = new ParallelTransition(fade, slide);
            enter.play();
        });
    }

    private void updateStyleClass(ObservableList<String> styleClasses, String styleClass, boolean present) {
        if (present) {
            if (!styleClasses.contains(styleClass)) {
                styleClasses.add(styleClass);
            }
            return;
        }

        styleClasses.remove(styleClass);
    }

    private Optional<User> resolveFaceLoginUser() {
        String email = normalize(emailField.getText());
        if (email == null) {
            markFieldError(emailField);
            setMessage("Enter your email before using Face ID.", true);
            return Optional.empty();
        }

        if (!isLikelyEmail(email)) {
            markFieldError(emailField);
            setMessage("Enter a valid email to use Face ID.", true);
            return Optional.empty();
        }

        Optional<User> maybeUser = userService.getUserByEmail(email);
        if (maybeUser.isEmpty()) {
            setMessage("Face ID is not available for this account. Continue with password.", true);
            return Optional.empty();
        }

        User user = maybeUser.get();
        if (user.getIsActive() != (byte) 1) {
            setMessage("This account is inactive. Continue with password or contact support.", true);
            return Optional.empty();
        }

        return Optional.of(user);
    }

    private boolean isLikelyEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    private File chooseFaceImageFile(String title) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.bmp"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            return chooser.showOpenDialog(loginRoot != null && loginRoot.getScene() != null
                    ? loginRoot.getScene().getWindow()
                    : null);
        } catch (Exception exception) {
            setMessage("Unable to open file browser. Continue with password.", true);
            return null;
        }
    }

    private void handleFaceVerificationResult(User user, FaceVerificationResult verification) {
        if (verification.verified()) {
            UserSession.setCurrentUser(user);
            persistRememberedEmail(normalize(emailField.getText()));
            setMessage("Face verified. Welcome " + safeText(user.getEmail()), false);
            routeByRole(user);
            return;
        }

        String reason = verification.reason() == null || verification.reason().isBlank()
                ? "Face verification failed"
                : verification.reason();

        if (verification.rateLimited()) {
            setMessage(reason + ". Continue with password.", true);
            return;
        }

        setMessage(reason + ". You can still continue with password.", true);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Unable to sign in" : message;
    }

    private void loadRememberedEmail() {
        String rememberedEmail = normalize(preferences.get(PREF_REMEMBERED_EMAIL, null));
        if (rememberedEmail == null) {
            return;
        }

        emailField.setText(rememberedEmail);
        if (rememberMeCheckBox != null) {
            rememberMeCheckBox.setSelected(true);
        }
    }

    private void persistRememberedEmail(String email) {
        if (rememberMeCheckBox != null && rememberMeCheckBox.isSelected()) {
            preferences.put(PREF_REMEMBERED_EMAIL, email);
            return;
        }

        preferences.remove(PREF_REMEMBERED_EMAIL);
    }
}
