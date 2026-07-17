package dev.mcdlss.core;

public enum DlssResourcePathStatus {
    UNKNOWN(true),
    READY(false),
    BLOCKED_UNSUPPORTED_BACKEND(true),
    BLOCKED_MISSING_NATIVE_RESOURCES(true),
    BLOCKED_MISSING_COMMAND_CONTEXT(true),
    BLOCKED_MISSING_MOTION_VECTORS(true);

    private final boolean blocked;

    DlssResourcePathStatus(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean blocked() {
        return blocked;
    }
}
