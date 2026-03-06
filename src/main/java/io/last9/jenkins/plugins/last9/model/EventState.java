package io.last9.jenkins.plugins.last9.model;

public enum EventState {
    START("start"),
    STOP("stop");

    private final String value;

    EventState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EventState fromString(String s) {
        if (s == null || s.isBlank()) {
            return STOP;
        }
        return switch (s.toLowerCase().trim()) {
            case "start" -> START;
            case "stop" -> STOP;
            default -> throw new IllegalArgumentException("Invalid event state: " + s + ". Must be 'start' or 'stop'.");
        };
    }
}
