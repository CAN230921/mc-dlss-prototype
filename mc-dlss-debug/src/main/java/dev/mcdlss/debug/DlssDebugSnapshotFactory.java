package dev.mcdlss.debug;

import dev.mcdlss.core.DlssBackendDiagnostic;
import dev.mcdlss.core.DlssQualityMode;
import dev.mcdlss.core.NativeProbeResult;

public final class DlssDebugSnapshotFactory {
    public static DlssDebugSnapshot fromProbe(
            NativeProbeResult nativeProbe,
            DlssBackendDiagnostic backendDiagnostic) {
        NativeProbeResult safeProbe = nativeProbe == null
                ? NativeProbeResult.unavailable("not-loaded", "Native probe result is null")
                : nativeProbe;
        DlssBackendDiagnostic safeBackend = backendDiagnostic == null
                ? DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked()
                : backendDiagnostic;

        return new DlssDebugSnapshot(
                safeProbe.available(),
                safeBackend,
                DlssQualityMode.QUALITY,
                0,
                0,
                0,
                0,
                safeProbe.version(),
                currentBlocker(safeProbe, safeBackend));
    }

    private static String currentBlocker(NativeProbeResult nativeProbe, DlssBackendDiagnostic backendDiagnostic) {
        if (!nativeProbe.available()) {
            return nativeProbe.message();
        }
        if (!backendDiagnostic.dlssEvaluationReady()) {
            return backendDiagnostic.message();
        }
        return "";
    }

    private DlssDebugSnapshotFactory() {
    }
}
