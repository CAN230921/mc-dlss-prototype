package dev.mcdlss.core;

public final class NativePersistentFrameSessionSelfTest {
    public static void main(String[] args) {
        NativePersistentFrameSessionInfo info = NativePersistentFrameSessionInfo.available(
                3L, 5L, 7L, 9L, 854, 480);
        if (!info.available() || info.sessionId() != 3L
                || info.colorTextureHandle() != 5L
                || info.depthTextureHandle() != 7L
                || info.fenceHandle() != 9L) {
            throw new AssertionError("Expected complete frame session handles");
        }
        if (NativePersistentFrameSessionInfo.available(
                3L, 5L, 7L, 9L, 0, 480).available()) {
            throw new AssertionError("Invalid frame dimensions were accepted");
        }

        long[] ready = {
            1, 11, 1, 409920,
            13, 1, 409920, 409920, 350000, 59920,
            Float.floatToRawIntBits(0.2f), Float.floatToRawIntBits(1.0f), 2, 2
        };
        NativeFrameReadbackFingerprint fingerprint =
                NativeLibraryBridge.decodePersistentFrameFingerprint(
                        ready, 2, 409920);
        if (!fingerprint.available() || fingerprint.colorHash() != 11L
                || fingerprint.depthHash() != 13L
                || fingerprint.depthFiniteSampleCount() != 409920
                || fingerprint.minimumDepth() != 0.2f
                || fingerprint.maximumDepth() != 1.0f) {
            throw new AssertionError("Expected decoded color/depth fingerprint");
        }

        long[] timeoutValues = {3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2};
        NativeFrameReadbackFingerprint timeout =
                NativeLibraryBridge.decodePersistentFrameFingerprint(
                        timeoutValues, 2, 409920);
        if (timeout.available() || !timeout.message().contains("timeout")
                || !timeout.message().contains("completed=1")
                || !timeout.message().contains("lastSubmitted=2")) {
            throw new AssertionError("Expected detailed frame timeout diagnostic");
        }

        NativeBridge bridge = () -> NativeProbeResult.unavailable("test", "test");
        if (bridge.openD3D12PersistentFrameSession(854, 480).available()
                || bridge.submitD3D12PersistentFrameReadback(1, 1, 2)
                || bridge.inspectD3D12PersistentFrameReadback(1, 2, 409920).available()) {
            throw new AssertionError("Default bridge accepted frame interop");
        }
        bridge.closeD3D12PersistentFrameSession(1);
        System.out.println("NativePersistentFrameSessionSelfTest passed");
    }

    private NativePersistentFrameSessionSelfTest() {
    }
}
