package dev.mcdlss.fabric;

public final class LiveDlssStartupFramePolicySelfTest {
    public static void main(String[] args) {
        for (int failures = 0; failures < 8; failures++) {
            if (!LiveDlssStartupFramePolicy.shouldRetry(
                    "TEMPORAL", 0, failures)) {
                throw new AssertionError("Startup temporal frame should be retried");
            }
        }
        if (LiveDlssStartupFramePolicy.shouldRetry("TEMPORAL", 0, 8)
                || LiveDlssStartupFramePolicy.shouldRetry("TEMPORAL", 1, 0)
                || LiveDlssStartupFramePolicy.shouldRetry("EVALUATE", 0, 0)) {
            throw new AssertionError("Non-transient failure was retried");
        }
        System.out.println("LiveDlssStartupFramePolicySelfTest passed");
    }

    private LiveDlssStartupFramePolicySelfTest() {
    }
}
