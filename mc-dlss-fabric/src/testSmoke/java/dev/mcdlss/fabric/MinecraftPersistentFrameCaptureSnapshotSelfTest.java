package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftPersistentFrameCaptureSnapshotSelfTest {
    public static void main(String[] args) {
        MinecraftPersistentFrameCaptureSnapshot complete = snapshot(
                PersistentFrameCaptureState.COMPLETE, 120, 30, true,
                119, 119, "11", "11", "22", "22", 409920, 409920, 350000, 59920);
        if (!complete.success()) {
            throw new AssertionError("Expected complete persistent frame capture");
        }
        if (snapshot(PersistentFrameCaptureState.COMPLETE, 120, 30, true,
                119, 0, "11", "11", "22", "22", 409920, 409920, 350000, 59920)
                .success()) {
            throw new AssertionError("Static depth was accepted");
        }
        if (snapshot(PersistentFrameCaptureState.COMPLETE, 120, 30, true,
                119, 119, "11", "11", "22", "33", 409920, 409920, 350000, 59920)
                .success()) {
            throw new AssertionError("Mismatched depth hash was accepted");
        }
        List<String> lines = MinecraftPersistentFrameCaptureOverlayLines.fromSnapshot(complete);
        String joined = String.join("\n", lines);
        if (!joined.contains("persistent-frame: true")
                || !joined.contains("frames=120/120")
                || !joined.contains("depth=350000/59920")
                || !joined.contains("released=true")) {
            throw new AssertionError("Persistent frame overlay mismatch");
        }
        System.out.println("MinecraftPersistentFrameCaptureSnapshotSelfTest passed");
    }

    private static MinecraftPersistentFrameCaptureSnapshot snapshot(
            PersistentFrameCaptureState state,
            int frames,
            int retained,
            boolean released,
            int colorChanges,
            int depthChanges,
            String glColor,
            String d3dColor,
            String glDepth,
            String d3dDepth,
            int finite,
            int inRange,
            int scene,
            int far) {
        return new MinecraftPersistentFrameCaptureSnapshot(
                state, 854, 480, 120, frames, 30, retained,
                60, 60, 0, colorChanges, depthChanges,
                glColor, d3dColor, glDepth, d3dDepth,
                finite, inRange, scene, far, 0.2f, 1.0f,
                9.0, 22.0, released, "test");
    }

    private MinecraftPersistentFrameCaptureSnapshotSelfTest() {
    }
}
