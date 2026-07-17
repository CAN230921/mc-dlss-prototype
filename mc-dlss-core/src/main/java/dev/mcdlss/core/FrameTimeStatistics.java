package dev.mcdlss.core;

import java.util.Arrays;

public final class FrameTimeStatistics {
    private final double[] frameTimesMs;
    private int count;
    private int cursor;
    private long previousNanos;

    public FrameTimeStatistics(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("Capacity must be at least two");
        frameTimesMs = new double[capacity];
    }

    public synchronized void record(long nowNanos) {
        if (previousNanos != 0 && nowNanos > previousNanos) {
            double elapsedMs = (nowNanos - previousNanos) / 1_000_000.0;
            if (elapsedMs <= 250.0) {
                frameTimesMs[cursor] = elapsedMs;
                cursor = (cursor + 1) % frameTimesMs.length;
                if (count < frameTimesMs.length) count++;
            }
        }
        previousNanos = nowNanos;
    }

    public synchronized Snapshot snapshot() {
        if (count == 0) return new Snapshot(0, 0, 0, 0, 0);
        double[] sorted = Arrays.copyOf(frameTimesMs, count);
        Arrays.sort(sorted);
        double total = 0;
        for (double value : sorted) total += value;
        double averageMs = total / count;
        double p95Ms = percentile(sorted, 0.95);
        double p99Ms = percentile(sorted, 0.99);
        return new Snapshot(count, 1000.0 / averageMs, 1000.0 / p99Ms,
                averageMs, p95Ms);
    }

    private static double percentile(double[] sorted, double percentile) {
        int index = Math.max(0, (int) Math.floor(sorted.length * percentile));
        return sorted[Math.min(index, sorted.length - 1)];
    }

    public record Snapshot(int samples, double averageFps, double onePercentLowFps,
                           double averageFrameMs, double p95FrameMs) {
        public String compact() {
            if (samples < 30) return "性能统计采集中 " + samples + "/30";
            return String.format(java.util.Locale.ROOT,
                    "平均 %.1f FPS | 1%% Low %.1f | %.2f ms | P95 %.2f ms",
                    averageFps, onePercentLowFps, averageFrameMs, p95FrameMs);
        }
    }
}
