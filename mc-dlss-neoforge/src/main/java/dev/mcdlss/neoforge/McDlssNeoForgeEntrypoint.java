package dev.mcdlss.neoforge;

import dev.mcdlss.core.DlssBackendDiagnostic;
import dev.mcdlss.core.NativeBridge;
import dev.mcdlss.core.NativeLibraryBridge;
import dev.mcdlss.core.NativeProbeResult;
import dev.mcdlss.core.NativeLiveDlssFrameResult;
import dev.mcdlss.core.NativeLiveDlssSessionInfo;
import dev.mcdlss.core.NativeTemporalConstants;
import dev.mcdlss.core.NativeUpscalerExecutionMode;
import dev.mcdlss.debug.DlssDebugSnapshot;
import dev.mcdlss.debug.DlssDebugSnapshotFactory;

public final class McDlssNeoForgeEntrypoint {
    private final NativeBridge nativeBridge;

    public McDlssNeoForgeEntrypoint() {
        this(NativeLibraryBridge.loadDefault());
    }

    public McDlssNeoForgeEntrypoint(NativeBridge nativeBridge) {
        this.nativeBridge = nativeBridge;
    }

    public String describe() {
        return "NeoForge adapter placeholder; Minecraft renderer hook is not installed.";
    }

    public NativeProbeResult probeNative() {
        return nativeBridge.probeSystem();
    }

    public NativeLiveDlssSessionInfo openLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath) {
        return nativeBridge.openLiveDlssSession(outputWidth, outputHeight, pluginPath, logPath);
    }

    public NativeLiveDlssSessionInfo openLiveFsr3Session(
            int outputWidth, int outputHeight, String loaderPath,
            dev.mcdlss.core.DlssQualityMode qualityMode) {
        return nativeBridge.openLiveFsr3Session(
                outputWidth, outputHeight, loaderPath, qualityMode);
    }

    public NativeLiveDlssSessionInfo openLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath,
            dev.mcdlss.core.DlssQualityMode qualityMode) {
        return nativeBridge.openLiveDlssSession(
                outputWidth, outputHeight, pluginPath, logPath, qualityMode);
    }

    public boolean submitLiveDlssEvaluation(
            long sessionId,
            int slotIndex,
            long waitValue,
            long signalValue,
            NativeTemporalConstants constants,
            NativeUpscalerExecutionMode mode) {
        return nativeBridge.submitLiveDlssEvaluation(
                sessionId, slotIndex, waitValue, signalValue, constants, mode);
    }

    public boolean submitLiveFsr3Upscale(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants, float frameTimeMilliseconds,
            boolean generateFrame) {
        return nativeBridge.submitLiveFsr3Upscale(
                sessionId, slotIndex, waitValue, signalValue,
                constants, frameTimeMilliseconds, generateFrame);
    }

    public int presentLiveFsr3DxgiFrame(
            long sessionId, long windowHandle, int slotIndex, long signalValue,
            int width, int height) {
        return nativeBridge.presentLiveFsr3DxgiFrame(
                sessionId, windowHandle, slotIndex, signalValue, width, height);
    }

    public boolean isLiveUpscalerSlotReady(long sessionId, int slotIndex) {
        return nativeBridge.isLiveUpscalerSlotReady(sessionId, slotIndex);
    }

    public NativeLiveDlssFrameResult inspectLiveDlssEvaluation(
            long sessionId, int slotIndex, long signalValue) {
        return nativeBridge.inspectLiveDlssEvaluation(sessionId, slotIndex, signalValue);
    }

    public void closeLiveDlssSession(long sessionId) {
        nativeBridge.closeLiveDlssSession(sessionId);
    }

    public void shutdownLiveDlssProcess() {
        nativeBridge.shutdownLiveDlssProcess();
    }

    public DlssDebugSnapshot debugSnapshot() {
        return DlssDebugSnapshotFactory.fromProbe(
                probeNative(),
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked());
    }
}
