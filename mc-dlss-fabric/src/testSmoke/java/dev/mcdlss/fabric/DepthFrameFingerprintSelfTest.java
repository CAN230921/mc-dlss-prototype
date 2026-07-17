package dev.mcdlss.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class DepthFrameFingerprintSelfTest {
    public static void main(String[] args) {
        float[] values = {0.25f, 1.0f, 0.5f, 1.0f};
        DepthFrameFingerprint result = DepthFrameFingerprint.fromR32f(values);
        if (!result.nonUniform() || result.finiteSampleCount() != 4
                || result.inRangeSampleCount() != 4
                || result.sceneSampleCount() != 2 || result.farSampleCount() != 2
                || result.minimum() != 0.25f || result.maximum() != 1.0f) {
            throw new AssertionError("Depth statistics mismatch");
        }
        ByteBuffer bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            bytes.putFloat(value);
        }
        DepthFrameFingerprint fromBytes = DepthFrameFingerprint.fromR32fBytes(bytes.array());
        if (fromBytes.hash() != result.hash()) {
            throw new AssertionError("Depth byte order mismatch");
        }
        DepthFrameFingerprint invalid = DepthFrameFingerprint.fromR32f(new float[] {
            Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f
        });
        if (invalid.finiteSampleCount() != 2 || invalid.inRangeSampleCount() != 0
                || invalid.validForFrame()) {
            throw new AssertionError("Invalid depth values were accepted");
        }
        System.out.println("DepthFrameFingerprintSelfTest passed");
    }

    private DepthFrameFingerprintSelfTest() {
    }
}
