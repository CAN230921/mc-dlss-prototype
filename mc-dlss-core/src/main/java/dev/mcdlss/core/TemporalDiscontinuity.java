package dev.mcdlss.core;

public final class TemporalDiscontinuity {
    private static final float MAX_TRANSLATION_SQUARED = 64.0f;
    private static final float MIN_LOOK_DOT = 0.70710677f;
    private static final float MAX_FOV_DELTA = 0.05f;
    private static final float MAX_ASPECT_DELTA = 0.01f;

    public static boolean requiresReset(TemporalCameraState previous, TemporalCameraState current) {
        if (previous == null || current == null) return true;
        float dx = current.x() - previous.x();
        float dy = current.y() - previous.y();
        float dz = current.z() - previous.z();
        if (dx * dx + dy * dy + dz * dz > MAX_TRANSLATION_SQUARED) return true;
        if (Math.abs(current.fovRadians() - previous.fovRadians()) > MAX_FOV_DELTA) return true;
        if (Math.abs(current.aspectRatio() - previous.aspectRatio()) > MAX_ASPECT_DELTA) return true;
        float previousLength = length(previous.lookX(), previous.lookY(), previous.lookZ());
        float currentLength = length(current.lookX(), current.lookY(), current.lookZ());
        if (previousLength < 0.0001f || currentLength < 0.0001f) return true;
        float dot = (previous.lookX() * current.lookX()
                + previous.lookY() * current.lookY()
                + previous.lookZ() * current.lookZ()) / (previousLength * currentLength);
        return dot < MIN_LOOK_DOT;
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private TemporalDiscontinuity() {
    }
}
