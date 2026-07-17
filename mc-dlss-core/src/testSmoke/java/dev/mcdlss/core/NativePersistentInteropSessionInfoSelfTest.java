package dev.mcdlss.core;

public final class NativePersistentInteropSessionInfoSelfTest {
    public static void main(String[] args) {
        validatesDynamicDimensions();
        requiresUnsupportedBridgeDefaults();
        decodesPersistentFingerprintDiagnostics();
        System.out.println("NativePersistentInteropSessionInfoSelfTest passed");
    }

    private static void validatesDynamicDimensions() {
        NativePersistentInteropSessionInfo valid =
                NativePersistentInteropSessionInfo.available(3, 5, 7, 1920, 1080);
        if (!valid.available() || valid.width() != 1920 || valid.height() != 1080) {
            throw new AssertionError("Expected full-resolution persistent session");
        }
        if (NativePersistentInteropSessionInfo.available(3, 5, 7, 0, 1080).available()
                || NativePersistentInteropSessionInfo.available(3, 5, 7, 8193, 1080).available()) {
            throw new AssertionError("Persistent dimensions must stay within 1..8192");
        }
    }

    private static void requiresUnsupportedBridgeDefaults() {
        NativeBridge bridge = () -> NativeProbeResult.unavailable("test", "test");
        if (bridge.openD3D12PersistentInteropSession(640, 480).available()
                || bridge.submitD3D12PersistentReadback(1, 1, 2)
                || bridge.inspectD3D12PersistentReadback(1, 2).available()) {
            throw new AssertionError("Default bridge must reject persistent operations");
        }
        bridge.closeD3D12PersistentInteropSession(1);
    }

    private static void decodesPersistentFingerprintDiagnostics() {
        NativeReadbackFingerprint ready = NativeLibraryBridge.decodePersistentFingerprint(
                new long[] {1, 0x1234L, 1, 42, 2, 2}, 2);
        if (!ready.available() || ready.hash() != 0x1234L
                || !ready.nonUniform() || ready.nonBlackPixelCount() != 42) {
            throw new AssertionError("Expected ready persistent fingerprint");
        }

        NativeReadbackFingerprint timeout = NativeLibraryBridge.decodePersistentFingerprint(
                new long[] {3, 0, 0, 0, 1, 2}, 2);
        if (timeout.available()
                || !timeout.message().contains("timeout")
                || !timeout.message().contains("requested=2")
                || !timeout.message().contains("completed=1")
                || !timeout.message().contains("lastSubmitted=2")) {
            throw new AssertionError("Expected detailed persistent timeout diagnostic");
        }
    }

    private NativePersistentInteropSessionInfoSelfTest() {
    }
}
