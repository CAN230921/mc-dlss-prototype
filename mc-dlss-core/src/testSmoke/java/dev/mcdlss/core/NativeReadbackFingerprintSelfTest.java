package dev.mcdlss.core;

public final class NativeReadbackFingerprintSelfTest {
    public static void main(String[] args) {
        validatesNativeFingerprintResults();
        requiresUnsupportedBridgeDefault();
        System.out.println("NativeReadbackFingerprintSelfTest passed");
    }

    private static void validatesNativeFingerprintResults() {
        NativeReadbackFingerprint available =
                NativeReadbackFingerprint.available(0x4d25077f9dcd5758L, true, 7);
        if (!available.available() || !available.nonUniform()
                || available.nonBlackPixelCount() != 7) {
            throw new AssertionError("Expected available native fingerprint");
        }
        if (!"4d25077f9dcd5758".equals(available.hashHex())) {
            throw new AssertionError("Unexpected unsigned hash formatting");
        }
        if (NativeReadbackFingerprint.available(1L, true, -1).available()) {
            throw new AssertionError("Negative non-black count must be rejected");
        }
    }

    private static void requiresUnsupportedBridgeDefault() {
        NativeBridge bridge = () -> NativeProbeResult.unavailable("test", "test");
        if (bridge.inspectD3D12InteropReadback(1L).available()) {
            throw new AssertionError("Default bridge must not inspect readback");
        }
    }

    private NativeReadbackFingerprintSelfTest() {
    }
}
