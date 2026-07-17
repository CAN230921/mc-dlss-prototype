package dev.mcdlss.fabric;

public record MinecraftPersistentMotionFrameCaptureSnapshot(
        MinecraftPersistentFrameCaptureSnapshot frame,
        int motionHashChanges,
        String openGlMotionHash,
        String d3d12MotionHash,
        int finiteVectorCount,
        int nonZeroVectorCount,
        int outOfBoundsVectorCount,
        float minimumX,
        float maximumX,
        float minimumY,
        float maximumY,
        float maximumMagnitude,
        boolean stationaryPhaseObserved,
        boolean cameraMotionPhaseObserved,
        boolean temporalConstantsValid,
        int resetFrameCount,
        String message) {
    public MinecraftPersistentMotionFrameCaptureSnapshot {
        openGlMotionHash = safe(openGlMotionHash);
        d3d12MotionHash = safe(d3d12MotionHash);
        message = safe(message);
    }

    public boolean success() {
        if (frame == null || !frame.success()) {
            return false;
        }
        long pixelCount = (long) frame.width() * frame.height();
        return motionHashChanges > 0
                && !openGlMotionHash.isEmpty()
                && openGlMotionHash.equals(d3d12MotionHash)
                && finiteVectorCount == pixelCount
                && nonZeroVectorCount >= 0 && nonZeroVectorCount <= finiteVectorCount
                && outOfBoundsVectorCount == 0
                && Float.isFinite(minimumX) && Float.isFinite(maximumX)
                && Float.isFinite(minimumY) && Float.isFinite(maximumY)
                && Float.isFinite(maximumMagnitude)
                && minimumX <= maximumX && minimumY <= maximumY
                && maximumMagnitude >= 0.0f
                && stationaryPhaseObserved && cameraMotionPhaseObserved
                && temporalConstantsValid && resetFrameCount > 0;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
