package dev.mcdlss.core;

import java.nio.file.Path;

public final class NativeProbeCli {
    public static void main(String[] args) {
        NativeLibraryBridge bridge = args.length > 0
                ? NativeLibraryBridge.loadFromPath(Path.of(args[0]))
                : NativeLibraryBridge.loadDefault();

        NativeProbeResult result = bridge.probeSystem();
        NativeAdapterIdentity adapterIdentity = bridge.probeD3D12Adapter();
        System.out.println(formatResult(result));
        System.out.println(formatAdapterIdentity(adapterIdentity));
        System.exit(exitCodeFor(result));
    }

    public static String formatResult(NativeProbeResult result) {
        return "available=" + result.available()
                + System.lineSeparator()
                + "version=" + result.version()
                + System.lineSeparator()
                + "message=" + result.message();
    }

    public static int exitCodeFor(NativeProbeResult result) {
        return result.available() ? 0 : 2;
    }

    public static String formatAdapterIdentity(NativeAdapterIdentity identity) {
        NativeAdapterIdentity safeIdentity = identity == null
                ? NativeAdapterIdentity.unavailable("Adapter identity is null")
                : identity;
        return "adapterAvailable=" + safeIdentity.available()
                + System.lineSeparator()
                + "adapterLuid=" + safeIdentity.luid()
                + System.lineSeparator()
                + "adapterMessage=" + safeIdentity.message();
    }

    private NativeProbeCli() {
    }
}
