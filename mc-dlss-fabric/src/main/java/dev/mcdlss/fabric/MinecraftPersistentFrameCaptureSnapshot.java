package dev.mcdlss.fabric;

public record MinecraftPersistentFrameCaptureSnapshot(
        PersistentFrameCaptureState state,
        int width,
        int height,
        int targetFrames,
        int successfulFrames,
        int targetRetainedFrames,
        int retainedFrames,
        int slotAUses,
        int slotBUses,
        int resizeCount,
        int colorHashChanges,
        int depthHashChanges,
        String openGlColorHash,
        String d3d12ColorHash,
        String openGlDepthHash,
        String d3d12DepthHash,
        int depthFiniteSampleCount,
        int depthInRangeSampleCount,
        int depthSceneSampleCount,
        int depthFarSampleCount,
        float minimumDepth,
        float maximumDepth,
        double averageMilliseconds,
        double maximumMilliseconds,
        boolean resourcesReleased,
        String message) {
    public MinecraftPersistentFrameCaptureSnapshot {
        state = state == null ? PersistentFrameCaptureState.WAITING : state;
        openGlColorHash = safe(openGlColorHash);
        d3d12ColorHash = safe(d3d12ColorHash);
        openGlDepthHash = safe(openGlDepthHash);
        d3d12DepthHash = safe(d3d12DepthHash);
        message = safe(message);
    }

    public boolean success() {
        long pixelCount = (long) width * height;
        return state == PersistentFrameCaptureState.COMPLETE
                && width > 0 && height > 0 && pixelCount <= Integer.MAX_VALUE
                && successfulFrames == targetFrames && targetFrames == 120
                && retainedFrames == targetRetainedFrames && targetRetainedFrames == 30
                && slotAUses > 0 && slotBUses > 0
                && colorHashChanges > 0 && depthHashChanges > 0
                && !openGlColorHash.isEmpty()
                && openGlColorHash.equals(d3d12ColorHash)
                && !openGlDepthHash.isEmpty()
                && openGlDepthHash.equals(d3d12DepthHash)
                && depthFiniteSampleCount == pixelCount
                && depthInRangeSampleCount == pixelCount
                && depthSceneSampleCount > 0 && depthFarSampleCount > 0
                && Float.isFinite(minimumDepth) && Float.isFinite(maximumDepth)
                && minimumDepth >= 0.0f && maximumDepth <= 1.0f
                && minimumDepth <= maximumDepth && resourcesReleased;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
