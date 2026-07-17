package dev.mcdlss.fabric;

import java.util.Arrays;

public final class CameraTemporalFrame {
    private static final int MATRIX_ELEMENTS = 16;

    private final float[] viewProjection;
    private final float[] projection;
    private final float[] view;
    private final int width;
    private final int height;
    private final long frameIndex;

    public CameraTemporalFrame(
            float[] viewProjection,
            float[] projection,
            float[] view,
            int width,
            int height,
            long frameIndex) {
        this.viewProjection = copyMatrix(viewProjection, "viewProjection");
        this.projection = copyMatrix(projection, "projection");
        this.view = copyMatrix(view, "view");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.frameIndex = frameIndex;
    }

    public float[] viewProjection() {
        return viewProjection.clone();
    }

    public float[] projection() {
        return projection.clone();
    }

    public float[] view() {
        return view.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long frameIndex() {
        return frameIndex;
    }

    private static float[] copyMatrix(float[] source, String label) {
        if (source == null || source.length != MATRIX_ELEMENTS) {
            throw new IllegalArgumentException(label + " must contain 16 elements");
        }
        float[] copy = Arrays.copyOf(source, source.length);
        for (float value : copy) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(label + " must be finite");
            }
        }
        return copy;
    }
}
