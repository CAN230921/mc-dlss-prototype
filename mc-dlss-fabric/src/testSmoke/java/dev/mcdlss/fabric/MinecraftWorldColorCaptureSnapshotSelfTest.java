package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftWorldColorCaptureSnapshotSelfTest {
    public static void main(String[] args) {
        requiresEveryCaptureStage();
        formatsCompactEvidence();
        System.out.println("MinecraftWorldColorCaptureSnapshotSelfTest passed");
    }

    private static void requiresEveryCaptureStage() {
        MinecraftWorldColorCaptureSnapshot complete = completeSnapshot();
        if (!complete.success()) {
            throw new AssertionError("Expected complete world-color capture");
        }
        MinecraftWorldColorCaptureSnapshot mismatch = new MinecraftWorldColorCaptureSnapshot(
                true, true, true, true, true, true, false, true,
                1920, 1080, "0011223344556677", "8899aabbccddeeff",
                "Fingerprint mismatch");
        if (mismatch.success()) {
            throw new AssertionError("Fingerprint mismatch must block success");
        }
    }

    private static void formatsCompactEvidence() {
        List<String> lines = MinecraftWorldColorCaptureOverlayLines.fromSnapshot(
                completeSnapshot());
        String joined = String.join("\n", lines);
        if (!joined.contains("world-color: true")
                || !joined.contains("blit=true")
                || !joined.contains("content=true")
                || !joined.contains("hash-match=true")
                || !joined.contains("released=true")) {
            throw new AssertionError("World-color overlay omitted acceptance stages");
        }
    }

    private static MinecraftWorldColorCaptureSnapshot completeSnapshot() {
        return new MinecraftWorldColorCaptureSnapshot(
                true, true, true, true, true, true, true, true,
                1920, 1080, "23c04fe567ee71cd", "23c04fe567ee71cd",
                "World color capture completed");
    }

    private MinecraftWorldColorCaptureSnapshotSelfTest() {
    }
}
