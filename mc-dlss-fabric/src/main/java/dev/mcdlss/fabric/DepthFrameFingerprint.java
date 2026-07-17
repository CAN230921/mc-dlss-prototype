package dev.mcdlss.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public record DepthFrameFingerprint(
        long hash,
        boolean nonUniform,
        int sampleCount,
        int finiteSampleCount,
        int inRangeSampleCount,
        int sceneSampleCount,
        int farSampleCount,
        float minimum,
        float maximum) {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final float FAR_THRESHOLD = 0.9999f;

    public static DepthFrameFingerprint fromR32f(float[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Depth values are required");
        }
        ByteBuffer bytes = ByteBuffer.allocate(Math.multiplyExact(values.length, 4))
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            bytes.putInt(Float.floatToRawIntBits(value));
        }
        return fromR32fBytes(bytes.array());
    }

    public static DepthFrameFingerprint fromR32fBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0) {
            throw new IllegalArgumentException("R32F bytes must contain complete pixels");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int sampleCount = bytes.length / 4;
        int firstBits = buffer.getInt(0);
        long hash = FNV_OFFSET;
        boolean nonUniform = false;
        int finite = 0;
        int inRange = 0;
        int scene = 0;
        int far = 0;
        boolean hasFinite = false;
        float minimum = 0.0f;
        float maximum = 0.0f;
        for (int offset = 0; offset < bytes.length; offset += 4) {
            int bits = buffer.getInt(offset);
            nonUniform |= bits != firstBits;
            for (int channel = 0; channel < 4; channel++) {
                hash ^= bytes[offset + channel] & 0xffL;
                hash *= FNV_PRIME;
            }
            float value = Float.intBitsToFloat(bits);
            if (!Float.isFinite(value)) {
                continue;
            }
            finite++;
            if (!hasFinite) {
                minimum = value;
                maximum = value;
                hasFinite = true;
            } else {
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
            if (value < 0.0f || value > 1.0f) {
                continue;
            }
            inRange++;
            if (value < FAR_THRESHOLD) {
                scene++;
            } else {
                far++;
            }
        }
        return new DepthFrameFingerprint(
                hash, nonUniform, sampleCount, finite, inRange,
                scene, far, minimum, maximum);
    }

    public boolean validForFrame() {
        return nonUniform && sampleCount > 0
                && finiteSampleCount == sampleCount
                && inRangeSampleCount == sampleCount
                && sceneSampleCount > 0 && farSampleCount > 0;
    }

    public String hashHex() {
        return String.format(Locale.ROOT, "%016x", hash);
    }
}
