package dev.mcdlss.core;

import java.util.Objects;

public final class StableCandidateGate<T> {
    private final int requiredObservations;
    private T candidate;
    private int observations;

    public StableCandidateGate(int requiredObservations) {
        if (requiredObservations < 1) {
            throw new IllegalArgumentException("requiredObservations must be positive");
        }
        this.requiredObservations = requiredObservations;
    }

    public boolean observe(T next) {
        if (!Objects.equals(candidate, next)) {
            candidate = next;
            observations = 1;
        } else if (observations < requiredObservations) {
            observations++;
        }
        return observations >= requiredObservations;
    }

    public void clear() {
        candidate = null;
        observations = 0;
    }
}
