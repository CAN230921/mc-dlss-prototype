package dev.mcdlss.core;

public final class TemporalDiscontinuitySelfTest {
    public static void runAll() {
        TemporalCameraState base = state(0, 64, 0, 0, 0, 1, 1.2f, 16f / 9f);
        require(!TemporalDiscontinuity.requiresReset(base,
                state(0.2f, 64, 0.1f, 0.02f, 0, 0.9998f, 1.2f, 16f / 9f)),
                "Normal camera motion must preserve history");
        require(TemporalDiscontinuity.requiresReset(base,
                state(12, 64, 0, 0, 0, 1, 1.2f, 16f / 9f)),
                "Teleport must reset history");
        require(TemporalDiscontinuity.requiresReset(base,
                state(0, 64, 0, 1, 0, 0, 1.2f, 16f / 9f)),
                "Large view rotation must reset history");
        require(TemporalDiscontinuity.requiresReset(base,
                state(0, 64, 0, 0, 0, 1, 1.35f, 16f / 9f)),
                "FOV jump must reset history");
        require(TemporalDiscontinuity.requiresReset(base,
                state(0, 64, 0, 0, 0, 1, 1.2f, 4f / 3f)),
                "Aspect change must reset history");
    }

    private static TemporalCameraState state(
            float x, float y, float z, float lookX, float lookY, float lookZ,
            float fov, float aspect) {
        return new TemporalCameraState(x, y, z, lookX, lookY, lookZ, fov, aspect);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private TemporalDiscontinuitySelfTest() {
    }
}
