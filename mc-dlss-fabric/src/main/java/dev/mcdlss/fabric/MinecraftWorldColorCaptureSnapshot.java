package dev.mcdlss.fabric;

public record MinecraftWorldColorCaptureSnapshot(
        boolean attempted,
        boolean framebufferComplete,
        boolean blitCompleted,
        boolean openGlContentValid,
        boolean d3d12ReadbackSubmitted,
        boolean openGlWaitCompleted,
        boolean fingerprintMatched,
        boolean resourcesReleased,
        int sourceWidth,
        int sourceHeight,
        String openGlHash,
        String d3d12Hash,
        String message) {
    public MinecraftWorldColorCaptureSnapshot {
        openGlHash = normalizeHash(openGlHash);
        d3d12Hash = normalizeHash(d3d12Hash);
        message = message == null ? "" : message.trim();
    }

    public static MinecraftWorldColorCaptureSnapshot failure(String message) {
        return new MinecraftWorldColorCaptureSnapshot(
                true, false, false, false, false, false, false, false,
                0, 0, "", "", message);
    }

    public boolean success() {
        return attempted
                && framebufferComplete
                && blitCompleted
                && openGlContentValid
                && d3d12ReadbackSubmitted
                && openGlWaitCompleted
                && fingerprintMatched
                && resourcesReleased
                && sourceWidth > 0
                && sourceHeight > 0
                && openGlHash.matches("[0-9a-f]{16}")
                && openGlHash.equals(d3d12Hash);
    }

    private static String normalizeHash(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
