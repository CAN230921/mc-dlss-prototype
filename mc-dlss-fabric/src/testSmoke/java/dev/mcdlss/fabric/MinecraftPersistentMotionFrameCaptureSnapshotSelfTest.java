package dev.mcdlss.fabric;

public final class MinecraftPersistentMotionFrameCaptureSnapshotSelfTest {
    public static void main(String[] args) {
        MinecraftPersistentMotionFrameCaptureSnapshot complete = snapshot(true, true, 0, "33", "33");
        if (!complete.success()) {
            throw new AssertionError("Expected complete persistent motion capture");
        }
        if (snapshot(false, true, 0, "33", "33").success()) {
            throw new AssertionError("Missing stationary phase was accepted");
        }
        if (snapshot(true, false, 0, "33", "33").success()) {
            throw new AssertionError("Missing camera motion was accepted");
        }
        if (snapshot(true, true, 1, "33", "33").success()) {
            throw new AssertionError("Out-of-bounds motion was accepted");
        }
        if (snapshot(true, true, 0, "33", "44").success()) {
            throw new AssertionError("Mismatched motion hash was accepted");
        }
        String overlay = String.join("\n",
                MinecraftPersistentMotionFrameCaptureOverlayLines.fromSnapshot(complete));
        if (!overlay.contains("persistent-motion: true")
                || !overlay.contains("motion=100/409920")
                || !overlay.contains("phases=true/true")) {
            throw new AssertionError("Motion overlay mismatch");
        }
        System.out.println("MinecraftPersistentMotionFrameCaptureSnapshotSelfTest passed");
    }

    private static MinecraftPersistentMotionFrameCaptureSnapshot snapshot(
            boolean stationary, boolean moving, int outOfBounds, String glHash, String d3dHash) {
        MinecraftPersistentFrameCaptureSnapshot frame = new MinecraftPersistentFrameCaptureSnapshot(
                PersistentFrameCaptureState.COMPLETE, 854, 480, 120, 120, 30, 30,
                60, 60, 0, 119, 119, "11", "11", "22", "22",
                409920, 409920, 350000, 59920, 0.2f, 1.0f,
                9.0, 22.0, true, "test");
        return new MinecraftPersistentMotionFrameCaptureSnapshot(
                frame, 119, glHash, d3dHash, 409920, 100, outOfBounds,
                -3.0f, 4.0f, -2.0f, 1.0f, 4.5f,
                stationary, moving, true, 1, "test");
    }

    private MinecraftPersistentMotionFrameCaptureSnapshotSelfTest() {
    }
}
