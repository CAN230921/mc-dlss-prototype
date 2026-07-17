package dev.mcdlss.fabric;

import java.util.List;
import java.util.Locale;

public final class MinecraftPersistentMotionFrameCaptureOverlayLines {
    public static List<String> fromSnapshot(
            MinecraftPersistentMotionFrameCaptureSnapshot snapshot) {
        if (snapshot == null) {
            return List.of("persistent-motion: false state=WAITING");
        }
        return List.of(
                "persistent-motion: " + snapshot.success() + " state="
                        + (snapshot.frame() == null ? "WAITING" : snapshot.frame().state()),
                "motion-vectors: motion=" + snapshot.nonZeroVectorCount() + "/"
                        + snapshot.finiteVectorCount() + " out="
                        + snapshot.outOfBoundsVectorCount(),
                "motion-phases: phases=" + snapshot.stationaryPhaseObserved() + "/"
                        + snapshot.cameraMotionPhaseObserved() + " resets="
                        + snapshot.resetFrameCount(),
                String.format(Locale.ROOT, "motion-range: x=%.2f..%.2f y=%.2f..%.2f max=%.2f",
                        snapshot.minimumX(), snapshot.maximumX(), snapshot.minimumY(),
                        snapshot.maximumY(), snapshot.maximumMagnitude()),
                "motion-message: " + snapshot.message());
    }

    private MinecraftPersistentMotionFrameCaptureOverlayLines() {
    }
}
