package dev.mcdlss.fabric;

public record MinecraftPersistentColorCaptureSnapshot(
        PersistentColorCaptureState state,
        int width,
        int height,
        int targetFrames,
        int successfulFrames,
        int targetRetainedFrames,
        int retainedFrames,
        int slotAUses,
        int slotBUses,
        int resizeCount,
        int hashChanges,
        String openGlHash,
        String d3d12Hash,
        double averageMilliseconds,
        double maximumMilliseconds,
        boolean resourcesReleased,
        String message) {
    public MinecraftPersistentColorCaptureSnapshot {
        state = state == null ? PersistentColorCaptureState.FAILED : state;
        openGlHash = normalizeHash(openGlHash);
        d3d12Hash = normalizeHash(d3d12Hash);
        message = message == null ? "" : message.trim();
    }

    public static MinecraftPersistentColorCaptureSnapshot waiting(String message) {
        return new MinecraftPersistentColorCaptureSnapshot(
                PersistentColorCaptureState.WAITING,
                0, 0, 120, 0, 30, 0,
                0, 0, 0, 0, "", "", 0, 0, false, message);
    }

    public boolean terminal() {
        return state == PersistentColorCaptureState.COMPLETE
                || state == PersistentColorCaptureState.FAILED;
    }

    public boolean success() {
        return state == PersistentColorCaptureState.COMPLETE
                && width > 0 && height > 0
                && successfulFrames == targetFrames && targetFrames == 120
                && retainedFrames == targetRetainedFrames && targetRetainedFrames == 30
                && slotAUses > 0 && slotBUses > 0
                && hashChanges > 0
                && openGlHash.matches("[0-9a-f]{16}")
                && openGlHash.equals(d3d12Hash)
                && averageMilliseconds >= 0
                && maximumMilliseconds >= averageMilliseconds
                && resourcesReleased;
    }

    private static String normalizeHash(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
