package dev.mcdlss.core;

public record NativeProbeResult(boolean available, String version, String message) {
    public NativeProbeResult {
        version = normalize(version);
        message = normalize(message);
    }

    public static NativeProbeResult available(String version, String message) {
        return new NativeProbeResult(true, version, message);
    }

    public static NativeProbeResult unavailable(String version, String message) {
        return new NativeProbeResult(false, version, message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
