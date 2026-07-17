package dev.mcdlss.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MotionFrameFingerprintSelfTest {
    public static void main(String[] args) {
        ByteBuffer bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        put(bytes, 0x0000, 0x0000);
        put(bytes, 0x3c00, 0xc000);
        put(bytes, 0x4200, 0x0000);
        put(bytes, 0x7c00, 0x7e00);
        MotionFrameFingerprint result = MotionFrameFingerprint.fromRg16f(
                bytes.array(), 4.0f, 3.0f);
        if (result.finiteVectorCount() != 3 || result.nonZeroVectorCount() != 2
                || result.outOfBoundsVectorCount() != 0
                || result.minimumX() != 0.0f || result.maximumX() != 3.0f
                || result.minimumY() != -2.0f || result.maximumY() != 0.0f
                || Math.abs(result.maximumMagnitude() - 3.0f) > 0.001f) {
            throw new AssertionError("Motion fingerprint statistics mismatch");
        }
        ByteBuffer bounded = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        put(bounded, 0x4500, 0x0000);
        if (MotionFrameFingerprint.fromRg16f(bounded.array(), 4, 3)
                .outOfBoundsVectorCount() != 1) {
            throw new AssertionError("Out-of-bounds motion was accepted");
        }
        expectFailure(() -> MotionFrameFingerprint.fromRg16f(new byte[3], 4, 3));
        System.out.println("MotionFrameFingerprintSelfTest passed");
    }

    private static void put(ByteBuffer target, int x, int y) {
        target.putShort((short) x).putShort((short) y);
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid motion bytes were accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private MotionFrameFingerprintSelfTest() {
    }
}
