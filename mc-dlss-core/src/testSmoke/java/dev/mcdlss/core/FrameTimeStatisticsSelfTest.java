package dev.mcdlss.core;

public final class FrameTimeStatisticsSelfTest {
    public static void runAll() {
        FrameTimeStatistics statistics = new FrameTimeStatistics(100);
        long now = 1_000_000_000L;
        statistics.record(now);
        for (int index = 0; index < 100; index++) {
            now += index == 99 ? 40_000_000L : 10_000_000L;
            statistics.record(now);
        }
        FrameTimeStatistics.Snapshot snapshot = statistics.snapshot();
        if (snapshot.samples() != 100 || snapshot.averageFps() < 96.0
                || snapshot.averageFps() > 98.0 || snapshot.onePercentLowFps() != 25.0) {
            throw new AssertionError("Unexpected frame statistics: " + snapshot);
        }
    }

    private FrameTimeStatisticsSelfTest() {
    }
}
