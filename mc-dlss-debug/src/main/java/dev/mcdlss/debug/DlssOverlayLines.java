package dev.mcdlss.debug;

import java.util.List;

public final class DlssOverlayLines {
    private static final int MAX_MESSAGE_LENGTH = 87;

    public static List<String> fromSnapshot(DlssDebugSnapshot snapshot) {
        DlssDebugSnapshot safeSnapshot = snapshot == null
                ? DlssDebugSnapshot.unavailable("Debug snapshot is null")
                : snapshot;

        return List.of(
                "MC DLSS Prototype",
                "native: " + safeSnapshot.nativeAvailable() + " " + safeSnapshot.nativeVersion(),
                "backend: " + safeSnapshot.backendDiagnostic().backend()
                        + " " + safeSnapshot.backendDiagnostic().resourcePathStatus(),
                "dlss-ready: " + safeSnapshot.backendDiagnostic().dlssEvaluationReady(),
                "message: " + truncate(safeSnapshot.lastError()));
    }

    private static String truncate(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.length() <= MAX_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private DlssOverlayLines() {
    }
}
