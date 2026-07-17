package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftLiveInteropOverlayLines {
    private static final int MAX_MESSAGE_LENGTH = 72;

    public static List<String> fromSnapshot(MinecraftLiveInteropSnapshot snapshot) {
        MinecraftLiveInteropSnapshot safe = snapshot == null
                ? MinecraftLiveInteropSnapshot.failure("Live shared-resource probe has not run")
                : snapshot;
        return List.of(
                "live-share: " + safe.success()
                        + " session=" + safe.sessionCreated()
                        + " memory=" + safe.memoryImported()
                        + " semaphore=" + safe.semaphoreImported(),
                "live-sync: gl-write=" + safe.openGlWriteSubmitted()
                        + " d3d-submit=" + safe.d3d12ReadbackSubmitted()
                        + " gl-wait=" + safe.openGlWaitCompleted(),
                "live-verify: readback=" + safe.readbackMatched()
                        + " released=" + safe.resourcesReleased(),
                "live-message: " + truncate(safe.message()));
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private MinecraftLiveInteropOverlayLines() {
    }
}
