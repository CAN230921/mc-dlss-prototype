package dev.mcdlss.core;

public final class NativeTemporalConstants {
    private final float[] cameraViewToClip;
    private final float[] clipToCameraView;
    private final float[] clipToPrevClip;
    private final float[] prevClipToClip;
    private final float[] cameraPos;
    private final float[] cameraUp;
    private final float[] cameraRight;
    private final float[] cameraFwd;
    private final float cameraNear;
    private final float cameraFar;
    private final float cameraFov;
    private final float cameraAspectRatio;
    private final float jitterX;
    private final float jitterY;
    private final float motionVectorScaleX;
    private final float motionVectorScaleY;
    private final boolean reset;

    public NativeTemporalConstants(
            float[] cameraViewToClip, float[] clipToCameraView,
            float[] clipToPrevClip, float[] prevClipToClip,
            float[] cameraPos, float[] cameraUp, float[] cameraRight, float[] cameraFwd,
            float cameraNear, float cameraFar, float cameraFov, float cameraAspectRatio,
            float jitterX, float jitterY,
            float motionVectorScaleX, float motionVectorScaleY, boolean reset) {
        this.cameraViewToClip = copy(cameraViewToClip, 16, "cameraViewToClip");
        this.clipToCameraView = copy(clipToCameraView, 16, "clipToCameraView");
        this.clipToPrevClip = copy(clipToPrevClip, 16, "clipToPrevClip");
        this.prevClipToClip = copy(prevClipToClip, 16, "prevClipToClip");
        this.cameraPos = copy(cameraPos, 3, "cameraPos");
        this.cameraUp = copy(cameraUp, 3, "cameraUp");
        this.cameraRight = copy(cameraRight, 3, "cameraRight");
        this.cameraFwd = copy(cameraFwd, 3, "cameraFwd");
        requirePositive(cameraNear, "cameraNear");
        requirePositive(cameraFar, "cameraFar");
        requirePositive(cameraFov, "cameraFov");
        requirePositive(cameraAspectRatio, "cameraAspectRatio");
        requireFinite(jitterX, "jitterX");
        requireFinite(jitterY, "jitterY");
        requirePositive(motionVectorScaleX, "motionVectorScaleX");
        requirePositive(motionVectorScaleY, "motionVectorScaleY");
        if (cameraFar <= cameraNear) {
            throw new IllegalArgumentException("cameraFar must exceed cameraNear");
        }
        this.cameraNear = cameraNear;
        this.cameraFar = cameraFar;
        this.cameraFov = cameraFov;
        this.cameraAspectRatio = cameraAspectRatio;
        this.jitterX = jitterX;
        this.jitterY = jitterY;
        this.motionVectorScaleX = motionVectorScaleX;
        this.motionVectorScaleY = motionVectorScaleY;
        this.reset = reset;
    }

    public float[] cameraViewToClip() { return cameraViewToClip.clone(); }
    public float[] clipToCameraView() { return clipToCameraView.clone(); }
    public float[] clipToPrevClip() { return clipToPrevClip.clone(); }
    public float[] prevClipToClip() { return prevClipToClip.clone(); }
    public float[] cameraPos() { return cameraPos.clone(); }
    public float[] cameraUp() { return cameraUp.clone(); }
    public float[] cameraRight() { return cameraRight.clone(); }
    public float[] cameraFwd() { return cameraFwd.clone(); }
    public float cameraNear() { return cameraNear; }
    public float cameraFar() { return cameraFar; }
    public float cameraFov() { return cameraFov; }
    public float cameraAspectRatio() { return cameraAspectRatio; }
    public float jitterX() { return jitterX; }
    public float jitterY() { return jitterY; }
    public float motionVectorScaleX() { return motionVectorScaleX; }
    public float motionVectorScaleY() { return motionVectorScaleY; }
    public boolean reset() { return reset; }
    public boolean depthInverted() { return false; }
    public boolean cameraMotionIncluded() { return true; }
    public boolean motionVectors3D() { return false; }
    public boolean motionVectorsJittered() { return false; }
    public boolean motionVectorsDilated() { return false; }

    public NativeTemporalConstants withReset(boolean reset) {
        return new NativeTemporalConstants(
                cameraViewToClip, clipToCameraView, clipToPrevClip, prevClipToClip,
                cameraPos, cameraUp, cameraRight, cameraFwd,
                cameraNear, cameraFar, cameraFov, cameraAspectRatio,
                jitterX, jitterY, motionVectorScaleX, motionVectorScaleY, reset);
    }

    private static float[] copy(float[] values, int length, String label) {
        if (values == null || values.length != length) {
            throw new IllegalArgumentException(label + " must contain " + length + " elements");
        }
        float[] copy = values.clone();
        for (float value : copy) {
            requireFinite(value, label);
        }
        return copy;
    }

    private static void requirePositive(float value, String label) {
        requireFinite(value, label);
        if (value <= 0.0f) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireFinite(float value, String label) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
