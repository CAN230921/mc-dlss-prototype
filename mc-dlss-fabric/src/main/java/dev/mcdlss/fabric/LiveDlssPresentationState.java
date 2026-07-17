package dev.mcdlss.fabric;

public enum LiveDlssPresentationState {
    DISABLED,
    INITIALIZING,
    CAPTURING,
    RETAINING,
    COMPLETE,
    FAILED;

    public boolean dlssReady() {
        return this == COMPLETE;
    }

    public boolean usesVanillaFallback() {
        return this == DISABLED || this == FAILED;
    }
}
