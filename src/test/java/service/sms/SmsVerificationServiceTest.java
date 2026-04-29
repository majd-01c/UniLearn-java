package service.sms;

import entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SmsVerificationService.
 * Tests OTP generation, validation, rate limiting, and lockout logic.
 */
@DisplayName("SMS Verification Service Tests")
public class SmsVerificationServiceTest {

    private SmsVerificationService smsVerificationService;
    private User testUser;

    @BeforeEach
    public void setUp() {
        smsVerificationService = new SmsVerificationService();
        
        // Create test user
        testUser = new User();
        testUser.setId(1);
        testUser.setEmail("test@example.com");
        testUser.setSmsPhoneNumber("+216 98765432");
        testUser.setSmsVerified((byte) 0);
        testUser.setSmsOtpAttempts(0);
    }

    @Test
    @DisplayName("Should send OTP code successfully")
    public void testSendOtpCode_Success() {
        // Given: User with valid phone number
        assertTrue(smsVerificationService.sendOtpCode(testUser));
        
        // Then: User OTP fields should be populated
        assertNotNull(testUser.getSmsOtpHash());
        assertNotNull(testUser.getSmsOtpExpiresAt());
        assertNotNull(testUser.getSmsOtpLastSentAt());
        assertEquals(0, testUser.getSmsOtpAttempts());
    }

    @Test
    @DisplayName("Should reject OTP send for user without phone number")
    public void testSendOtpCode_NoPhoneNumber() {
        // Given: User without phone number
        testUser.setSmsPhoneNumber(null);
        
        // When: Attempting to send OTP
        boolean result = smsVerificationService.sendOtpCode(testUser);
        
        // Then: Should fail
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject OTP send if user is locked out")
    public void testSendOtpCode_UserLockedOut() {
        // Given: User locked out
        testUser.setSmsOtpLockedUntil(Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
        
        // When: Attempting to send OTP
        boolean result = smsVerificationService.sendOtpCode(testUser);
        
        // Then: Should fail
        assertFalse(result);
    }

    @Test
    @DisplayName("Should enforce resend cooldown")
    public void testSendOtpCode_ResendCooldown() {
        // Given: OTP was sent recently
        testUser.setSmsOtpLastSentAt(Timestamp.from(Instant.now().minus(10, ChronoUnit.SECONDS)));
        
        // When: Attempting to resend immediately
        boolean result = smsVerificationService.sendOtpCode(testUser);
        
        // Then: Should fail due to cooldown
        assertFalse(result);
    }

    @Test
    @DisplayName("Should verify correct OTP code")
    public void testVerifyOtpCode_Correct() {
        // Given: User with valid OTP
        String testOtp = "123456";
        setupUserWithValidOtp(testUser, testOtp);
        
        // When: Verifying correct code
        boolean result = smsVerificationService.verifyOtpCode(testUser, testOtp);
        
        // Then: Verification should succeed
        assertTrue(result);
        assertEquals(1, testUser.getSmsVerified());
        assertNotNull(testUser.getSmsVerifiedAt());
        assertNull(testUser.getSmsOtpHash());
    }

    @Test
    @DisplayName("Should reject incorrect OTP code")
    public void testVerifyOtpCode_Incorrect() {
        // Given: User with valid OTP
        String testOtp = "123456";
        setupUserWithValidOtp(testUser, testOtp);
        int initialAttempts = testUser.getSmsOtpAttempts() != null ? testUser.getSmsOtpAttempts() : 0;
        
        // When: Verifying wrong code
        boolean result = smsVerificationService.verifyOtpCode(testUser, "999999");
        
        // Then: Verification should fail and attempts should increment
        assertFalse(result);
        assertEquals(initialAttempts + 1, testUser.getSmsOtpAttempts());
        assertEquals(0, testUser.getSmsVerified());
    }

    @Test
    @DisplayName("Should lock out user after max attempts")
    public void testVerifyOtpCode_MaxAttemptsExceeded() {
        // Given: User with failed attempts at max
        setupUserWithValidOtp(testUser, "123456");
        testUser.setSmsOtpAttempts(3); // Already at max
        
        // When: Attempting one more verification
        boolean result = smsVerificationService.verifyOtpCode(testUser, "999999");
        
        // Then: Should lock out user
        assertFalse(result);
        assertNotNull(testUser.getSmsOtpLockedUntil());
        assertTrue(Instant.now().isBefore(testUser.getSmsOtpLockedUntil().toInstant()));
    }

    @Test
    @DisplayName("Should reject OTP if expired")
    public void testVerifyOtpCode_Expired() {
        // Given: User with expired OTP
        setupUserWithValidOtp(testUser, "123456");
        testUser.setSmsOtpExpiresAt(Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        
        // When: Attempting to verify expired OTP
        boolean result = smsVerificationService.verifyOtpCode(testUser, "123456");
        
        // Then: Verification should fail
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject OTP if user is locked out")
    public void testVerifyOtpCode_UserLockedOut() {
        // Given: User locked out
        setupUserWithValidOtp(testUser, "123456");
        testUser.setSmsOtpLockedUntil(Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
        
        // When: Attempting to verify
        boolean result = smsVerificationService.verifyOtpCode(testUser, "123456");
        
        // Then: Verification should fail
        assertFalse(result);
    }

    @Test
    @DisplayName("Should calculate resend cooldown correctly")
    public void testGetRemainingResendCooldownSeconds() {
        // Given: OTP sent recently
        testUser.setSmsOtpLastSentAt(Timestamp.from(Instant.now().minus(30, ChronoUnit.SECONDS)));
        
        // When: Getting remaining cooldown
        long remaining = smsVerificationService.getRemainingResendCooldownSeconds(testUser);
        
        // Then: Should be approximately 30 seconds remaining
        assertTrue(remaining > 0);
        assertTrue(remaining <= 30);
    }

    @Test
    @DisplayName("Should calculate lockout duration correctly")
    public void testGetRemainingLockoutSeconds() {
        // Given: User locked out
        testUser.setSmsOtpLockedUntil(Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        
        // When: Getting remaining lockout time
        long remaining = smsVerificationService.getRemainingLockoutSeconds(testUser);
        
        // Then: Should be approximately 10 minutes
        assertTrue(remaining > 0);
        assertTrue(remaining <= 600);
    }

    @Test
    @DisplayName("Should reset SMS verification")
    public void testResetSmsVerification() {
        // Given: Verified user with OTP data
        testUser.setSmsVerified((byte) 1);
        testUser.setSmsVerifiedAt(Timestamp.from(Instant.now()));
        testUser.setSmsOtpHash("somehash");
        testUser.setSmsOtpAttempts(2);
        
        // When: Resetting verification
        smsVerificationService.resetSmsVerification(testUser);
        
        // Then: All SMS verification data should be cleared
        assertEquals(0, testUser.getSmsVerified());
        assertNull(testUser.getSmsVerifiedAt());
        assertNull(testUser.getSmsOtpHash());
        assertNull(testUser.getSmsOtpAttempts());
        assertNull(testUser.getSmsOtpLockedUntil());
    }

    @Test
    @DisplayName("Should handle null user gracefully")
    public void testSendOtpCode_NullUser() {
        // When: Sending OTP with null user
        boolean result = smsVerificationService.sendOtpCode(null);
        
        // Then: Should return false safely
        assertFalse(result);
    }

    @Test
    @DisplayName("Should unlock user after lockout expires")
    public void testAutoUnlock_AfterLockoutExpires() {
        // Given: User with expired lockout
        testUser.setSmsOtpLockedUntil(Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        testUser.setSmsOtpAttempts(3);
        
        // When: Getting lockout seconds
        long remaining = smsVerificationService.getRemainingLockoutSeconds(testUser);
        
        // Then: Should indicate no remaining lockout
        assertEquals(0, remaining);
    }

    // ========================
    // Helper Methods
    // ========================

    private void setupUserWithValidOtp(User user, String otp) {
        String otpHash = hashOtp(otp);
        user.setSmsOtpHash(otpHash);
        user.setSmsOtpExpiresAt(Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        user.setSmsOtpLastSentAt(Timestamp.from(Instant.now()));
        user.setSmsOtpAttempts(0);
        user.setSmsOtpLockedUntil(null);
    }

    private String hashOtp(String otp) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(otp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            return otp; // Fallback
        }
    }
}
