package dev.mcdlss.core;

import java.util.Objects;

public final class DlssRetryGate<K> {
    private final long cooldownMillis;
    private K failedKey;
    private long retryAtMillis;

    public DlssRetryGate(long cooldownMillis) {
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis must be non-negative");
        this.cooldownMillis = cooldownMillis;
    }

    public boolean canAttempt(K key, long nowMillis) {
        return !Objects.equals(key, failedKey) || nowMillis >= retryAtMillis;
    }

    public void recordFailure(K key, long nowMillis) {
        failedKey = key;
        retryAtMillis = nowMillis + cooldownMillis;
    }

    public void clear() {
        failedKey = null;
        retryAtMillis = 0;
    }
}
