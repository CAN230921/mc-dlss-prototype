package dev.mcdlss.core;

public final class NativeAdapterIdentitySelfTest {
    public static void main(String[] args) {
        acceptsCanonicalLuid();
        rejectsMalformedLuid();
        reportsUnavailableIdentity();
        suppliesDefaultBridgeFallback();
        System.out.println("NativeAdapterIdentitySelfTest passed");
    }

    private static void acceptsCanonicalLuid() {
        NativeAdapterIdentity identity = NativeAdapterIdentity.available("A1B2C3D4E5F60718");
        if (!identity.available()) {
            throw new AssertionError("Expected available adapter identity");
        }
        if (!"a1b2c3d4e5f60718".equals(identity.luid())) {
            throw new AssertionError("Expected normalized lowercase LUID");
        }
        if (!identity.message().isEmpty()) {
            throw new AssertionError("Available identity should have no error message");
        }
    }

    private static void rejectsMalformedLuid() {
        for (String value : new String[] {"", "1234", "0123456789abcdeg", null}) {
            try {
                NativeAdapterIdentity.available(value);
                throw new AssertionError("Expected malformed LUID rejection: " + value);
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
        }
    }

    private static void reportsUnavailableIdentity() {
        NativeAdapterIdentity identity = NativeAdapterIdentity.unavailable("adapter missing");
        if (identity.available() || !identity.luid().isEmpty()) {
            throw new AssertionError("Unavailable identity must not carry a LUID");
        }
        if (!"adapter missing".equals(identity.message())) {
            throw new AssertionError("Expected unavailable reason");
        }
    }

    private static void suppliesDefaultBridgeFallback() {
        NativeBridge bridge = () -> NativeProbeResult.unavailable("test", "not used");
        NativeAdapterIdentity identity = bridge.probeD3D12Adapter();
        if (identity.available() || !identity.message().contains("not supported")) {
            throw new AssertionError("Expected default adapter probe fallback");
        }
    }

    private NativeAdapterIdentitySelfTest() {
    }
}
