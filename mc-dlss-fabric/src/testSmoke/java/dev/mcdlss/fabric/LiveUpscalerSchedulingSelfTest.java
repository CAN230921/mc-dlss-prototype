package dev.mcdlss.fabric;

import dev.mcdlss.core.NativeUpscalerBackend;
import dev.mcdlss.core.NativeUpscalerExecutionMode;

public final class LiveUpscalerSchedulingSelfTest {
    public static void main(String[] args) {
        testVendorNeutralIdentifiers();
        testSlotSelection();
        testModeTransitions();
        testBusyFailureAndResizeReset();
        System.out.println("LiveUpscalerSchedulingSelfTest passed");
    }

    private static void testVendorNeutralIdentifiers() {
        if (NativeUpscalerBackend.STREAMLINE_DLSS.code() != 0
                || NativeUpscalerBackend.INTEL_XESS.code() != 1
                || NativeUpscalerBackend.AMD_FSR3.code() != 2
                || NativeUpscalerExecutionMode.VALIDATING.code() != 0
                || NativeUpscalerExecutionMode.FAST.code() != 1) {
            throw new AssertionError("Upscaler identifiers changed");
        }
    }

    private static void testSlotSelection() {
        if (LiveUpscalerSlotScheduler.select(true, true, 0) != 0
                || LiveUpscalerSlotScheduler.select(true, true, 1) != 1
                || LiveUpscalerSlotScheduler.select(false, true, 0) != 1
                || LiveUpscalerSlotScheduler.select(true, false, 1) != 0
                || LiveUpscalerSlotScheduler.select(false, false, 0) != -1) {
            throw new AssertionError("A/B slot selection mismatch");
        }
        expectFailure(() -> LiveUpscalerSlotScheduler.select(true, true, 2));
    }

    private static void testModeTransitions() {
        LiveUpscalerModeTracker tracker = new LiveUpscalerModeTracker();
        for (int frame = 0; frame < 29; frame++) tracker.recordValidatedFrame();
        if (tracker.executionMode() != NativeUpscalerExecutionMode.VALIDATING
                || tracker.validatedFrames() != 29) {
            throw new AssertionError("Validation mode ended early");
        }
        tracker.recordValidatedFrame();
        if (tracker.executionMode() != NativeUpscalerExecutionMode.FAST
                || tracker.validatedFrames() != 30) {
            throw new AssertionError("Validation gate did not enter fast mode");
        }
        for (int frame = 0; frame < 300; frame++) tracker.recordFastFrame();
        if (tracker.executionMode() != NativeUpscalerExecutionMode.VALIDATING
                || !tracker.periodicValidationPending() || tracker.dlssReady()) {
            throw new AssertionError("Periodic validation was not requested");
        }
        tracker.recordValidatedFrame();
        if (!tracker.dlssReady() || tracker.periodicValidationCount() != 1
                || tracker.executionMode() != NativeUpscalerExecutionMode.FAST) {
            throw new AssertionError("Fast readiness gate mismatch");
        }
    }

    private static void testBusyFailureAndResizeReset() {
        LiveUpscalerModeTracker tracker = new LiveUpscalerModeTracker();
        for (int frame = 0; frame < 7; frame++) tracker.recordBusyFallback();
        if (tracker.failed() || tracker.busyFallbackFrames() != 7) {
            throw new AssertionError("Busy fallback failed too early");
        }
        tracker.recordBusyFallback();
        if (!tracker.failed() || tracker.consecutiveBusyFrames() != 8) {
            throw new AssertionError("Eight busy frames did not fail");
        }
        tracker.resetForResize();
        if (tracker.failed() || tracker.validatedFrames() != 0
                || tracker.fastFrames() != 0 || !tracker.resetRequired()
                || tracker.executionMode() != NativeUpscalerExecutionMode.VALIDATING) {
            throw new AssertionError("Resize did not reset scheduling state");
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid input was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private LiveUpscalerSchedulingSelfTest() {
    }
}
