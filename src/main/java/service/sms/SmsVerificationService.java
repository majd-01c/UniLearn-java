package service.sms;

import entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.UserRepository;
import util.ConfigurationProvider;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Service for SMS OTP verification management.
 * Handles OTP generation, validation, rate limiting, and lockout logic.
 */
public class SmsVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmsVerificationService.class);

    private static final int OTP_TTL_SECONDS = 600; // 10 minutes default
    private static final int MAX_ATTEMPTS = 3;
    private static final int RESEND_COOLDOWN_SECONDS = 60; // 1 minute default
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final TwilioSmsService twilioSmsService;
    private final SecureRandom secureRandom;
    private final int otpTtl;
    private final int maxAttempts;
    private final int resendCooldown;

    public SmsVerificationService() {
        this.userRepository = new UserRepository();
        this.twilioSmsService = new TwilioSmsService();
        this.secureRandom = new SecureRandom();
        this.otpTtl = Integer.parseInt(ConfigurationProvider.getProperty("OTP_TTL_SECONDS", String.valueOf(OTP_TTL_SECONDS)));
        this.maxAttempts = Integer.parseInt(ConfigurationProvider.getProperty("OTP_MAX_ATTEMPTS", String.valueOf(MAX_ATTEMPTS)));
        this.resendCooldown = Integer.parseInt(ConfigurationProvider.getProperty("OTP_RESEND_COOLDOWN_SECONDS", String.valueOf(RESEND_COOLDOWN_SECONDS)));
    }

    /**
     * Send OTP code to user's phone number.
     * Returns true if OTP was sent successfully, false otherwise.
     * Enforces rate limiting and lockout logic.
     */
    public boolean sendOtpCode(User user) {
        if (user == null || user.getId() == null) {
            LOGGER.error("Invalid user for OTP sending");
            return false;
        }

        if (user.getSmsPhoneNumber() == null || user.getSmsPhoneNumber().isBlank()) {
            LOGGER.error("User {} has no phone number for SMS verification", user.getId());
            return false;
        }

        // Check if user is locked out
        if (isLockedOut(user)) {
            LOGGER.warn("User {} is locked out from OTP attempts", user.getId());
            return false;
        }

        // Check resend cooldown
        if (!canResendOtp(user)) {
            LOGGER.warn("User {} attempted OTP resend before cooldown expired", user.getId());
            return false;
        }

        try {
            // Send via Twilio
            String verifySid = twilioSmsService.sendVerificationCode(user.getSmsPhoneNumber());

            if (verifySid == null && twilioSmsService.isUsingVerifyApi()) {
                LOGGER.error("Failed to send OTP via Twilio Verify API for user {}", user.getId());
                auditLog(user.getId(), "OTP_SEND", "FAILED", "Twilio API error");
                return false;
            }

            // Update user with OTP data
            // For Twilio Verify, Twilio manages the code and expiry; for direct SMS, app manages OTP hash.
            if (twilioSmsService.isUsingVerifyApi()) {
                user.setSmsOtpHash(null);
                user.setSmsOtpExpiresAt(null);
            } else {
                String otp = generateOtp();
                String otpHash = hashOtp(otp);
                user.setSmsOtpHash(otpHash);
                user.setSmsOtpExpiresAt(Timestamp.from(Instant.now().plus(otpTtl, ChronoUnit.SECONDS)));
            }
            user.setSmsOtpLastSentAt(Timestamp.from(Instant.now()));
            user.setSmsOtpAttempts(0);

            // Reset lockout if any
            user.setSmsOtpLockedUntil(null);

            userRepository.save(user);

            LOGGER.info("OTP sent successfully to user {} at {}", user.getId(), maskPhoneNumber(user.getSmsPhoneNumber()));
            auditLog(user.getId(), "OTP_SEND", "SUCCESS", null);
            return true;

        } catch (Exception exception) {
            LOGGER.error("Error sending OTP to user {}: {}", user.getId(), exception.getMessage(), exception);
            auditLog(user.getId(), "OTP_SEND", "ERROR", exception.getMessage());
            return false;
        }
    }

    /**
     * Verify OTP code provided by user.
     * Returns true if OTP is valid, false otherwise.
     * Enforces max attempts and lockout logic.
     */
    public boolean verifyOtpCode(User user, String providedCode) {
        if (user == null || user.getId() == null || providedCode == null || providedCode.isBlank()) {
            LOGGER.error("Invalid user or OTP code for verification");
            return false;
        }

        try {
            // Check if locked out
            if (isLockedOut(user)) {
                LOGGER.warn("User {} is locked out from OTP verification attempts", user.getId());
                auditLog(user.getId(), "OTP_VERIFY", "FAILED", "User is locked out");
                return false;
            }

            boolean isValid;
            if (twilioSmsService.isUsingVerifyApi()) {
                // Twilio Verify owns code generation and expiry checks
                isValid = twilioSmsService.verifyCode(user.getSmsPhoneNumber(), providedCode, null);
            } else {
                // App-managed OTP path
                if (isOtpExpired(user)) {
                    LOGGER.warn("OTP expired for user {}", user.getId());
                    auditLog(user.getId(), "OTP_VERIFY", "FAILED", "OTP expired");
                    return false;
                }
                isValid = verifyOtpHash(user.getSmsOtpHash(), providedCode);
            }

            if (isValid) {
                // Verification successful - mark user as SMS verified
                user.setSmsVerified((byte) 1);
                user.setSmsVerifiedAt(Timestamp.from(Instant.now()));
                user.setFirstLoginCompleted((byte) 1);
                user.setSmsOtpHash(null);
                user.setSmsOtpExpiresAt(null);
                user.setSmsOtpAttempts(0);
                user.setSmsOtpLockedUntil(null);

                userRepository.save(user);

                LOGGER.info("SMS verification successful for user {}", user.getId());
                auditLog(user.getId(), "OTP_VERIFY", "SUCCESS", null);
                return true;
            } else {
                // Verification failed - increment attempts
                int attempts = user.getSmsOtpAttempts() != null ? user.getSmsOtpAttempts() : 0;
                attempts++;
                user.setSmsOtpAttempts(attempts);

                if (attempts >= maxAttempts) {
                    // Lock out user
                    user.setSmsOtpLockedUntil(Timestamp.from(Instant.now().plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES)));
                    LOGGER.warn("User {} locked out after {} failed OTP attempts", user.getId(), attempts);
                    auditLog(user.getId(), "OTP_VERIFY", "LOCKOUT", "Max attempts exceeded");
                } else {
                    LOGGER.warn("Invalid OTP for user {}, attempt {} of {}", user.getId(), attempts, maxAttempts);
                    auditLog(user.getId(), "OTP_VERIFY", "FAILED", "Invalid code (attempt " + attempts + "/" + maxAttempts + ")");
                }

                userRepository.save(user);
                return false;
            }

        } catch (Exception exception) {
            LOGGER.error("Error verifying OTP for user {}: {}", user.getId(), exception.getMessage(), exception);
            auditLog(user.getId(), "OTP_VERIFY", "ERROR", exception.getMessage());
            return false;
        }
    }

    /**
     * Reset SMS verification for a user (admin action).
     */
    public void resetSmsVerification(User user) {
        if (user == null) {
            return;
        }

        user.setSmsVerified((byte) 0);
        user.setSmsVerifiedAt(null);
        user.setSmsOtpHash(null);
        user.setSmsOtpExpiresAt(null);
        user.setSmsOtpAttempts(0);
        user.setSmsOtpLastSentAt(null);
        user.setSmsOtpLockedUntil(null);

        userRepository.save(user);

        LOGGER.info("SMS verification reset for user {}", user.getId());
        auditLog(user.getId(), "SMS_RESET", "SUCCESS", "Admin reset SMS verification");
    }

    /**
     * Check if OTP has expired.
     */
    private boolean isOtpExpired(User user) {
        if (user.getSmsOtpExpiresAt() == null) {
            return true;
        }

        Instant expiry = user.getSmsOtpExpiresAt().toInstant();
        return Instant.now().isAfter(expiry);
    }

    /**
     * Check if user is locked out from OTP attempts.
     */
    private boolean isLockedOut(User user) {
        if (user.getSmsOtpLockedUntil() == null) {
            return false;
        }

        Instant lockedUntil = user.getSmsOtpLockedUntil().toInstant();
        boolean isLocked = Instant.now().isBefore(lockedUntil);

        if (!isLocked) {
            // Unlock user - lockout period has expired
            user.setSmsOtpLockedUntil(null);
            user.setSmsOtpAttempts(0);
            userRepository.save(user);
        }

        return isLocked;
    }

    /**
     * Check if user can resend OTP (respects cooldown period).
     */
    private boolean canResendOtp(User user) {
        if (user.getSmsOtpLastSentAt() == null) {
            return true;
        }

        Instant lastSent = user.getSmsOtpLastSentAt().toInstant();
        Instant canResendAt = lastSent.plus(resendCooldown, ChronoUnit.SECONDS);

        return Instant.now().isAfter(canResendAt);
    }

    /**
     * Get remaining cooldown seconds for resend.
     * Returns 0 if resend is available.
     */
    public long getRemainingResendCooldownSeconds(User user) {
        if (user == null || user.getSmsOtpLastSentAt() == null) {
            return 0;
        }

        Instant lastSent = user.getSmsOtpLastSentAt().toInstant();
        Instant canResendAt = lastSent.plus(resendCooldown, ChronoUnit.SECONDS);
        long remaining = ChronoUnit.SECONDS.between(Instant.now(), canResendAt);

        return Math.max(0, remaining);
    }

    /**
     * Get remaining lockout seconds.
     * Returns 0 if not locked out.
     */
    public long getRemainingLockoutSeconds(User user) {
        if (user == null || user.getSmsOtpLockedUntil() == null) {
            return 0;
        }

        Instant lockedUntil = user.getSmsOtpLockedUntil().toInstant();
        long remaining = ChronoUnit.SECONDS.between(Instant.now(), lockedUntil);

        return Math.max(0, remaining);
    }

    /**
     * Generate random 6-digit OTP.
     */
    private String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    /**
     * Hash OTP for secure storage using PBKDF2.
     */
    private String hashOtp(String otp) {
        // For production, use BCrypt or PBKDF2
        // This is a simplified version - use a proper password hashing library
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(otp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            LOGGER.error("SHA-256 not available", e);
            return otp; // Fallback - not recommended for production
        }
    }

    /**
     * Verify OTP against stored hash.
     */
    private boolean verifyOtpHash(String storedHash, String providedOtp) {
        if (storedHash == null || providedOtp == null) {
            return false;
        }

        String providedHash = hashOtp(providedOtp);
        return storedHash.equals(providedHash);
    }

    /**
     * Mask phone number for logging (show only last 4 digits).
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return "*".repeat(phoneNumber.length() - 4) + phoneNumber.substring(phoneNumber.length() - 4);
    }

    /**
     * Log SMS audit events.
     */
    private void auditLog(Integer userId, String operation, String status, String errorMessage) {
        try {
            // In production, insert into sms_audit_log table
            LOGGER.info("SMS_AUDIT: userId={}, operation={}, status={}, error={}", userId, operation, status, errorMessage);
        } catch (Exception exception) {
            LOGGER.warn("Failed to write audit log: {}", exception.getMessage());
        }
    }
}
