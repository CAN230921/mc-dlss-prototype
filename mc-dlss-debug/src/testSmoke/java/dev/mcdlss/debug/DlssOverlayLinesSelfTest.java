package dev.mcdlss.debug;

import dev.mcdlss.core.DlssBackendDiagnostic;
import dev.mcdlss.core.NativeProbeResult;

import java.util.List;

public final class DlssOverlayLinesSelfTest {
    public static void main(String[] args) {
        formatsCompactOverlayLines();
        truncatesLongMessages();
        System.out.println("DlssOverlayLinesSelfTest passed");
    }

    private static void formatsCompactOverlayLines() {
        DlssDebugSnapshot snapshot = DlssDebugSnapshotFactory.fromProbe(
                NativeProbeResult.available("mc-dlss-native/test", "native ok"),
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked());

        List<String> lines = DlssOverlayLines.fromSnapshot(snapshot);

        if (lines.size() != 5) {
            throw new AssertionError("Expected five overlay lines");
        }
        if (!"MC DLSS Prototype".equals(lines.get(0))) {
            throw new AssertionError("Expected overlay title");
        }
        if (!lines.get(1).contains("native: true mc-dlss-native/test")) {
            throw new AssertionError("Expected native status line");
        }
        if (!lines.get(2).contains("backend: OPENGL BLOCKED_UNSUPPORTED_BACKEND")) {
            throw new AssertionError("Expected backend status line");
        }
        if (!lines.get(3).contains("dlss-ready: false")) {
            throw new AssertionError("Expected DLSS readiness line");
        }
        if (!lines.get(4).startsWith("message: ")) {
            throw new AssertionError("Expected message line");
        }
    }

    private static void truncatesLongMessages() {
        DlssDebugSnapshot snapshot = DlssDebugSnapshotFactory.fromProbe(
                NativeProbeResult.available("mc-dlss-native/test", "native ok"),
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked());

        List<String> lines = DlssOverlayLines.fromSnapshot(snapshot);
        if (lines.get(4).length() > 96) {
            throw new AssertionError("Expected compact message line");
        }
    }

    private DlssOverlayLinesSelfTest() {
    }
}
