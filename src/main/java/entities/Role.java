package entities;

import java.util.Arrays;

public enum Role {
    ADMIN("ROLE_ADMIN", "Admin"),
    TEACHER("ROLE_TEACHER", "Teacher"),
    STUDENT("ROLE_STUDENT", "Student"),
    STAFF("ROLE_STAFF", "Staff"),
    USER("ROLE_USER", "User");

    private final String dbValue;
    private final String label;

    Role(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static Role fromDbValue(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }

        return Arrays.stream(values())
                .filter(role -> role.dbValue.equalsIgnoreCase(value) || role.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(USER);
    }

    @Override
    public String toString() {
        return label;
    }
}
