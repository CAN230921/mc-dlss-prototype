package dev.mcdlss.core;

public record NativeLiveDlssFrameResult(
        boolean available,
        boolean completed,
        boolean diagnosticReadbackRequested,
        String stage,
        long outputHash,
        boolean outputNonUniform,
        int outputNonBlackPixelCount,
        long completedFenceValue,
        long lastSubmittedSignal,
        double evaluationMilliseconds,
        String message) {
    public NativeLiveDlssFrameResult {
        stage = stage == null ? "UNAVAILABLE" : stage.trim();
        message = message == null ? "" : message.trim();
    }

    public static NativeLiveDlssFrameResult ready(
            long outputHash, boolean outputNonUniform, int outputNonBlackPixelCount,
            long completedFenceValue, long lastSubmittedSignal,
            double evaluationMilliseconds, String message) {
        if (outputNonBlackPixelCount < 0 || completedFenceValue <= 0
                || lastSubmittedSignal <= 0 || !Double.isFinite(evaluationMilliseconds)
                || evaluationMilliseconds < 0.0) {
            return failure("VALIDATE", "Invalid live DLSS frame result",
                    completedFenceValue, lastSubmittedSignal);
        }
        return new NativeLiveDlssFrameResult(
                true, true, true, "READY", outputHash, outputNonUniform,
                outputNonBlackPixelCount, completedFenceValue,
                lastSubmittedSignal, evaluationMilliseconds, message);
    }

    public static NativeLiveDlssFrameResult fastCompleted(
            long completedFenceValue, long lastSubmittedSignal,
            double evaluationMilliseconds, String message) {
        if (completedFenceValue <= 0 || lastSubmittedSignal <= 0
                || !Double.isFinite(evaluationMilliseconds)
                || evaluationMilliseconds < 0.0) {
            return failure("VALIDATE", "Invalid fast upscaler frame result",
                    completedFenceValue, lastSubmittedSignal);
        }
        return new NativeLiveDlssFrameResult(
                false, true, false, "READY", 0, false, 0,
                completedFenceValue, lastSubmittedSignal,
                evaluationMilliseconds, message);
    }

    public static NativeLiveDlssFrameResult failure(
            String stage, String message, long completedFenceValue, long lastSubmittedSignal) {
        return new NativeLiveDlssFrameResult(
                false, false, false, stage, 0, false, 0, completedFenceValue,
                lastSubmittedSignal, 0.0, message);
    }
}
