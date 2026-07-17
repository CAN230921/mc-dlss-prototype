package dev.mcdlss.fabric;

import dev.mcdlss.core.NativeTemporalConstants;

public final class MinecraftTemporalConstantsSelfTest {
    public static void main(String[] args) {
        CameraTemporalFrame previous = frame(4L);
        CameraTemporalFrame current = frame(5L);
        NativeTemporalConstants constants = MinecraftTemporalConstants.create(
                current,
                CameraTemporalReprojection.between(previous, current),
                identity(),
                new float[] {1, 2, 3},
                new float[] {0, 1, 0},
                new float[] {1, 0, 0},
                new float[] {0, 0, -1},
                0.05f, 1024.0f, 1.2f);
        if (constants.reset() || constants.cameraAspectRatio() != 2.0f
                || constants.motionVectorScaleX() != 0.005f
                || constants.motionVectorScaleY() != 0.01f
                || constants.jitterX() != 0.0f || constants.jitterY() != 0.0f) {
            throw new AssertionError("Minecraft temporal constants mismatch");
        }
        NativeTemporalConstants reset = MinecraftTemporalConstants.create(
                current, CameraTemporalReprojection.between(null, current), identity(),
                new float[3], new float[] {0, 1, 0}, new float[] {1, 0, 0},
                new float[] {0, 0, -1}, 0.05f, 1024.0f, 1.2f);
        if (!reset.reset()) {
            throw new AssertionError("Reset was not propagated");
        }
        System.out.println("MinecraftTemporalConstantsSelfTest passed");
    }

    private static CameraTemporalFrame frame(long index) {
        return new CameraTemporalFrame(identity(), identity(), identity(), 200, 100, index);
    }

    private static float[] identity() {
        return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private MinecraftTemporalConstantsSelfTest() {
    }
}
