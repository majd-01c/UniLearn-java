package service;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PasswordManagementService {

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final int BCRYPT_ROUNDS = 12;

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[!@#$%^&*].*");

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateTemporaryPassword() {
        List<Character> chars = new ArrayList<>();

        chars.add(randomChar(UPPERCASE));
        chars.add(randomChar(LOWERCASE));
        chars.add(randomChar(DIGITS));
        chars.add(randomChar(SPECIAL));

        while (chars.size() < TEMP_PASSWORD_LENGTH) {
            chars.add(randomChar(ALL));
        }

        Collections.shuffle(chars, secureRandom);

        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (Character c : chars) {
            password.append(c);
        }

        return password.toString();
    }

    public String hashPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    public boolean validatePassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }

        try {
            return BCrypt.checkpw(rawPassword, hashedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        return UPPERCASE_PATTERN.matcher(password).matches()
                && LOWERCASE_PATTERN.matcher(password).matches()
                && DIGIT_PATTERN.matcher(password).matches()
                && SPECIAL_PATTERN.matcher(password).matches();
    }

    private char randomChar(String source) {
        return source.charAt(secureRandom.nextInt(source.length()));
    }
}
