package dev.mcdlss.core;

public record DlssInternalResolution(int width, int height) {
    public DlssInternalResolution {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Resolution must be positive");
        }
    }

    public static DlssInternalResolution forOutput(
            int outputWidth, int outputHeight, DlssQualityMode qualityMode) {
        if (outputWidth <= 0 || outputHeight <= 0) {
            throw new IllegalArgumentException("Output resolution must be positive");
        }
        DlssQualityMode mode = qualityMode == null ? DlssQualityMode.QUALITY : qualityMode;
        double scale = switch (mode) {
            case BALANCED -> 0.58;
            case PERFORMANCE -> 0.5;
            case ULTRA_PERFORMANCE -> 1.0 / 3.0;
            case DLAA -> 1.0;
            case QUALITY -> 2.0 / 3.0;
        };
        return new DlssInternalResolution(
                Math.max(1, (int) Math.round(outputWidth * scale)),
                Math.max(1, (int) Math.round(outputHeight * scale)));
    }
}
