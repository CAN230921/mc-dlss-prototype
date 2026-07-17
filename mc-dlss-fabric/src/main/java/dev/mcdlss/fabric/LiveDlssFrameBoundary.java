package dev.mcdlss.fabric;

public final class LiveDlssFrameBoundary<T> {
    private boolean active;
    private T captured;

    public void begin() {
        active = true;
        captured = null;
    }

    public void captureAtWorldEnd(T value) {
        if (!active || value == null) {
            throw new IllegalStateException("World frame boundary is not active");
        }
        captured = value;
    }

    public T consumeAfterRenderWorld() {
        T result = captured;
        captured = null;
        active = false;
        return result;
    }

    public boolean active() {
        return active;
    }

    public T peek() {
        return captured;
    }
}
