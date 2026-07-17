package dev.mcdlss.fabric;

public final class LiveDlssResolutionContractSelfTest {
    public static void main(String[] args) {
        LiveDlssResolutionContract valid =
                LiveDlssResolutionContract.create(1280, 720, 1920, 1080);
        if (valid.renderPixelCount() != 921600 || valid.outputPixelCount() != 2073600) {
            throw new AssertionError("Resolution pixel counts mismatch");
        }
        expectFailure(() -> LiveDlssResolutionContract.create(1920, 1080, 1920, 1080));
        expectFailure(() -> LiveDlssResolutionContract.create(1280, 700, 1920, 1080));

        if (LiveDlssPresentationState.CAPTURING.dlssReady()
                || !LiveDlssPresentationState.COMPLETE.dlssReady()
                || !LiveDlssPresentationState.FAILED.usesVanillaFallback()) {
            throw new AssertionError("Presentation state flags mismatch");
        }
        System.out.println("LiveDlssResolutionContractSelfTest passed");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid resolution was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private LiveDlssResolutionContractSelfTest() {
    }
}
