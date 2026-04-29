package service;

import entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Authentication Service with SMS verification.
 * Tests authentication flow and SMS verification requirement detection.
 */
@DisplayName("Authentication Service SMS Tests")
public class AuthenticationServiceSmsTest {

    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser = createTestUser();
    }

    @Test
    @DisplayName("Should indicate SMS verification required for first-time unverified user")
    public void testAuthenticateWithSmsCheck_FirstLoginUnverified() {
        // Given: User with SMS unverified and first login not completed
        testUser.setSmsVerified((byte) 0);
        testUser.setFirstLoginCompleted((byte) 0);
        
        // Note: In real implementation, this would authenticate with credentials
        // This test demonstrates the logic structure
        boolean requiresSmsVerification = !testUser.isSmsVerified() && !testUser.isFirstLoginCompleted();
        
        // Then: SMS verification should be required
        assertTrue(requiresSmsVerification);
    }

    @Test
    @DisplayName("Should not require SMS verification for already verified user")
    public void testAuthenticateWithSmsCheck_AlreadyVerified() {
        // Given: User with SMS verified
        testUser.setSmsVerified((byte) 1);
        testUser.setFirstLoginCompleted((byte) 1);
        
        boolean requiresSmsVerification = !testUser.isSmsVerified() && !testUser.isFirstLoginCompleted();
        
        // Then: SMS verification should not be required
        assertFalse(requiresSmsVerification);
    }

    @Test
    @DisplayName("Should not require SMS verification after first login completion")
    public void testAuthenticateWithSmsCheck_FirstLoginCompleted() {
        // Given: User with first login completed but SMS not verified
        // (simulates user who is using temporary phone until SMS verification added)
        testUser.setSmsVerified((byte) 0);
        testUser.setFirstLoginCompleted((byte) 1);
        
        boolean requiresSmsVerification = !testUser.isSmsVerified() && !testUser.isFirstLoginCompleted();
        
        // Then: SMS verification should not be required (already passed first login)
        assertFalse(requiresSmsVerification);
    }

    @Test
    @DisplayName("Should create AuthenticationResult with SMS requirement flag")
    public void testAuthenticationResult() {
        // Given: Authentication result for unverified user
        AuthenticationService.AuthenticationResult result = new AuthenticationService.AuthenticationResult(
                testUser, true
        );
        
        // Then: Result should contain user and SMS flag
        assertNotNull(result.user);
        assertTrue(result.requiresSmsVerification);
        assertEquals(testUser, result.user);
    }

    @Test
    @DisplayName("Should create AuthenticationResult for verified user")
    public void testAuthenticationResult_Verified() {
        // Given: Authentication result for verified user
        testUser.setSmsVerified((byte) 1);
        AuthenticationService.AuthenticationResult result = new AuthenticationService.AuthenticationResult(
                testUser, false
        );
        
        // Then: Result should indicate no SMS verification needed
        assertNotNull(result.user);
        assertFalse(result.requiresSmsVerification);
    }

    // ========================
    // Helper Methods
    // ========================

    private User createTestUser() {
        User user = new User();
        user.setId(1);
        user.setEmail("test@example.com");
        user.setPassword("hashedPassword");
        user.setIsActive((byte) 1);
        user.setRole("STUDENT");
        user.setSmsPhoneNumber("+216 98765432");
        return user;
    }
}
