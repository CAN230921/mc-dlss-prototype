package dev.mcdlss.fabric;

public final class MinecraftInteropTestPattern {
    public static byte[] createRgba8(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Pattern dimensions must be positive");
        }
        byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 4;
                pixels[offset] = (byte) (x * 3 + y);
                pixels[offset + 1] = (byte) (x + y * 5);
                pixels[offset + 2] = (byte) (x ^ y);
                pixels[offset + 3] = (byte) 0xff;
            }
        }
        return pixels;
    }

    private MinecraftInteropTestPattern() {
    }
}
