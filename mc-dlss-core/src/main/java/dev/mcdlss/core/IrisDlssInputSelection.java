package dev.mcdlss.core;

public record IrisDlssInputSelection(
        int colorTexture,
        int depthTexture,
        int width,
        int height,
        long generation) {
    public IrisDlssInputSelection {
        if (colorTexture <= 0 || depthTexture <= 0 || width <= 0 || height <= 0 || generation < 0) {
            throw new IllegalArgumentException("Iris DLSS input selection is invalid");
        }
    }

    public static IrisDlssInputSelection select(
            IrisRenderTargetSnapshot snapshot,
            boolean colorZeroFlipped,
            boolean afterFinalPass) {
        if (!snapshot.targetsUsable()) {
            throw new IllegalStateException("Iris render targets are not ready");
        }
        int colorTexture = colorZeroFlipped && !afterFinalPass
                ? snapshot.colorAltTexture()
                : snapshot.colorMainTexture();
        return new IrisDlssInputSelection(
                colorTexture,
                snapshot.depthTexture(),
                snapshot.width(),
                snapshot.height(),
                snapshot.generation());
    }
}
