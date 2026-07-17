package dev.mcdlss.core;

public final class NativeLiveDlssContractSelfTest {
    public static void main(String[] args) {
        NativeDlssOptimalSettings settings =
                NativeDlssOptimalSettings.available(1280, 720, 1920, 1080);
        if (!settings.available() || settings.renderWidth() != 1280) {
            throw new AssertionError("Optimal settings mismatch");
        }
        if (NativeDlssOptimalSettings.available(1920, 1080, 1920, 1080).available()) {
            throw new AssertionError("Non-upscaling settings were accepted");
        }

        long[] handles = {11, 12};
        NativeLiveDlssSessionInfo session = NativeLiveDlssSessionInfo.available(
                7, 1280, 720, 1920, 1080,
                handles, handles, handles, handles, handles);
        handles[0] = 0;
        long[] exposed = session.colorTextureHandles();
        exposed[0] = 0;
        if (!session.available() || session.colorTextureHandles()[0] != 11) {
            throw new AssertionError("Live DLSS handles are not immutable");
        }

        NativeLiveDlssFrameResult frame = NativeLiveDlssFrameResult.ready(
                9, true, 100, 2, 2, 14.5, "evaluated");
        if (!frame.available() || !frame.completed()
                || !frame.diagnosticReadbackRequested() || !frame.outputNonUniform()
                || frame.outputNonBlackPixelCount() != 100) {
            throw new AssertionError("Live DLSS frame result mismatch");
        }
        NativeLiveDlssFrameResult fast = NativeLiveDlssFrameResult.fastCompleted(
                4, 4, 1.25, "fast");
        if (!fast.completed() || fast.available()
                || fast.diagnosticReadbackRequested()) {
            throw new AssertionError("Fast frame result mismatch");
        }
        if (NativeLiveDlssFrameResult.failure(
                "EVALUATE", "injected", 1, 0).available()) {
            throw new AssertionError("Failed frame was accepted");
        }
        System.out.println("NativeLiveDlssContractSelfTest passed");
    }

    private NativeLiveDlssContractSelfTest() {
    }
}
