package dev.mcdlss.neoforge;

import dev.mcdlss.core.NativeBridge;
import dev.mcdlss.core.NativeProbeResult;
import dev.mcdlss.core.RenderBackendKind;
import dev.mcdlss.debug.DlssDebugSnapshot;

public final class McDlssNeoForgeEntrypointSelfTest {
    public static void main(String[] args) {
        reportsDebugSnapshotFromNativeBridge();
        System.out.println("McDlssNeoForgeEntrypointSelfTest passed");
    }

    private static void reportsDebugSnapshotFromNativeBridge() {
        FakeNativeBridge bridge = new FakeNativeBridge(
                NativeProbeResult.available("mc-dlss-native/test", "neoforge native ok"));
        McDlssNeoForgeEntrypoint entrypoint = new McDlssNeoForgeEntrypoint(bridge);

        DlssDebugSnapshot snapshot = entrypoint.debugSnapshot();

        if (bridge.probeCalls != 1) {
            throw new AssertionError("Expected debug snapshot to probe native bridge once");
        }
        if (!snapshot.nativeAvailable()) {
            throw new AssertionError("Expected available native status");
        }
        if (!"mc-dlss-native/test".equals(snapshot.nativeVersion())) {
            throw new AssertionError("Expected NeoForge native version in snapshot");
        }
        if (snapshot.backendDiagnostic().backend() != RenderBackendKind.OPENGL) {
            throw new AssertionError("Expected current OpenGL backend diagnostic");
        }
        if (!snapshot.lastError().contains("OpenGL")) {
            throw new AssertionError("Expected backend blocker after native probe succeeds");
        }
    }

    private static final class FakeNativeBridge implements NativeBridge {
        private final NativeProbeResult result;
        private int probeCalls;

        private FakeNativeBridge(NativeProbeResult result) {
            this.result = result;
        }

        @Override
        public NativeProbeResult probeSystem() {
            probeCalls++;
            return result;
        }
    }

    private McDlssNeoForgeEntrypointSelfTest() {
    }
}
