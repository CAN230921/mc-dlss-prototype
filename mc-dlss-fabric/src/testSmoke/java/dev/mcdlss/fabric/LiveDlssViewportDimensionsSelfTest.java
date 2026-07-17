package dev.mcdlss.fabric;

public final class LiveDlssViewportDimensionsSelfTest {
    public static void main(String[] args) {
        int[] redirected = LiveDlssViewportDimensions.select(
                854, 480, 569, 320, true);
        if (redirected[0] != 569 || redirected[1] != 320) {
            throw new AssertionError("Redirected viewport must match the framebuffer");
        }
        int[] vanilla = LiveDlssViewportDimensions.select(
                854, 480, 569, 320, false);
        if (vanilla[0] != 854 || vanilla[1] != 480) {
            throw new AssertionError("Vanilla viewport must match the window");
        }
        System.out.println("LiveDlssViewportDimensionsSelfTest passed");
    }

    private LiveDlssViewportDimensionsSelfTest() {
    }
}
