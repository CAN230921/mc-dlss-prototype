package dev.mcdlss.core;

public final class NativeProbeCliSelfTest {
    public static void main(String[] args) {
        formatsAvailableProbe();
        formatsUnavailableProbe();
        formatsAdapterIdentity();
        System.out.println("NativeProbeCliSelfTest passed");
    }

    private static void formatsAvailableProbe() {
        NativeProbeResult result = NativeProbeResult.available("mc-dlss-native/0.1.0", "probe ok");
        String formatted = NativeProbeCli.formatResult(result);

        if (!formatted.contains("available=true")) {
            throw new AssertionError("Expected availability in formatted result");
        }
        if (!formatted.contains("version=mc-dlss-native/0.1.0")) {
            throw new AssertionError("Expected version in formatted result");
        }
        if (NativeProbeCli.exitCodeFor(result) != 0) {
            throw new AssertionError("Available probe should return exit code 0");
        }
    }

    private static void formatsUnavailableProbe() {
        NativeProbeResult result = NativeProbeResult.unavailable("not-loaded", "missing dll");
        String formatted = NativeProbeCli.formatResult(result);

        if (!formatted.contains("available=false")) {
            throw new AssertionError("Expected unavailable state in formatted result");
        }
        if (!formatted.contains("message=missing dll")) {
            throw new AssertionError("Expected message in formatted result");
        }
        if (NativeProbeCli.exitCodeFor(result) != 2) {
            throw new AssertionError("Unavailable probe should return exit code 2");
        }
    }

    private static void formatsAdapterIdentity() {
        String formatted = NativeProbeCli.formatAdapterIdentity(
                NativeAdapterIdentity.available("A1B2C3D4E5F60718"));
        if (!formatted.contains("adapterAvailable=true")) {
            throw new AssertionError("Expected adapter availability");
        }
        if (!formatted.contains("adapterLuid=a1b2c3d4e5f60718")) {
            throw new AssertionError("Expected canonical adapter LUID");
        }
    }

    private NativeProbeCliSelfTest() {
    }
}
