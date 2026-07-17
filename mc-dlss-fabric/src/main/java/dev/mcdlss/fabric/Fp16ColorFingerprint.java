package dev.mcdlss.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public record Fp16ColorFingerprint(
        long hash,
        int pixelCount,
        int finiteChannelCount,
        int nonBlackPixelCount,
        boolean nonUniform) {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static Fp16ColorFingerprint fromRgba16f(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 8 != 0) {
            throw new IllegalArgumentException("RGBA16F bytes must contain complete pixels");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long hash = FNV_OFFSET;
        long firstPixel = buffer.getLong(0);
        boolean nonUniform = false;
        int finite = 0;
        int nonBlack = 0;
        for (int offset = 0; offset < bytes.length; offset += 8) {
            nonUniform |= buffer.getLong(offset) != firstPixel;
            boolean rgbNonBlack = false;
            for (int channel = 0; channel < 4; channel++) {
                int bits = Short.toUnsignedInt(buffer.getShort(offset + channel * 2));
                float value = decodeHalf(bits);
                if (Float.isFinite(value)) finite++;
                if (channel < 3 && (bits & 0x7fff) != 0) rgbNonBlack = true;
            }
            if (rgbNonBlack) nonBlack++;
            for (int index = 0; index < 8; index++) {
                hash ^= bytes[offset + index] & 0xffL;
                hash *= FNV_PRIME;
            }
        }
        return new Fp16ColorFingerprint(
                hash, bytes.length / 8, finite, nonBlack, nonUniform);
    }

    public boolean validForOutput() {
        return pixelCount > 0 && finiteChannelCount == pixelCount * 4
                && nonBlackPixelCount > 0 && nonUniform;
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
