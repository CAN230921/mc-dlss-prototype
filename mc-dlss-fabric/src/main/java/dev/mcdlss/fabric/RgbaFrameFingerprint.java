package dev.mcdlss.fabric;

import java.util.Locale;

public record RgbaFrameFingerprint(
        long hash,
        boolean nonUniform,
        int nonBlackPixelCount) {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static RgbaFrameFingerprint fromRgba8(byte[] pixels) {
        if (pixels == null || pixels.length == 0 || pixels.length % 4 != 0) {
            throw new IllegalArgumentException("RGBA8 pixels must contain complete pixels");
        }

        long hash = FNV_OFFSET_BASIS;
        int firstRgba = rgba(pixels, 0);
        boolean nonUniform = false;
        int nonBlackPixels = 0;
        for (int offset = 0; offset < pixels.length; offset += 4) {
            int current = rgba(pixels, offset);
            nonUniform |= current != firstRgba;
            if ((current & 0xffffff00) != 0) {
                nonBlackPixels++;
            }
            for (int channel = 0; channel < 4; channel++) {
                hash ^= Byte.toUnsignedInt(pixels[offset + channel]);
                hash *= FNV_PRIME;
            }
        }
        return new RgbaFrameFingerprint(hash, nonUniform, nonBlackPixels);
    }

    public String hashHex() {
        return String.format(Locale.ROOT, "%016x", hash);
    }

    private static int rgba(byte[] pixels, int offset) {
        return Byte.toUnsignedInt(pixels[offset]) << 24
                | Byte.toUnsignedInt(pixels[offset + 1]) << 16
                | Byte.toUnsignedInt(pixels[offset + 2]) << 8
                | Byte.toUnsignedInt(pixels[offset + 3]);
    }
}
