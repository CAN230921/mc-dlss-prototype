package dev.mcdlss.debug;

import dev.mcdlss.core.DlssBackendDiagnostic;
import dev.mcdlss.core.DlssQualityMode;

public record DlssDebugSnapshot(
        boolean nativeAvailable,
        DlssBackendDiagnostic backendDiagnostic,
        DlssQualityMode selectedMode,
        int inputWidth,
        int inputHeight,
        int outputWidth,
        int outputHeight,
        String nativeVersion,
        String lastError) {
    public DlssDebugSnapshot {
        if (selectedMode == null) {
            selectedMode = DlssQualityMode.QUALITY;
        }
        if (backendDiagnostic == null) {
            backendDiagnostic = DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked();
        }
        if (inputWidth < 0 || inputHeight < 0 || outputWidth < 0 || outputHeight < 0) {
            throw new IllegalArgumentException("Render dimensions must be non-negative");
        }
        nativeVersion = normalize(nativeVersion);
        lastError = normalize(lastError);
    }

    public static DlssDebugSnapshot unavailable(String reason) {
        return new DlssDebugSnapshot(
                false,
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked(),
                DlssQualityMode.QUALITY,
                0,
                0,
                0,
                0,
                "not-loaded",
                reason);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
