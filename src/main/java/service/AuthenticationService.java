package service;

import entities.ResetToken;
import entities.User;
import repository.ResetTokenRepository;
import repository.UserRepository;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthenticationService {

    private static final int RESET_TOKEN_LENGTH_HEX = 32;
    private static final int RESET_TOKEN_TTL_HOURS = 2;

    private final UserRepository userRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final PasswordManagementService passwordManagementService;
    private final SecureRandom secureRandom;

    private final Map<String, Instant> loginAttemptTimestamps = new ConcurrentHashMap<>();

    public AuthenticationService() {
        this.userRepository = new UserRepository();
        this.resetTokenRepository = new ResetTokenRepository();
        this.passwordManagementService = new PasswordManagementService();
        this.secureRandom = new SecureRandom();
    }

    public User authenticate(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        loginAttemptTimestamps.put(normalizedEmail, Instant.now());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (user.getIsActive() != (byte) 1) {
            throw new IllegalStateException("User account is inactive");
        }

        if (!passwordManagementService.validatePassword(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        user.setUpdatedAt(Timestamp.from(Instant.now()));
        userRepository.save(user);
        return user;
    }

    public ResetToken validateResetToken(String token, User user) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid user is required for token validation");
        }

        ResetToken resetToken = resetTokenRepository.findByToken(token.trim())
                .orElseThrow(() -> new IllegalArgumentException("Reset token not found"));

        if (resetToken.getUsed() == (byte) 1) {
            throw new IllegalStateException("Reset token has already been used");
        }

        if (resetToken.getUser() == null || resetToken.getUser().getId() == null
                || !resetToken.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Reset token does not belong to this user");
        }

        Instant now = Instant.now();
        Instant expiry = resetToken.getExpiryDate().toInstant();
        if (expiry.isBefore(now) || expiry.equals(now)) {
            throw new IllegalStateException("Reset token has expired");
        }

        if (resetToken.getCreatedAt() != null) {
            Instant maxAllowedExpiry = resetToken.getCreatedAt().toInstant().plus(RESET_TOKEN_TTL_HOURS, ChronoUnit.HOURS);
            if (now.isAfter(maxAllowedExpiry)) {
                throw new IllegalStateException("Reset token has expired");
            }
        }

        return resetToken;
    }

    public ResetToken generatePasswordResetToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Valid user is required");
        }

        List<ResetToken> activeTokens = resetTokenRepository.findActiveTokensForUser(user.getId().longValue());
        for (ResetToken activeToken : activeTokens) {
            activeToken.setUsed((byte) 1);
            resetTokenRepository.save(activeToken);
        }

        Instant now = Instant.now();
        ResetToken newToken = new ResetToken();
        newToken.setUser(user);
        newToken.setToken(generateRandomToken());
        newToken.setUsed((byte) 0);
        newToken.setCreatedAt(Timestamp.from(now));
        newToken.setExpiryDate(Timestamp.from(now.plus(RESET_TOKEN_TTL_HOURS, ChronoUnit.HOURS)));

        return resetTokenRepository.save(newToken);
    }

    public void resetPasswordWithToken(String token, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required");
        }
        if (!passwordManagementService.validatePasswordStrength(newPassword)) {
            throw new IllegalArgumentException("New password does not meet strength requirements");
        }

        ResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Reset token not found"));

        User user = resetToken.getUser();
        validateResetToken(token, user);

        user.setPassword(passwordManagementService.hashPassword(newPassword));
        user.setMustChangePassword((byte) 0);
        user.setUpdatedAt(Timestamp.from(Instant.now()));
        userRepository.save(user);

        resetToken.setUsed((byte) 1);
        resetTokenRepository.save(resetToken);
    }

    public Optional<Instant> getLastLoginAttempt(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(loginAttemptTimestamps.get(email.trim().toLowerCase()));
    }

    private String generateRandomToken() {
        int bytesLength = RESET_TOKEN_LENGTH_HEX / 2;
        byte[] randomBytes = new byte[bytesLength];
        secureRandom.nextBytes(randomBytes);

        StringBuilder token = new StringBuilder(RESET_TOKEN_LENGTH_HEX);
        for (byte value : randomBytes) {
            token.append(String.format("%02x", value));
        }
        return token.toString();
    }
}
