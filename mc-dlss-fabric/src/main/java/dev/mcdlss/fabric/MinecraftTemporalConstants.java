package dev.mcdlss.fabric;

import dev.mcdlss.core.NativeTemporalConstants;

public final class MinecraftTemporalConstants {
    public static NativeTemporalConstants create(
            CameraTemporalFrame current,
            CameraTemporalReprojection reprojection,
            float[] clipToCameraView,
            float[] cameraPosition,
            float[] cameraUp,
            float[] cameraRight,
            float[] cameraForward,
            float cameraNear,
            float cameraFar,
            float cameraFov) {
        if (current == null || reprojection == null) {
            throw new IllegalArgumentException("Current frame and reprojection are required");
        }
        return new NativeTemporalConstants(
                current.projection(),
                clipToCameraView,
                reprojection.clipToPrevClip(),
                reprojection.prevClipToClip(),
                cameraPosition,
                cameraUp,
                cameraRight,
                cameraForward,
                cameraNear,
                cameraFar,
                cameraFov,
                (float) current.width() / current.height(),
                0.0f,
                0.0f,
                1.0f / current.width(),
                1.0f / current.height(),
                reprojection.reset());
    }

    private MinecraftTemporalConstants() {
    }
}
