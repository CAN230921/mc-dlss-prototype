package dev.mcdlss.neoforge;

import dev.mcdlss.core.IrisTemporalFrameSequence;
import dev.mcdlss.core.NativeTemporalConstants;
import dev.mcdlss.core.TemporalCameraState;
import dev.mcdlss.core.TemporalDiscontinuity;
import net.minecraft.client.Camera;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class IrisTemporalCapture {
    private static final IrisTemporalFrameSequence SEQUENCE = new IrisTemporalFrameSequence();
    private static Matrix4f previousViewProjection;
    private static TemporalCameraState previousCameraState;

    public static Frame capture(
            RenderLevelStageEvent event,
            long generation,
            int renderWidth,
            int renderHeight) {
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        IrisTemporalFrameSequence.Frame identity = SEQUENCE.advance(
                generation, renderWidth, renderHeight);
        Camera camera = event.getCamera();
        var position = camera.getPosition();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        Vector3f look = camera.getLookVector();
        float near = Math.abs(projection.m32() / (projection.m22() - 1.0f));
        float far = Math.abs(projection.m32() / (projection.m22() + 1.0f));
        float fov = (float) (2.0 * Math.atan(1.0 / Math.abs(projection.m11())));
        float aspect = (float) renderWidth / renderHeight;
        TemporalCameraState cameraState = new TemporalCameraState(
                (float) position.x, (float) position.y, (float) position.z,
                look.x, look.y, look.z, fov, aspect);
        boolean reset = identity.reset() || previousViewProjection == null
                || TemporalDiscontinuity.requiresReset(previousCameraState, cameraState);

        Matrix4f clipToPrevClip = new Matrix4f();
        Matrix4f prevClipToClip = new Matrix4f();
        if (!reset && invertible(viewProjection) && invertible(previousViewProjection)) {
            clipToPrevClip.set(previousViewProjection)
                    .mul(new Matrix4f(viewProjection).invert());
            prevClipToClip.set(viewProjection)
                    .mul(new Matrix4f(previousViewProjection).invert());
        } else {
            reset = true;
        }

        NativeTemporalConstants constants = new NativeTemporalConstants(
                projection.get(new float[16]),
                new Matrix4f(projection).invert().get(new float[16]),
                clipToPrevClip.get(new float[16]),
                prevClipToClip.get(new float[16]),
                new float[] {(float) position.x, (float) position.y, (float) position.z},
                new float[] {up.x, up.y, up.z},
                new float[] {-left.x, -left.y, -left.z},
                new float[] {look.x, look.y, look.z},
                near, far, fov, aspect,
                0.0f, 0.0f, 1.0f / renderWidth, 1.0f / renderHeight, reset);
        previousViewProjection = viewProjection;
        previousCameraState = cameraState;
        return new Frame(identity.index(), clipToPrevClip.get(new float[16]), constants);
    }

    private static boolean invertible(Matrix4f matrix) {
        float determinant = matrix.determinant();
        return Float.isFinite(determinant) && Math.abs(determinant) > 0.000001f;
    }

    public record Frame(
            long index,
            float[] clipToPrevClip,
            NativeTemporalConstants constants) {
        public Frame {
            clipToPrevClip = clipToPrevClip.clone();
        }

        @Override
        public float[] clipToPrevClip() {
            return clipToPrevClip.clone();
        }
    }

    private IrisTemporalCapture() {
    }
}
