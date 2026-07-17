package dev.mcdlss.fabric;

public record LiveDlssPresentationSnapshot(
        LiveDlssPresentationState state,
        int renderWidth,
        int renderHeight,
        int outputWidth,
        int outputHeight,
        int successfulFrames,
        int targetFrames,
        int retainedFrames,
        int targetRetainedFrames,
        int slotAUses,
        int slotBUses,
        int resizeCount,
        int outputHashChanges,
        String openGlOutputHash,
        String d3d12OutputHash,
        int finiteChannelCount,
        int nonBlackPixelCount,
        boolean outputNonUniform,
        int resetFrameCount,
        int fallbackFrameCount,
        boolean originalFramebufferRestored,
        boolean resourcesRetained,
        boolean resourcesReleased,
        String failureStage,
        double averageMilliseconds,
        double maximumMilliseconds,
        String message) {
    private static final int INITIAL_GATE_FRAMES = 30;

    public LiveDlssPresentationSnapshot {
        state = state == null ? LiveDlssPresentationState.DISABLED : state;
        openGlOutputHash = safe(openGlOutputHash);
        d3d12OutputHash = safe(d3d12OutputHash);
        failureStage = safe(failureStage);
        message = safe(message);
    }

    public boolean outputHashesMatch() {
        return !openGlOutputHash.isEmpty()
                && openGlOutputHash.equals(d3d12OutputHash);
    }

    public boolean initialGatePassed() {
        return successfulFrames >= INITIAL_GATE_FRAMES
                && validDimensions()
                && validOutput();
    }

    public boolean dlssReady() {
        int totalPresentedFrames = targetFrames + targetRetainedFrames + 1;
        return state == LiveDlssPresentationState.COMPLETE
                && targetFrames >= INITIAL_GATE_FRAMES
                && successfulFrames == targetFrames
                && retainedFrames >= targetRetainedFrames
                && targetRetainedFrames > 0
                && slotAUses + slotBUses >= totalPresentedFrames
                && Math.abs(slotAUses - slotBUses) <= 1
                && outputHashChanges > 0
                && resetFrameCount > 0
                && resourcesRetained
                && originalFramebufferRestored
                && validDimensions()
                && validOutput();
    }

    public boolean fallbackRecovered() {
        return state == LiveDlssPresentationState.FAILED
                && fallbackFrameCount > 0
                && originalFramebufferRestored
                && resourcesReleased;
    }

    private boolean validDimensions() {
        return renderWidth > 0 && renderHeight > 0
                && outputWidth > renderWidth && outputHeight > renderHeight;
    }

    private boolean validOutput() {
        long pixels = (long) outputWidth * outputHeight;
        return outputHashesMatch()
                && outputNonUniform
                && finiteChannelCount == pixels * 4L
                && nonBlackPixelCount > 0
                && nonBlackPixelCount <= pixels;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
