package dev.mcdlss.core;

public record TemporalCameraState(
        float x, float y, float z,
        float lookX, float lookY, float lookZ,
        float fovRadians, float aspectRatio) {
    public TemporalCameraState {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(lookX, "lookX");
        requireFinite(lookY, "lookY");
        requireFinite(lookZ, "lookZ");
        requireFinite(fovRadians, "fovRadians");
        requireFinite(aspectRatio, "aspectRatio");
        if (fovRadians <= 0 || aspectRatio <= 0) {
            throw new IllegalArgumentException("Temporal camera projection is invalid");
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
