package dev.mcdlss.fabric;

public final class CameraTemporalReprojection {
    private static final float DIVIDE_EPSILON = 0.000001f;
    private static final float FAR_DEPTH = 0.9999f;

    private final boolean reset;
    private final int width;
    private final int height;
    private final float[] clipToPrevClip;
    private final float[] prevClipToClip;

    private CameraTemporalReprojection(
            boolean reset,
            int width,
            int height,
            float[] clipToPrevClip,
            float[] prevClipToClip) {
        this.reset = reset;
        this.width = width;
        this.height = height;
        this.clipToPrevClip = clipToPrevClip;
        this.prevClipToClip = prevClipToClip;
    }

    public static CameraTemporalReprojection between(
            CameraTemporalFrame previous, CameraTemporalFrame current) {
        if (current == null) {
            throw new IllegalArgumentException("Current frame is required");
        }
        float[] identity = identity();
        if (previous == null
                || previous.width() != current.width()
                || previous.height() != current.height()
                || previous.frameIndex() + 1L != current.frameIndex()) {
            return new CameraTemporalReprojection(
                    true, current.width(), current.height(), identity, identity.clone());
        }

        float[] inverseCurrent = invert(current.viewProjection());
        float[] inversePrevious = invert(previous.viewProjection());
        if (inverseCurrent == null || inversePrevious == null) {
            return new CameraTemporalReprojection(
                    true, current.width(), current.height(), identity, identity.clone());
        }
        return new CameraTemporalReprojection(
                false,
                current.width(),
                current.height(),
                multiply(previous.viewProjection(), inverseCurrent),
                multiply(current.viewProjection(), inversePrevious));
    }

    public boolean reset() {
        return reset;
    }

    public float[] clipToPrevClip() {
        return clipToPrevClip.clone();
    }

    public float[] prevClipToClip() {
        return prevClipToClip.clone();
    }

    public float[] motionPixels(float pixelX, float pixelY, float depth) {
        if (reset || !Float.isFinite(depth) || depth < 0.0f || depth >= FAR_DEPTH) {
            return new float[] {0.0f, 0.0f};
        }
        float currentNdcX = pixelX * 2.0f / width - 1.0f;
        float currentNdcY = pixelY * 2.0f / height - 1.0f;
        float[] previousClip = transform(
                clipToPrevClip,
                currentNdcX,
                currentNdcY,
                depth * 2.0f - 1.0f,
                1.0f);
        if (!allFinite(previousClip) || Math.abs(previousClip[3]) <= DIVIDE_EPSILON
                || previousClip[3] <= 0.0f) {
            return new float[] {0.0f, 0.0f};
        }
        float previousNdcX = previousClip[0] / previousClip[3];
        float previousNdcY = previousClip[1] / previousClip[3];
        float motionX = (currentNdcX - previousNdcX) * 0.5f * width;
        float motionY = (currentNdcY - previousNdcY) * 0.5f * height;
        return new float[] {
            clamp(motionX, -width, width),
            clamp(motionY, -height, height)
        };
    }

    private static float[] multiply(float[] left, float[] right) {
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float value = 0.0f;
                for (int k = 0; k < 4; k++) {
                    value += left[k * 4 + row] * right[column * 4 + k];
                }
                result[column * 4 + row] = value;
            }
        }
        return result;
    }

    private static float[] transform(float[] matrix, float x, float y, float z, float w) {
        return new float[] {
            matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12] * w,
            matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13] * w,
            matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14] * w,
            matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15] * w
        };
    }

    private static float[] invert(float[] matrix) {
        double[][] augmented = new double[4][8];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                augmented[row][column] = matrix[column * 4 + row];
            }
            augmented[row][row + 4] = 1.0;
        }
        for (int pivot = 0; pivot < 4; pivot++) {
            int bestRow = pivot;
            for (int row = pivot + 1; row < 4; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[bestRow][pivot])) {
                    bestRow = row;
                }
            }
            if (Math.abs(augmented[bestRow][pivot]) <= DIVIDE_EPSILON) {
                return null;
            }
            double[] swap = augmented[pivot];
            augmented[pivot] = augmented[bestRow];
            augmented[bestRow] = swap;
            double divisor = augmented[pivot][pivot];
            for (int column = 0; column < 8; column++) {
                augmented[pivot][column] /= divisor;
            }
            for (int row = 0; row < 4; row++) {
                if (row == pivot) {
                    continue;
                }
                double factor = augmented[row][pivot];
                for (int column = 0; column < 8; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }
        float[] inverse = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                inverse[column * 4 + row] = (float) augmented[row][column + 4];
            }
        }
        return allFinite(inverse) ? inverse : null;
    }

    private static boolean allFinite(float[] values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float[] identity() {
        return new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
    }
}
