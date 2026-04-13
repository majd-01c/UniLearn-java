package entities.forum;

public enum TopicStatus {
    OPEN("open", "Open", "primary"),
    SOLVED("solved", "Solved", "success"),
    LOCKED("locked", "Locked", "secondary");

    private final String value;
    private final String label;
    private final String color;

    TopicStatus(String value, String label, String color) {
        this.value = value;
        this.label = label;
        this.color = color;
    }

    public String getValue() {
        return value;
    }

    public String label() {
        return label;
    }

    public String color() {
        return color;
    }

    public static TopicStatus fromValue(String value) {
        if (value == null) return OPEN;
        for (TopicStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return OPEN;
    }
}
