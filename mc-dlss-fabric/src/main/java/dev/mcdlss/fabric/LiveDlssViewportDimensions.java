package dev.mcdlss.fabric;

public final class LiveDlssViewportDimensions {
    public static int[] select(
            int windowWidth, int windowHeight,
            int framebufferWidth, int framebufferHeight,
            boolean redirected) {
        if (windowWidth <= 0 || windowHeight <= 0
                || framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        return redirected
                ? new int[] {framebufferWidth, framebufferHeight}
                : new int[] {windowWidth, windowHeight};
    }

    private LiveDlssViewportDimensions() {
    }
}
