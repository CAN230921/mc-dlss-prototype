package dev.mcdlss.core;

public record NativeMotionFrameReadbackFingerprint(
        boolean available,
        NativeFrameReadbackFingerprint frame,
        long motionHash,
        int finiteVectorCount,
        int nonZeroVectorCount,
        int outOfBoundsVectorCount,
        float minimumX,
        float maximumX,
        float minimumY,
        float maximumY,
        float maximumMagnitude,
        String message) {
    public NativeMotionFrameReadbackFingerprint {
        frame = frame == null
                ? NativeFrameReadbackFingerprint.unavailable("Missing frame fingerprint") : frame;
        message = message == null ? "" : message.trim();
    }

    public static NativeMotionFrameReadbackFingerprint available(
            NativeFrameReadbackFingerprint frame, long motionHash,
            int finiteVectorCount, int nonZeroVectorCount, int outOfBoundsVectorCount,
            float minimumX, float maximumX, float minimumY, float maximumY,
            float maximumMagnitude) {
        if (frame == null || !frame.available() || finiteVectorCount < 0
                || nonZeroVectorCount < 0 || outOfBoundsVectorCount < 0
                || nonZeroVectorCount > finiteVectorCount
                || outOfBoundsVectorCount > finiteVectorCount
                || !Float.isFinite(minimumX) || !Float.isFinite(maximumX)
                || !Float.isFinite(minimumY) || !Float.isFinite(maximumY)
                || !Float.isFinite(maximumMagnitude) || minimumX > maximumX
                || minimumY > maximumY || maximumMagnitude < 0.0f) {
            return unavailable("Invalid persistent motion frame fingerprint");
        }
        return new NativeMotionFrameReadbackFingerprint(
                true, frame, motionHash, finiteVectorCount, nonZeroVectorCount,
                outOfBoundsVectorCount, minimumX, maximumX, minimumY, maximumY,
                maximumMagnitude, "Persistent motion frame fingerprint ready");
    }

    public static NativeMotionFrameReadbackFingerprint unavailable(String message) {
        return new NativeMotionFrameReadbackFingerprint(
                false, NativeFrameReadbackFingerprint.unavailable(message),
                0, 0, 0, 0, 0, 0, 0, 0, 0, message);
    }
}
