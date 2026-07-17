package dev.mcdlss.fabric;

import java.util.List;
import java.util.Locale;

public final class LiveDlssPresentationOverlayLines {
    public static List<String> fromSnapshot(LiveDlssPresentationSnapshot snapshot) {
        if (snapshot == null) {
            return List.of("live-dlss: false state=DISABLED");
        }
        return List.of(
                "live-dlss: " + snapshot.dlssReady() + " state=" + snapshot.state(),
                "dlss-size: " + snapshot.renderWidth() + "x" + snapshot.renderHeight()
                        + " -> " + snapshot.outputWidth() + "x" + snapshot.outputHeight(),
                "dlss-frames: frames=" + snapshot.successfulFrames() + "/"
                        + snapshot.targetFrames() + " retained=" + snapshot.retainedFrames()
                        + "/" + snapshot.targetRetainedFrames(),
                "dlss-slots: slots=" + snapshot.slotAUses() + "/" + snapshot.slotBUses()
                        + " changes=" + snapshot.outputHashChanges(),
                String.format(Locale.ROOT, "dlss-time: avg=%.2fms max=%.2fms",
                        snapshot.averageMilliseconds(), snapshot.maximumMilliseconds()),
                "dlss-message: " + snapshot.message());
    }

    private LiveDlssPresentationOverlayLines() {
    }
}
