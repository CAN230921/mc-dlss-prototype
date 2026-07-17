package dev.mcdlss.debug;

import dev.mcdlss.core.DlssBackendDiagnostic;
import dev.mcdlss.core.DlssResourcePathStatus;
import dev.mcdlss.core.NativeProbeResult;
import dev.mcdlss.core.RenderBackendKind;

public final class DlssDebugSnapshotFactorySelfTest {
    public static void main(String[] args) {
        reportsUnavailableNativeProbe();
        reportsAvailableNativeProbeWithBlockedOpenGlBackend();
        System.out.println("DlssDebugSnapshotFactorySelfTest passed");
    }

    private static void reportsUnavailableNativeProbe() {
        DlssDebugSnapshot snapshot = DlssDebugSnapshotFactory.fromProbe(
                NativeProbeResult.unavailable("not-loaded", "library missing"),
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked());

        if (snapshot.nativeAvailable()) {
            throw new AssertionError("Expected native probe to be unavailable");
        }
        if (!"not-loaded".equals(snapshot.nativeVersion())) {
            throw new AssertionError("Expected native version from probe");
        }
        if (!snapshot.lastError().contains("library missing")) {
            throw new AssertionError("Expected native probe message as blocker");
        }
        if (snapshot.backendDiagnostic().backend() != RenderBackendKind.OPENGL) {
            throw new AssertionError("Expected OpenGL backend diagnostic");
        }
    }

    private static void reportsAvailableNativeProbeWithBlockedOpenGlBackend() {
        DlssDebugSnapshot snapshot = DlssDebugSnapshotFactory.fromProbe(
                NativeProbeResult.available("mc-dlss-native/0.1.0", "native ok"),
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked());

        if (!snapshot.nativeAvailable()) {
            throw new AssertionError("Expected native probe to be available");
        }
        if (!"mc-dlss-native/0.1.0".equals(snapshot.nativeVersion())) {
            throw new AssertionError("Expected native version from probe");
        }
        if (snapshot.backendDiagnostic().resourcePathStatus() != DlssResourcePathStatus.BLOCKED_UNSUPPORTED_BACKEND) {
            throw new AssertionError("Expected unsupported backend blocker");
        }
        if (!snapshot.lastError().contains("OpenGL")) {
            throw new AssertionError("Expected backend diagnostic message as blocker");
        }
        if (snapshot.backendDiagnostic().dlssEvaluationReady()) {
            throw new AssertionError("OpenGL diagnostic must not be DLSS-ready");
        }
    }

    private DlssDebugSnapshotFactorySelfTest() {
    }
}
