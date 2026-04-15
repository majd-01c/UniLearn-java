package validation;

import entities.Role;
import entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import repository.UserRepository;
import service.PasswordManagementService;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public final class UserValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserValidator.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\-\\s]+$");

    private static final UserRepository USER_REPOSITORY = new UserRepository();
    private static final PasswordManagementService PASSWORD_SERVICE = new PasswordManagementService();

    private UserValidator() {
    }

    public static boolean validateEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return false;
        }

        try {
            Optional<User> existingUser = USER_REPOSITORY.findByEmail(normalizedEmail);
            return existingUser.isEmpty();
        } catch (Exception exception) {
            LOGGER.error("Email uniqueness validation failed", exception);
            return false;
        }
    }

    public static boolean validateUserCreation(User user, String password) {
        if (user == null) {
            return false;
        }

        boolean hasValidEmail = validateEmail(user.getEmail());
        boolean hasStrongPassword = PASSWORD_SERVICE.validatePasswordStrength(password);
        boolean hasValidName = hasFirstAndLastName(user.getName());
        boolean hasValidRole = isValidRole(user.getRole());
        boolean hasValidPhone = validatePhoneNumber(user.getPhone());

        return hasValidEmail
                && hasStrongPassword
                && hasValidName
                && hasValidRole
                && hasValidPhone;
    }

    public static boolean validatePasswordChange(String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            return false;
        }

        return newPassword != null
                && newPassword.equals(confirmPassword)
                && PASSWORD_SERVICE.validatePasswordStrength(newPassword);
    }

    public static boolean validatePasswordChange(String currentPassword,
                                                 String newPassword,
                                                 String confirmPassword,
                                                 String storedHashedPassword) {
        if (!validatePasswordChange(currentPassword, newPassword, confirmPassword)) {
            return false;
        }

        return PASSWORD_SERVICE.validatePassword(currentPassword, storedHashedPassword);
    }

    public static boolean validatePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return true;
        }

        String normalizedPhone = phone.trim();
        return PHONE_PATTERN.matcher(normalizedPhone).matches();
    }

    private static boolean hasFirstAndLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return false;
        }

        String[] parts = fullName.trim().split("\\s+");
        return parts.length >= 2
                && !parts[0].isBlank()
                && !parts[1].isBlank();
    }

    private static boolean isValidRole(String roleValue) {
        if (roleValue == null || roleValue.isBlank()) {
            return false;
        }

        String normalizedRole = roleValue.trim();
        return Arrays.stream(Role.values())
                .anyMatch(role -> role.getDbValue().equalsIgnoreCase(normalizedRole)
                        || role.name().equalsIgnoreCase(normalizedRole));
    }
}
