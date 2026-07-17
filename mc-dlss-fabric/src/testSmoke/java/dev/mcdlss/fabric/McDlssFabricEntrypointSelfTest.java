package dev.mcdlss.fabric;

import dev.mcdlss.core.NativeBridge;
import dev.mcdlss.core.NativeProbeResult;
import dev.mcdlss.core.RenderBackendKind;
import dev.mcdlss.debug.DlssDebugSnapshot;

public final class McDlssFabricEntrypointSelfTest {
    public static void main(String[] args) {
        reportsDebugSnapshotFromNativeBridge();
        System.out.println("McDlssFabricEntrypointSelfTest passed");
    }

    private static void reportsDebugSnapshotFromNativeBridge() {
        FakeNativeBridge bridge = new FakeNativeBridge(
                NativeProbeResult.unavailable("not-loaded", "fabric native missing"));
        McDlssFabricEntrypoint entrypoint = new McDlssFabricEntrypoint(bridge);

        DlssDebugSnapshot snapshot = entrypoint.debugSnapshot();

        if (bridge.probeCalls != 1) {
            throw new AssertionError("Expected debug snapshot to probe native bridge once");
        }
        if (snapshot.nativeAvailable()) {
            throw new AssertionError("Expected unavailable native status");
        }
        if (!snapshot.lastError().contains("fabric native missing")) {
            throw new AssertionError("Expected Fabric native probe message in snapshot");
        }
        if (snapshot.backendDiagnostic().backend() != RenderBackendKind.OPENGL) {
            throw new AssertionError("Expected current OpenGL backend diagnostic");
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

    private McDlssFabricEntrypointSelfTest() {
    }
}
