package dev.mcdlss.core;

import java.nio.file.Path;

public final class NativeBundleProbeCli {
    public static void main(String[] args) {
        Path runDirectory = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        NativeBundleLoadResult bundle = NativeBundleBootstrap.load(runDirectory);
        NativeProbeResult probe = NativeLibraryBridge.loadDefault().probeSystem();
        System.out.println("bundleStage=" + bundle.stage()
                + " bundleReady=" + bundle.available()
                + " nativeReady=" + probe.available()
                + " nativeVersion=" + probe.version()
                + " message=" + probe.message());
        if (!bundle.available() || !probe.available()) System.exit(1);
    }

    private NativeBundleProbeCli() {
    }
}
