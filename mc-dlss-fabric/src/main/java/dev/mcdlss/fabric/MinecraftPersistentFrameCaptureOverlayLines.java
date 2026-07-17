package dev.mcdlss.fabric;

import java.util.List;
import java.util.Locale;

public final class MinecraftPersistentFrameCaptureOverlayLines {
    public static List<String> fromSnapshot(
            MinecraftPersistentFrameCaptureSnapshot snapshot) {
        if (snapshot == null) {
            return List.of("persistent-frame: false state=WAITING");
        }
        return List.of(
                "persistent-frame: " + snapshot.success() + " state=" + snapshot.state(),
                "frame-progress: frames=" + snapshot.successfulFrames() + "/"
                        + snapshot.targetFrames() + " retained=" + snapshot.retainedFrames()
                        + "/" + snapshot.targetRetainedFrames(),
                "frame-slots: slots=" + snapshot.slotAUses() + "/" + snapshot.slotBUses()
                        + " changes=" + snapshot.colorHashChanges() + "/"
                        + snapshot.depthHashChanges(),
                "frame-depth: depth=" + snapshot.depthSceneSampleCount() + "/"
                        + snapshot.depthFarSampleCount() + " valid="
                        + snapshot.depthInRangeSampleCount(),
                String.format(Locale.ROOT, "frame-time: avg=%.3fms max=%.3fms",
                        snapshot.averageMilliseconds(), snapshot.maximumMilliseconds()),
                "frame-release: released=" + snapshot.resourcesReleased(),
                "frame-message: " + snapshot.message());
    }

    private MinecraftPersistentFrameCaptureOverlayLines() {
    }
}
