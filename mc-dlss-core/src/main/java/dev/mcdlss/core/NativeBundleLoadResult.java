package dev.mcdlss.core;

public record NativeBundleLoadResult(boolean available, String stage, String message) {
    public NativeBundleLoadResult {
        stage = stage == null ? "UNKNOWN" : stage;
        message = message == null ? "" : message;
    }

    public static NativeBundleLoadResult ready(String message) {
        return new NativeBundleLoadResult(true, "READY", message);
    }

    public static NativeBundleLoadResult unavailable(String stage, String message) {
        return new NativeBundleLoadResult(false, stage, message);
    }
}
