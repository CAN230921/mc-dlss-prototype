package dev.mcdlss.fabric;

import dev.mcdlss.core.NativeUpscalerExecutionMode;

public final class LiveUpscalerModeTracker {
    private static final int VALIDATION_TARGET = 30;
    private static final int FAST_SAMPLE_INTERVAL = 300;
    private static final int BUSY_FAILURE_LIMIT = 8;

    private NativeUpscalerExecutionMode executionMode =
            NativeUpscalerExecutionMode.VALIDATING;
    private int validatedFrames;
    private int fastFrames;
    private int periodicValidationCount;
    private int busyFallbackFrames;
    private int consecutiveBusyFrames;
    private boolean periodicValidationPending;
    private boolean failed;
    private boolean resetRequired;

    public void recordValidatedFrame() {
        requireActive();
        if (executionMode != NativeUpscalerExecutionMode.VALIDATING) {
            throw new IllegalStateException("Validated frame is not currently requested");
        }
        if (periodicValidationPending) {
            periodicValidationCount++;
            periodicValidationPending = false;
            executionMode = NativeUpscalerExecutionMode.FAST;
        } else {
            validatedFrames++;
            if (validatedFrames >= VALIDATION_TARGET) {
                executionMode = NativeUpscalerExecutionMode.FAST;
            }
        }
        consecutiveBusyFrames = 0;
    }

    public void recordFastFrame() {
        requireActive();
        if (executionMode != NativeUpscalerExecutionMode.FAST
                || periodicValidationPending) {
            throw new IllegalStateException("Fast frame is not currently allowed");
        }
        fastFrames++;
        consecutiveBusyFrames = 0;
        if (fastFrames % FAST_SAMPLE_INTERVAL == 0) {
            periodicValidationPending = true;
            executionMode = NativeUpscalerExecutionMode.VALIDATING;
        }
    }

    public void recordBusyFallback() {
        requireActive();
        busyFallbackFrames++;
        consecutiveBusyFrames++;
        failed = consecutiveBusyFrames >= BUSY_FAILURE_LIMIT;
    }

    public void resetForResize() {
        executionMode = NativeUpscalerExecutionMode.VALIDATING;
        validatedFrames = 0;
        fastFrames = 0;
        periodicValidationCount = 0;
        busyFallbackFrames = 0;
        consecutiveBusyFrames = 0;
        periodicValidationPending = false;
        failed = false;
        resetRequired = true;
    }

    public boolean consumeResetRequired() {
        boolean result = resetRequired;
        resetRequired = false;
        return result;
    }

    public NativeUpscalerExecutionMode executionMode() { return executionMode; }
    public int validatedFrames() { return validatedFrames; }
    public int fastFrames() { return fastFrames; }
    public int periodicValidationCount() { return periodicValidationCount; }
    public int busyFallbackFrames() { return busyFallbackFrames; }
    public int consecutiveBusyFrames() { return consecutiveBusyFrames; }
    public boolean periodicValidationPending() { return periodicValidationPending; }
    public boolean failed() { return failed; }
    public boolean resetRequired() { return resetRequired; }

    public boolean dlssReady() {
        return !failed && validatedFrames >= VALIDATION_TARGET
                && fastFrames >= FAST_SAMPLE_INTERVAL
                && periodicValidationCount > 0;
    }

    private void requireActive() {
        if (failed) throw new IllegalStateException("Upscaler scheduling has failed");
    }
}
