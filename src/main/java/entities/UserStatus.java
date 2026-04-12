package entities;

public enum UserStatus {
    ACTIVE((byte) 1, "Active"),
    INACTIVE((byte) 0, "Inactive");

    private final byte dbValue;
    private final String label;

    UserStatus(byte dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public byte getDbValue() {
        return dbValue;
    }

    public static UserStatus fromDbValue(byte value) {
        return value == ACTIVE.dbValue ? ACTIVE : INACTIVE;
    }

    @Override
    public String toString() {
        return label;
    }
}
