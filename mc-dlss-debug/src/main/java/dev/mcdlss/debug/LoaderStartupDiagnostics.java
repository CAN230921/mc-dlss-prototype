package dev.mcdlss.debug;

public final class LoaderStartupDiagnostics {
    public static String format(String loaderName, DlssDebugSnapshot snapshot) {
        String safeLoaderName = normalize(loaderName);
        DlssDebugSnapshot safeSnapshot = snapshot == null
                ? DlssDebugSnapshot.unavailable("Debug snapshot is null")
                : snapshot;

        return "loader=" + safeLoaderName
                + " nativeAvailable=" + safeSnapshot.nativeAvailable()
                + " nativeVersion=" + safeSnapshot.nativeVersion()
                + " backend=" + safeSnapshot.backendDiagnostic().backend()
                + " resourcePath=" + safeSnapshot.backendDiagnostic().resourcePathStatus()
                + " dlssReady=" + safeSnapshot.backendDiagnostic().dlssEvaluationReady()
                + " message=" + safeSnapshot.lastError();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private LoaderStartupDiagnostics() {
    }
}
