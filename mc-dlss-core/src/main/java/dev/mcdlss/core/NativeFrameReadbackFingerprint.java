package dev.mcdlss.core;

import java.util.Locale;

public record NativeFrameReadbackFingerprint(
        boolean available,
        long colorHash,
        boolean colorNonUniform,
        int colorNonBlackPixelCount,
        long depthHash,
        boolean depthNonUniform,
        int depthFiniteSampleCount,
        int depthInRangeSampleCount,
        int depthSceneSampleCount,
        int depthFarSampleCount,
        float minimumDepth,
        float maximumDepth,
        String message) {
    public NativeFrameReadbackFingerprint {
        message = message == null ? "" : message.trim();
    }

    public static NativeFrameReadbackFingerprint available(
            long colorHash,
            boolean colorNonUniform,
            int colorNonBlackPixelCount,
            long depthHash,
            boolean depthNonUniform,
            int depthFiniteSampleCount,
            int depthInRangeSampleCount,
            int depthSceneSampleCount,
            int depthFarSampleCount,
            float minimumDepth,
            float maximumDepth) {
        if (colorNonBlackPixelCount < 0 || depthFiniteSampleCount < 0
                || depthInRangeSampleCount < 0 || depthSceneSampleCount < 0
                || depthFarSampleCount < 0 || !Float.isFinite(minimumDepth)
                || !Float.isFinite(maximumDepth) || minimumDepth > maximumDepth) {
            return unavailable("Invalid persistent frame fingerprint");
        }
        return new NativeFrameReadbackFingerprint(
                true, colorHash, colorNonUniform, colorNonBlackPixelCount,
                depthHash, depthNonUniform, depthFiniteSampleCount,
                depthInRangeSampleCount, depthSceneSampleCount, depthFarSampleCount,
                minimumDepth, maximumDepth, "Persistent frame fingerprint ready");
    }

    public static NativeFrameReadbackFingerprint unavailable(String message) {
        return new NativeFrameReadbackFingerprint(
                false, 0, false, 0, 0, false,
                0, 0, 0, 0, 0.0f, 0.0f, message);
    }

    public String colorHashHex() {
        return String.format(Locale.ROOT, "%016x", colorHash);
    }

    public String depthHashHex() {
        return String.format(Locale.ROOT, "%016x", depthHash);
    }
}
