package dev.mcdlss.core;

public final class NativeTemporalConstantsSelfTest {
    public static void main(String[] args) {
        float[] matrix = new float[16];
        for (int index = 0; index < matrix.length; index++) {
            matrix[index] = index + 1.0f;
        }
        NativeTemporalConstants constants = new NativeTemporalConstants(
                matrix, matrix, matrix, matrix,
                new float[] {1, 2, 3}, new float[] {0, 1, 0},
                new float[] {1, 0, 0}, new float[] {0, 0, -1},
                0.05f, 1024.0f, 1.0f, 2.0f,
                0.0f, 0.0f, 1.0f / 200.0f, 1.0f / 100.0f,
                true);
        matrix[0] = 99.0f;
        float[] exposed = constants.cameraViewToClip();
        exposed[1] = 99.0f;
        if (constants.cameraViewToClip()[0] != 1.0f
                || constants.cameraViewToClip()[1] != 2.0f) {
            throw new AssertionError("Temporal matrices are not immutable");
        }
        if (!constants.cameraMotionIncluded() || constants.motionVectors3D()
                || constants.motionVectorsJittered() || constants.depthInverted()
                || !constants.reset()) {
            throw new AssertionError("Temporal flags mismatch");
        }
        NativeTemporalConstants withoutReset = constants.withReset(false);
        NativeTemporalConstants forcedReset = withoutReset.withReset(true);
        if (withoutReset.reset() || !forcedReset.reset()
                || forcedReset.cameraViewToClip()[0] != constants.cameraViewToClip()[0]) {
            throw new AssertionError("Temporal reset override mismatch");
        }
        expectFailure(() -> new NativeTemporalConstants(
                new float[15], new float[16], new float[16], new float[16],
                new float[3], new float[3], new float[3], new float[3],
                0.1f, 10.0f, 1.0f, 1.0f, 0, 0, 1, 1, false));
        System.out.println("NativeTemporalConstantsSelfTest passed");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid constants were accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private NativeTemporalConstantsSelfTest() {
    }
}
