package dev.mcdlss.fabric;

import java.util.List;
import java.util.Locale;

public final class MinecraftPersistentColorCaptureOverlayLines {
    public static List<String> fromSnapshot(MinecraftPersistentColorCaptureSnapshot snapshot) {
        MinecraftPersistentColorCaptureSnapshot safe = snapshot == null
                ? MinecraftPersistentColorCaptureSnapshot.waiting("Persistent capture has not started")
                : snapshot;
        return List.of(
                "persistent-color: " + safe.success() + " state=" + safe.state(),
                "persistent-progress: frames=" + safe.successfulFrames() + "/" + safe.targetFrames()
                        + " retain=" + safe.retainedFrames() + "/" + safe.targetRetainedFrames(),
                "persistent-slots: slots=" + safe.slotAUses() + "/" + safe.slotBUses()
                        + " resize=" + safe.resizeCount() + " changes=" + safe.hashChanges(),
                String.format(Locale.ROOT,
                        "persistent-time: avg=%.3fms max=%.3fms",
                        safe.averageMilliseconds(), safe.maximumMilliseconds()),
                "persistent-release: released=" + safe.resourcesReleased(),
                "persistent-message: " + safe.message());
    }

    private MinecraftPersistentColorCaptureOverlayLines() {
    }
}
