package dev.mcdlss.core;

public final class NativeInteropSessionInfoSelfTest {
    public static void main(String[] args) {
        requiresExactLiveProbeDimensions();
        requiresPositiveSessionValues();
        requiresUnsupportedBridgeDefaults();
        System.out.println("NativeInteropSessionInfoSelfTest passed");
    }

    private static void requiresExactLiveProbeDimensions() {
        NativeInteropSessionInfo valid = NativeInteropSessionInfo.available(7L, 11L, 13L, 64, 64);
        if (!valid.available()) {
            throw new AssertionError("Expected exact 64 x 64 session to be available");
        }

        NativeInteropSessionInfo wrongWidth = NativeInteropSessionInfo.available(7L, 11L, 13L, 32, 64);
        if (wrongWidth.available()) {
            throw new AssertionError("Unexpected non-64 width acceptance");
        }
    }

    private static void requiresPositiveSessionValues() {
        NativeInteropSessionInfo invalid = NativeInteropSessionInfo.available(0L, 11L, 13L, 64, 64);
        if (invalid.available()) {
            throw new AssertionError("Unexpected zero session ID acceptance");
        }

        NativeInteropSessionInfo unavailable = NativeInteropSessionInfo.unavailable("not ready");
        if (!"not ready".equals(unavailable.message())) {
            throw new AssertionError("Unavailable message was not preserved");
        }
    }

    private static void requiresUnsupportedBridgeDefaults() {
        NativeBridge bridge = () -> NativeProbeResult.unavailable("test", "test");
        if (bridge.openD3D12InteropSession(64, 64).available()) {
            throw new AssertionError("Default bridge must not open an interop session");
        }
        if (bridge.submitD3D12InteropReadback(1L)) {
            throw new AssertionError("Default bridge must not submit an interop readback");
        }
        if (bridge.verifyD3D12InteropReadback(1L)) {
            throw new AssertionError("Default bridge must not verify an interop readback");
        }
        bridge.closeD3D12InteropSession(1L);
    }

    private NativeInteropSessionInfoSelfTest() {
    }
}
