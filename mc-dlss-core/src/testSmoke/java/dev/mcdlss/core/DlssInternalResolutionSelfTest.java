package dev.mcdlss.core;

public final class DlssInternalResolutionSelfTest {
    public static void runAll() {
        requireSize(DlssInternalResolution.forOutput(1920, 1080, DlssQualityMode.QUALITY), 1280, 720);
        requireSize(DlssInternalResolution.forOutput(1920, 1080, DlssQualityMode.BALANCED), 1114, 626);
        requireSize(DlssInternalResolution.forOutput(1920, 1080, DlssQualityMode.PERFORMANCE), 960, 540);
        requireSize(DlssInternalResolution.forOutput(1920, 1080, DlssQualityMode.ULTRA_PERFORMANCE), 640, 360);
        requireSize(DlssInternalResolution.forOutput(1920, 1080, DlssQualityMode.DLAA), 1920, 1080);
    }

    private static void requireSize(DlssInternalResolution size, int width, int height) {
        if (size.width() != width || size.height() != height) {
            throw new AssertionError("Unexpected internal resolution: " + size);
        }
    }

    private DlssInternalResolutionSelfTest() {
    }
}
