package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftWorldColorCaptureOverlayLines {
    public static List<String> fromSnapshot(MinecraftWorldColorCaptureSnapshot snapshot) {
        MinecraftWorldColorCaptureSnapshot safe = snapshot == null
                ? MinecraftWorldColorCaptureSnapshot.failure("World-color capture has not run")
                : snapshot;
        return List.of(
                "world-color: " + safe.success()
                        + " fbo=" + safe.framebufferComplete()
                        + " blit=" + safe.blitCompleted(),
                "world-content: content=" + safe.openGlContentValid()
                        + " d3d-submit=" + safe.d3d12ReadbackSubmitted()
                        + " gl-wait=" + safe.openGlWaitCompleted(),
                "world-hash: hash-match=" + safe.fingerprintMatched()
                        + " gl=" + displayHash(safe.openGlHash())
                        + " d3d=" + displayHash(safe.d3d12Hash()),
                "world-release: released=" + safe.resourcesReleased(),
                "world-message: " + safe.message());
    }

    private static String displayHash(String value) {
        return value.isEmpty() ? "unavailable" : value;
    }

    private MinecraftWorldColorCaptureOverlayLines() {
    }
}
