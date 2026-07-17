package dev.mcdlss.debug;

import dev.mcdlss.core.DlssBackendDiagnostic;
import dev.mcdlss.core.NativeProbeResult;

public final class LoaderStartupDiagnosticsSelfTest {
    public static void main(String[] args) {
        formatsNativeAvailableBackendBlockedLine();
        System.out.println("LoaderStartupDiagnosticsSelfTest passed");
    }

    private static void formatsNativeAvailableBackendBlockedLine() {
        DlssDebugSnapshot snapshot = DlssDebugSnapshotFactory.fromProbe(
                NativeProbeResult.available("mc-dlss-native/test", "native ok"),
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked());

        String line = LoaderStartupDiagnostics.format("fabric", snapshot);

        if (!line.contains("loader=fabric")) {
            throw new AssertionError("Expected loader name in startup line");
        }
        if (!line.contains("nativeAvailable=true")) {
            throw new AssertionError("Expected native availability in startup line");
        }
        if (!line.contains("nativeVersion=mc-dlss-native/test")) {
            throw new AssertionError("Expected native version in startup line");
        }
        if (!line.contains("backend=OPENGL")) {
            throw new AssertionError("Expected backend in startup line");
        }
        if (!line.contains("resourcePath=BLOCKED_UNSUPPORTED_BACKEND")) {
            throw new AssertionError("Expected resource path status in startup line");
        }
        if (!line.contains("dlssReady=false")) {
            throw new AssertionError("Expected DLSS readiness in startup line");
        }
    }

    private LoaderStartupDiagnosticsSelfTest() {
    }
}
