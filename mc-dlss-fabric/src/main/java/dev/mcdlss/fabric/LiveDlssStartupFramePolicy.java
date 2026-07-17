package dev.mcdlss.fabric;

public final class LiveDlssStartupFramePolicy {
    private static final int MAX_TEMPORAL_RETRIES = 8;

    public static boolean shouldRetry(
            String stage, long successfulEvaluations, int priorTemporalFailures) {
        return "TEMPORAL".equals(stage)
                && successfulEvaluations == 0
                && priorTemporalFailures >= 0
                && priorTemporalFailures < MAX_TEMPORAL_RETRIES;
    }

    private LiveDlssStartupFramePolicy() {
    }
}
