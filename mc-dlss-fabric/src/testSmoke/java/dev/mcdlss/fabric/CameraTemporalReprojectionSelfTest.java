package dev.mcdlss.fabric;

public final class CameraTemporalReprojectionSelfTest {
    private static final float EPSILON = 0.0001f;

    public static void main(String[] args) {
        identityProducesZeroMotion();
        translationProducesCurrentToPreviousPixelMotion();
        resetConditionsAreExplicit();
        sourceMatricesAreDefensivelyCopied();
        invalidDepthReturnsZeroMotion();
        System.out.println("CameraTemporalReprojectionSelfTest passed");
    }

    private static void identityProducesZeroMotion() {
        CameraTemporalFrame previous = frame(identity(), 100, 50, 1L);
        CameraTemporalFrame current = frame(identity(), 100, 50, 2L);
        CameraTemporalReprojection reprojection =
                CameraTemporalReprojection.between(previous, current);
        assertFalse(reprojection.reset(), "identity frame should be continuous");
        float[] motion = reprojection.motionPixels(50.0f, 25.0f, 0.5f);
        assertNear(0.0f, motion[0], "identity x motion");
        assertNear(0.0f, motion[1], "identity y motion");
    }

    private static void translationProducesCurrentToPreviousPixelMotion() {
        CameraTemporalFrame previous = frame(identity(), 100, 50, 10L);
        float[] currentViewProjection = identity();
        currentViewProjection[12] = -0.2f;
        CameraTemporalFrame current = frame(currentViewProjection, 100, 50, 11L);
        CameraTemporalReprojection reprojection =
                CameraTemporalReprojection.between(previous, current);
        float[] motion = reprojection.motionPixels(50.0f, 25.0f, 0.5f);
        assertNear(-10.0f, motion[0], "current-to-previous x sign");
        assertNear(0.0f, motion[1], "translation y motion");
    }

    private static void resetConditionsAreExplicit() {
        CameraTemporalFrame frame = frame(identity(), 100, 50, 20L);
        assertTrue(CameraTemporalReprojection.between(null, frame).reset(),
                "missing previous frame must reset");
        assertTrue(CameraTemporalReprojection.between(
                frame, frame(identity(), 101, 50, 21L)).reset(),
                "resize must reset");
        assertTrue(CameraTemporalReprojection.between(
                frame, frame(identity(), 100, 50, 22L)).reset(),
                "nonconsecutive frame must reset");

        float[] singular = new float[16];
        assertTrue(CameraTemporalReprojection.between(
                frame, frame(singular, 100, 50, 21L)).reset(),
                "singular matrix must reset");
    }

    private static void sourceMatricesAreDefensivelyCopied() {
        float[] source = identity();
        CameraTemporalFrame frame = frame(source, 100, 50, 30L);
        source[0] = 7.0f;
        float[] exposed = frame.viewProjection();
        exposed[0] = 9.0f;
        assertNear(1.0f, frame.viewProjection()[0], "matrix must be immutable");
    }

    private static void invalidDepthReturnsZeroMotion() {
        CameraTemporalFrame previous = frame(identity(), 100, 50, 40L);
        CameraTemporalFrame current = frame(identity(), 100, 50, 41L);
        CameraTemporalReprojection reprojection =
                CameraTemporalReprojection.between(previous, current);
        for (float depth : new float[] {Float.NaN, -0.1f, 1.0f, 1.1f}) {
            float[] motion = reprojection.motionPixels(25.0f, 10.0f, depth);
            assertNear(0.0f, motion[0], "invalid depth x motion");
            assertNear(0.0f, motion[1], "invalid depth y motion");
        }
    }

    private static CameraTemporalFrame frame(
            float[] viewProjection, int width, int height, long frameIndex) {
        return new CameraTemporalFrame(
                viewProjection, identity(), identity(), width, height, frameIndex);
    }

    private static float[] identity() {
        return new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
    }

    private static void assertNear(float expected, float actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private CameraTemporalReprojectionSelfTest() {
    }
}
