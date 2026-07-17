package dev.mcdlss.core;

public record NativeDlssOptimalSettings(
        boolean available,
        int renderWidth,
        int renderHeight,
        int outputWidth,
        int outputHeight,
        String message) {
    public NativeDlssOptimalSettings {
        message = message == null ? "" : message.trim();
    }

    public static NativeDlssOptimalSettings available(
            int renderWidth, int renderHeight, int outputWidth, int outputHeight) {
        if (renderWidth <= 0 || renderHeight <= 0 || outputWidth <= 0 || outputHeight <= 0
                || renderWidth >= outputWidth || renderHeight >= outputHeight
                || outputWidth > 16384 || outputHeight > 16384) {
            return unavailable("Invalid DLSS optimal settings");
        }
        return new NativeDlssOptimalSettings(
                true, renderWidth, renderHeight, outputWidth, outputHeight,
                "DLSS Quality optimal settings ready");
    }

    public static NativeDlssOptimalSettings unavailable(String message) {
        return new NativeDlssOptimalSettings(false, 0, 0, 0, 0, message);
    }
}
