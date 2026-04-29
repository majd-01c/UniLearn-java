package controller;

import entities.User;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import security.UserSession;
import service.sms.SmsVerificationService;
import util.AppNavigator;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

/**
 * Controller for SMS verification screen.
 * Manages OTP input, verification, and resend functionality.
 */
public class SmsVerificationController implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmsVerificationController.class);
    private static final Pattern OTP_PATTERN = Pattern.compile("^\\d{6}$");
    private static final int RESEND_COOLDOWN_INITIAL_SECONDS = 60;

    @FXML
    private Label phoneNumberLabel;

    @FXML
    private TextField otpField;

    @FXML
    private Label otpErrorLabel;

    @FXML
    private Button verifyButton;

    @FXML
    private Label attemptsLabel;

    @FXML
    private Button resendButton;

    @FXML
    private Label resendCooldownLabel;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Label loadingLabel;

    @FXML
    private VBox loadingContainer;

    @FXML
    private VBox successContainer;

    @FXML
    private VBox errorContainer;

    @FXML
    private Label errorLabel;

    @FXML
    private Button cancelButton;

    private final SmsVerificationService smsVerificationService = new SmsVerificationService();
    private User currentUser;
    private Timeline resendCooldownTimer;
    private boolean isVerifying;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentUser = UserSession.getCurrentUser();

        if (currentUser == null) {
            LOGGER.error("No authenticated user found for SMS verification");
            handleCancel();
            return;
        }

        // Display masked phone number
        displayPhoneNumber();

        // Initialize UI state
        clearMessages();
        otpField.setDisable(false);
        resendButton.setDisable(false);
        verifyButton.setDisable(false);

        // Listen for OTP input changes
        otpField.textProperty().addListener((obs, oldVal, newVal) -> {
            // Only allow digits
            if (!newVal.matches("\\d*")) {
                otpField.setText(newVal.replaceAll("\\D", ""));
            }
            // Max 6 digits
            if (otpField.getText().length() > 6) {
                otpField.setText(otpField.getText().substring(0, 6));
            }
            clearErrorMessage();
        });

        // Auto-submit when 6 digits are entered
        otpField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() == 6 && !isVerifying) {
                handleVerifyCode();
            }
        });

        updateAttemptsDisplay();
        updateResendCooldown();
    }

    @FXML
    private void handleVerifyCode() {
        if (isVerifying) {
            return;
        }

        String otp = otpField.getText().trim();

        if (!validateOtpInput(otp)) {
            return;
        }

        setVerifying(true);
        showLoading("Verifying code...");

        // Run verification in background thread
        new Thread(() -> {
            try {
                boolean verified = smsVerificationService.verifyOtpCode(currentUser, otp);

                Platform.runLater(() -> {
                    if (verified) {
                        showSuccess();
                        // Navigate to dashboard after short delay
                        Timeline timeline = new Timeline(
                                new KeyFrame(Duration.seconds(1.5), event -> navigateToDashboard())
                        );
                        timeline.play();
                    } else {
                        setVerifying(false);
                        hideLoading();
                        updateAttemptsDisplay();

                        long remainingLockout = smsVerificationService.getRemainingLockoutSeconds(currentUser);
                        if (remainingLockout > 0) {
                            showError(String.format(
                                    "Too many failed attempts. Please try again in %d minutes.",
                                    (remainingLockout + 59) / 60
                            ));
                            disableInputs();
                        } else {
                            showError("Invalid verification code. Please try again.");
                            otpField.clear();
                        }
                    }
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    setVerifying(false);
                    hideLoading();
                    LOGGER.error("Error during OTP verification: {}", exception.getMessage(), exception);
                    showError("An error occurred during verification. Please try again.");
                });
            }
        }).start();
    }

    @FXML
    private void handleResendCode() {
        if (resendButton.isDisabled()) {
            return;
        }

        resendButton.setDisable(true);
        verifyButton.setDisable(true);
        otpField.setDisable(true);

        setVerifying(true);
        showLoading("Sending new code...");

        new Thread(() -> {
            try {
                // Reload user to get current state
                currentUser = UserSession.getCurrentUser();
                boolean sent = smsVerificationService.sendOtpCode(currentUser);

                Platform.runLater(() -> {
                    setVerifying(false);
                    hideLoading();

                    if (sent) {
                        otpField.clear();
                        otpField.setDisable(false);
                        verifyButton.setDisable(false);
                        clearMessages();
                        showInfo("New verification code sent to " + maskPhoneNumber(currentUser.getSmsPhoneNumber()));
                        startResendCooldown();
                    } else {
                        resendButton.setDisable(false);
                        showError("Failed to send verification code. Please try again.");
                    }
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    setVerifying(false);
                    hideLoading();
                    resendButton.setDisable(false);
                    verifyButton.setDisable(false);
                    otpField.setDisable(false);
                    LOGGER.error("Error resending OTP: {}", exception.getMessage(), exception);
                    showError("Failed to resend code. Please try again.");
                });
            }
        }).start();
    }

    @FXML
    private void handleCancel() {
        // Log out user
        UserSession.clearCurrentUser();
        AppNavigator.showLogin();
    }

    private boolean validateOtpInput(String otp) {
        if (otp == null || otp.isBlank()) {
            showErrorMessage("Please enter the verification code.");
            return false;
        }

        if (!OTP_PATTERN.matcher(otp).matches()) {
            showErrorMessage("Verification code must be 6 digits.");
            return false;
        }

        return true;
    }

    private void displayPhoneNumber() {
        if (currentUser.getSmsPhoneNumber() != null) {
            String masked = maskPhoneNumber(currentUser.getSmsPhoneNumber());
            phoneNumberLabel.setText("Phone: " + masked);
        }
    }

    private void updateAttemptsDisplay() {
        if (currentUser.getSmsOtpAttempts() != null && currentUser.getSmsOtpAttempts() > 0) {
            attemptsLabel.setText("Attempt " + currentUser.getSmsOtpAttempts() + " of 3");
            attemptsLabel.setStyle("-fx-text-fill: #f59e0b;");
        } else {
            attemptsLabel.setText("");
        }
    }

    private void updateResendCooldown() {
        long remainingSeconds = smsVerificationService.getRemainingResendCooldownSeconds(currentUser);
        
        if (remainingSeconds > 0) {
            startResendCooldown();
        } else {
            resendButton.setDisable(false);
            resendCooldownLabel.setText("");
        }
    }

    private void startResendCooldown() {
        resendButton.setDisable(true);
        
        if (resendCooldownTimer != null) {
            resendCooldownTimer.stop();
        }

        long[] remainingSeconds = {RESEND_COOLDOWN_INITIAL_SECONDS};

        resendCooldownTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> {
                    remainingSeconds[0]--;
                    
                    if (remainingSeconds[0] > 0) {
                        resendCooldownLabel.setText("Resend available in " + remainingSeconds[0] + "s");
                    } else {
                        resendButton.setDisable(false);
                        resendCooldownLabel.setText("");
                        resendCooldownTimer.stop();
                    }
                })
        );
        resendCooldownTimer.setCycleCount((int) remainingSeconds[0]);
        resendCooldownTimer.play();
    }

    private void showLoading(String message) {
        loadingLabel.setText(message);
        loadingContainer.setVisible(true);
        loadingContainer.setManaged(true);
    }

    private void hideLoading() {
        loadingContainer.setVisible(false);
        loadingContainer.setManaged(false);
    }

    private void showSuccess() {
        successContainer.setVisible(true);
        successContainer.setManaged(true);
        otpField.setDisable(true);
        verifyButton.setDisable(true);
        resendButton.setDisable(true);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorContainer.setVisible(true);
        errorContainer.setManaged(true);
    }

    private void showInfo(String message) {
        attemptsLabel.setText(message);
        attemptsLabel.setStyle("-fx-text-fill: #16a34a;");
    }

    private void showErrorMessage(String message) {
        otpErrorLabel.setText(message);
        otpErrorLabel.setStyle("-fx-text-fill: #dc2626;");
    }

    private void clearErrorMessage() {
        otpErrorLabel.setText("");
    }

    private void clearMessages() {
        otpErrorLabel.setText("");
        errorContainer.setVisible(false);
        errorContainer.setManaged(false);
        successContainer.setVisible(false);
        successContainer.setManaged(false);
        attemptsLabel.setText("");
    }

    private void disableInputs() {
        otpField.setDisable(true);
        verifyButton.setDisable(true);
        resendButton.setDisable(true);
    }

    private void setVerifying(boolean verifying) {
        isVerifying = verifying;
        if (verifying) {
            otpField.setDisable(true);
            verifyButton.setDisable(true);
            resendButton.setDisable(true);
        } else {
            otpField.setDisable(false);
            verifyButton.setDisable(false);
            if (!resendButton.isDisabled()) {
                resendButton.setDisable(false);
            }
        }
    }

    private void navigateToDashboard() {
        AppNavigator.loginSuccess(currentUser);
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return "*".repeat(phoneNumber.length() - 4) + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
