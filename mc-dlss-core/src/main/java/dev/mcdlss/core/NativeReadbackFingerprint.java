package dev.mcdlss.core;

import java.util.Locale;

public record NativeReadbackFingerprint(
        boolean available,
        long hash,
        boolean nonUniform,
        int nonBlackPixelCount,
        String message) {
    public NativeReadbackFingerprint {
        message = message == null ? "" : message.trim();
    }

    public static NativeReadbackFingerprint available(
            long hash, boolean nonUniform, int nonBlackPixelCount) {
        if (nonBlackPixelCount < 0) {
            return unavailable("Invalid native non-black pixel count");
        }
        return new NativeReadbackFingerprint(
                true, hash, nonUniform, nonBlackPixelCount, "Readback fingerprint ready");
    }

    public static NativeReadbackFingerprint unavailable(String message) {
        return new NativeReadbackFingerprint(false, 0L, false, 0, message);
    }

    public String hashHex() {
        return String.format(Locale.ROOT, "%016x", hash);
    }
}
