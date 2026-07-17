package dev.mcdlss.fabric;

public record LiveDlssResolutionContract(
        int renderWidth, int renderHeight, int outputWidth, int outputHeight) {
    public LiveDlssResolutionContract {
        if (renderWidth <= 0 || renderHeight <= 0 || outputWidth <= 0 || outputHeight <= 0
                || renderWidth >= outputWidth || renderHeight >= outputHeight
                || outputWidth > 16384 || outputHeight > 16384) {
            throw new IllegalArgumentException("DLSS dimensions are invalid");
        }
        long aspectError = Math.abs(
                (long) renderWidth * outputHeight - (long) outputWidth * renderHeight);
        if (aspectError > outputHeight) {
            throw new IllegalArgumentException("DLSS render/output aspects differ");
        }
    }

    public static LiveDlssResolutionContract create(
            int renderWidth, int renderHeight, int outputWidth, int outputHeight) {
        return new LiveDlssResolutionContract(
                renderWidth, renderHeight, outputWidth, outputHeight);
    }

    public int renderPixelCount() {
        return Math.multiplyExact(renderWidth, renderHeight);
    }

    public int outputPixelCount() {
        return Math.multiplyExact(outputWidth, outputHeight);
    }
}
