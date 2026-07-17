package dev.mcdlss.fabric;

import dev.mcdlss.core.DlssBackendDiagnostic;
import dev.mcdlss.core.NativeBridge;
import dev.mcdlss.core.NativeAdapterIdentity;
import dev.mcdlss.core.NativeLibraryBridge;
import dev.mcdlss.core.NativeProbeResult;
import dev.mcdlss.core.NativeInteropSessionInfo;
import dev.mcdlss.core.NativeReadbackFingerprint;
import dev.mcdlss.core.NativePersistentInteropSessionInfo;
import dev.mcdlss.core.NativePersistentFrameSessionInfo;
import dev.mcdlss.core.NativePersistentMotionFrameSessionInfo;
import dev.mcdlss.core.NativeMotionFrameReadbackFingerprint;
import dev.mcdlss.core.NativeFrameReadbackFingerprint;
import dev.mcdlss.core.NativeLiveDlssFrameResult;
import dev.mcdlss.core.NativeLiveDlssSessionInfo;
import dev.mcdlss.core.NativeTemporalConstants;
import dev.mcdlss.core.NativeUpscalerExecutionMode;
import dev.mcdlss.debug.DlssDebugSnapshot;
import dev.mcdlss.debug.DlssDebugSnapshotFactory;

public final class McDlssFabricEntrypoint {
    private final NativeBridge nativeBridge;

    public McDlssFabricEntrypoint() {
        this(NativeLibraryBridge.loadDefault());
    }

    public McDlssFabricEntrypoint(NativeBridge nativeBridge) {
        this.nativeBridge = nativeBridge;
    }

    public String describe() {
        return "Fabric adapter placeholder; Minecraft renderer hook is not installed.";
    }

    public NativeProbeResult probeNative() {
        return nativeBridge.probeSystem();
    }

    public NativeAdapterIdentity probeD3D12Adapter() {
        return nativeBridge.probeD3D12Adapter();
    }

    public NativeInteropSessionInfo openD3D12InteropSession(int width, int height) {
        return nativeBridge.openD3D12InteropSession(width, height);
    }

    public boolean submitD3D12InteropReadback(long sessionId) {
        return nativeBridge.submitD3D12InteropReadback(sessionId);
    }

    public boolean verifyD3D12InteropReadback(long sessionId) {
        return nativeBridge.verifyD3D12InteropReadback(sessionId);
    }

    public NativeReadbackFingerprint inspectD3D12InteropReadback(long sessionId) {
        return nativeBridge.inspectD3D12InteropReadback(sessionId);
    }

    public void closeD3D12InteropSession(long sessionId) {
        nativeBridge.closeD3D12InteropSession(sessionId);
    }

    public NativePersistentInteropSessionInfo openD3D12PersistentInteropSession(
            int width, int height) {
        return nativeBridge.openD3D12PersistentInteropSession(width, height);
    }

    public boolean submitD3D12PersistentReadback(
            long sessionId, long waitValue, long signalValue) {
        return nativeBridge.submitD3D12PersistentReadback(
                sessionId, waitValue, signalValue);
    }

    public NativeReadbackFingerprint inspectD3D12PersistentReadback(
            long sessionId, long signalValue) {
        return nativeBridge.inspectD3D12PersistentReadback(sessionId, signalValue);
    }

    public void closeD3D12PersistentInteropSession(long sessionId) {
        nativeBridge.closeD3D12PersistentInteropSession(sessionId);
    }

    public NativePersistentFrameSessionInfo openD3D12PersistentFrameSession(
            int width, int height) {
        return nativeBridge.openD3D12PersistentFrameSession(width, height);
    }

    public boolean submitD3D12PersistentFrameReadback(
            long sessionId, long waitValue, long signalValue) {
        return nativeBridge.submitD3D12PersistentFrameReadback(
                sessionId, waitValue, signalValue);
    }

    public NativeFrameReadbackFingerprint inspectD3D12PersistentFrameReadback(
            long sessionId, long signalValue, int expectedPixelCount) {
        return nativeBridge.inspectD3D12PersistentFrameReadback(
                sessionId, signalValue, expectedPixelCount);
    }

    public void closeD3D12PersistentFrameSession(long sessionId) {
        nativeBridge.closeD3D12PersistentFrameSession(sessionId);
    }

    public NativePersistentMotionFrameSessionInfo openD3D12PersistentMotionFrameSession(
            int width, int height) {
        return nativeBridge.openD3D12PersistentMotionFrameSession(width, height);
    }

    public boolean submitD3D12PersistentMotionFrameReadback(
            long sessionId, long waitValue, long signalValue) {
        return nativeBridge.submitD3D12PersistentMotionFrameReadback(
                sessionId, waitValue, signalValue);
    }

    public NativeMotionFrameReadbackFingerprint inspectD3D12PersistentMotionFrameReadback(
            long sessionId, long signalValue, int expectedPixelCount) {
        return nativeBridge.inspectD3D12PersistentMotionFrameReadback(
                sessionId, signalValue, expectedPixelCount);
    }

    public void closeD3D12PersistentMotionFrameSession(long sessionId) {
        nativeBridge.closeD3D12PersistentMotionFrameSession(sessionId);
    }

    public NativeLiveDlssSessionInfo openLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath) {
        return nativeBridge.openLiveDlssSession(
                outputWidth, outputHeight, pluginPath, logPath);
    }

    public boolean submitLiveDlssEvaluation(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants) {
        return nativeBridge.submitLiveDlssEvaluation(
                sessionId, slotIndex, waitValue, signalValue, constants);
    }

    public boolean submitLiveDlssEvaluation(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants, NativeUpscalerExecutionMode mode) {
        return nativeBridge.submitLiveDlssEvaluation(
                sessionId, slotIndex, waitValue, signalValue, constants, mode);
    }

    public boolean isLiveUpscalerSlotReady(long sessionId, int slotIndex) {
        return nativeBridge.isLiveUpscalerSlotReady(sessionId, slotIndex);
    }

    public NativeLiveDlssFrameResult inspectLiveDlssEvaluation(
            long sessionId, int slotIndex, long signalValue) {
        return nativeBridge.inspectLiveDlssEvaluation(
                sessionId, slotIndex, signalValue);
    }

    public void closeLiveDlssSession(long sessionId) {
        nativeBridge.closeLiveDlssSession(sessionId);
    }

    public DlssDebugSnapshot debugSnapshot() {
        return DlssDebugSnapshotFactory.fromProbe(
                probeNative(),
                DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked());
    }
}
