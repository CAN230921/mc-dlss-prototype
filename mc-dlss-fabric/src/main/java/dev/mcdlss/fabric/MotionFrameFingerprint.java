package dev.mcdlss.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public record MotionFrameFingerprint(
        long hash,
        int vectorCount,
        int finiteVectorCount,
        int nonZeroVectorCount,
        int outOfBoundsVectorCount,
        float minimumX,
        float maximumX,
        float minimumY,
        float maximumY,
        float maximumMagnitude) {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static MotionFrameFingerprint fromRg16f(
            byte[] bytes, float maximumAbsoluteX, float maximumAbsoluteY) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0
                || !Float.isFinite(maximumAbsoluteX) || maximumAbsoluteX <= 0.0f
                || !Float.isFinite(maximumAbsoluteY) || maximumAbsoluteY <= 0.0f) {
            throw new IllegalArgumentException("RG16F bytes and bounds must be valid");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long hash = FNV_OFFSET;
        int finite = 0;
        int nonZero = 0;
        int outOfBounds = 0;
        boolean hasFinite = false;
        float minimumX = 0.0f;
        float maximumX = 0.0f;
        float minimumY = 0.0f;
        float maximumY = 0.0f;
        float maximumMagnitude = 0.0f;
        for (int offset = 0; offset < bytes.length; offset += 4) {
            for (int index = 0; index < 4; index++) {
                hash ^= bytes[offset + index] & 0xffL;
                hash *= FNV_PRIME;
            }
            float x = decodeHalf(Short.toUnsignedInt(buffer.getShort(offset)));
            float y = decodeHalf(Short.toUnsignedInt(buffer.getShort(offset + 2)));
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                continue;
            }
            finite++;
            if (x != 0.0f || y != 0.0f) {
                nonZero++;
            }
            if (Math.abs(x) > maximumAbsoluteX || Math.abs(y) > maximumAbsoluteY) {
                outOfBounds++;
            }
            maximumMagnitude = Math.max(maximumMagnitude, (float) Math.hypot(x, y));
            if (!hasFinite) {
                minimumX = maximumX = x;
                minimumY = maximumY = y;
                hasFinite = true;
            } else {
                minimumX = Math.min(minimumX, x);
                maximumX = Math.max(maximumX, x);
                minimumY = Math.min(minimumY, y);
                maximumY = Math.max(maximumY, y);
            }
        }
        return new MotionFrameFingerprint(
                hash, bytes.length / 4, finite, nonZero, outOfBounds,
                minimumX, maximumX, minimumY, maximumY, maximumMagnitude);
    }

    public boolean validForFrame() {
        return vectorCount > 0 && finiteVectorCount == vectorCount
                && outOfBoundsVectorCount == 0;
    }

    public String hashHex() {
        return String.format(Locale.ROOT, "%016x", hash);
    }

    private static float decodeHalf(int bits) {
        boolean negative = (bits & 0x8000) != 0;
        int exponent = (bits >>> 10) & 0x1f;
        int fraction = bits & 0x03ff;
        float value;
        if (exponent == 0) {
            value = fraction == 0 ? 0.0f : Math.scalb((float) fraction, -24);
        } else if (exponent == 0x1f) {
            value = fraction == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
        } else {
            value = Math.scalb((float) (fraction + 1024), exponent - 25);
        }
        return negative ? -value : value;
    }
}
