package dev.mcdlss.core;

public final class NativePersistentMotionFrameSessionSelfTest {
    public static void main(String[] args) {
        NativePersistentMotionFrameSessionInfo info =
                NativePersistentMotionFrameSessionInfo.available(1, 2, 3, 4, 5, 854, 480);
        if (!info.available() || info.motionTextureHandle() != 4) {
            throw new AssertionError("Motion session info mismatch");
        }
        long[] values = new long[23];
        values[0] = 1;
        values[1] = 11; values[2] = 1; values[3] = 100;
        values[4] = 12; values[5] = 1;
        values[6] = 200; values[7] = 200; values[8] = 150; values[9] = 50;
        values[10] = Float.floatToRawIntBits(0.2f);
        values[11] = Float.floatToRawIntBits(1.0f);
        values[12] = 13; values[13] = 200; values[14] = 40; values[15] = 0;
        values[16] = Float.floatToRawIntBits(-3.0f);
        values[17] = Float.floatToRawIntBits(4.0f);
        values[18] = Float.floatToRawIntBits(-2.0f);
        values[19] = Float.floatToRawIntBits(1.0f);
        values[20] = Float.floatToRawIntBits(4.5f);
        values[21] = 2; values[22] = 2;
        NativeMotionFrameReadbackFingerprint decoded =
                NativeLibraryBridge.decodePersistentMotionFrameFingerprint(values, 2, 200);
        if (!decoded.available() || decoded.motionHash() != 13
                || decoded.minimumX() != -3.0f || decoded.maximumMagnitude() != 4.5f) {
            throw new AssertionError("Motion fingerprint decode mismatch");
        }
        if (NativeLibraryBridge.decodePersistentMotionFrameFingerprint(
                new long[22], 2, 200).available()) {
            throw new AssertionError("Malformed motion fingerprint was accepted");
        }
        values[13] = 201;
        if (NativeLibraryBridge.decodePersistentMotionFrameFingerprint(
                values, 2, 200).available()) {
            throw new AssertionError("Oversized motion count was accepted");
        }
        System.out.println("NativePersistentMotionFrameSessionSelfTest passed");
    }

    private NativePersistentMotionFrameSessionSelfTest() {
    }
}
