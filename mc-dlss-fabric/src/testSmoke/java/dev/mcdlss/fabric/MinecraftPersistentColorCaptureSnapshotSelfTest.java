package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftPersistentColorCaptureSnapshotSelfTest {
    public static void main(String[] args) {
        schedulesIndependentFencePairs();
        gatesCompleteLifecycle();
        formatsProgressAndTiming();
        System.out.println("MinecraftPersistentColorCaptureSnapshotSelfTest passed");
    }

    private static void schedulesIndependentFencePairs() {
        PersistentFenceValues first = PersistentFenceValues.forUse(0);
        PersistentFenceValues second = PersistentFenceValues.forUse(1);
        if (first.waitValue() != 1 || first.signalValue() != 2
                || second.waitValue() != 3 || second.signalValue() != 4) {
            throw new AssertionError("Unexpected per-slot fence schedule");
        }
    }

    private static void gatesCompleteLifecycle() {
        MinecraftPersistentColorCaptureSnapshot complete = completeSnapshot();
        if (!complete.success()) {
            throw new AssertionError("Expected complete persistent capture");
        }
        MinecraftPersistentColorCaptureSnapshot noChange = new MinecraftPersistentColorCaptureSnapshot(
                PersistentColorCaptureState.COMPLETE,
                1920, 1080, 120, 120, 30, 30,
                60, 60, 0, 0,
                "0011223344556677", "0011223344556677",
                1.5, 3.0, true, "Static capture");
        if (noChange.success()) {
            throw new AssertionError("At least one frame hash change is required");
        }
    }

    private static void formatsProgressAndTiming() {
        List<String> lines = MinecraftPersistentColorCaptureOverlayLines.fromSnapshot(
                completeSnapshot());
        String joined = String.join("\n", lines);
        if (!joined.contains("persistent-color: true")
                || !joined.contains("frames=120/120")
                || !joined.contains("retain=30/30")
                || !joined.contains("slots=60/60")
                || !joined.contains("avg=1.500ms")
                || !joined.contains("released=true")) {
            throw new AssertionError("Persistent overlay omitted final metrics");
        }
    }

    private static MinecraftPersistentColorCaptureSnapshot completeSnapshot() {
        return new MinecraftPersistentColorCaptureSnapshot(
                PersistentColorCaptureState.COMPLETE,
                1920, 1080, 120, 120, 30, 30,
                60, 60, 1, 15,
                "0011223344556677", "0011223344556677",
                1.5, 3.0, true, "Persistent capture completed");
    }

    private MinecraftPersistentColorCaptureSnapshotSelfTest() {
    }
}
