package dev.mcdlss.core;

public interface NativeBridge {
    NativeProbeResult probeSystem();

    default NativeAdapterIdentity probeD3D12Adapter() {
        return NativeAdapterIdentity.unavailable(
                "D3D12 adapter identity is not supported by this native bridge");
    }

    default NativeInteropSessionInfo openD3D12InteropSession(int width, int height) {
        return NativeInteropSessionInfo.unavailable(
                "D3D12 interop sessions are not supported by this native bridge");
    }

    default boolean submitD3D12InteropReadback(long sessionId) {
        return false;
    }

    default boolean verifyD3D12InteropReadback(long sessionId) {
        return false;
    }

    default NativeReadbackFingerprint inspectD3D12InteropReadback(long sessionId) {
        return NativeReadbackFingerprint.unavailable(
                "D3D12 interop readback inspection is not supported by this native bridge");
    }

    default void closeD3D12InteropSession(long sessionId) {
    }

    default NativePersistentInteropSessionInfo openD3D12PersistentInteropSession(
            int width, int height) {
        return NativePersistentInteropSessionInfo.unavailable(
                "Persistent D3D12 interop is not supported by this native bridge");
    }

    default boolean submitD3D12PersistentReadback(
            long sessionId, long waitValue, long signalValue) {
        return false;
    }

    default NativeReadbackFingerprint inspectD3D12PersistentReadback(
            long sessionId, long signalValue) {
        return NativeReadbackFingerprint.unavailable(
                "Persistent D3D12 readback inspection is not supported by this native bridge");
    }

    default void closeD3D12PersistentInteropSession(long sessionId) {
    }

    default NativePersistentFrameSessionInfo openD3D12PersistentFrameSession(
            int width, int height) {
        return NativePersistentFrameSessionInfo.unavailable(
                "Persistent frame interop is unavailable");
    }

    default boolean submitD3D12PersistentFrameReadback(
            long sessionId, long waitValue, long signalValue) {
        return false;
    }

    default NativeFrameReadbackFingerprint inspectD3D12PersistentFrameReadback(
            long sessionId, long signalValue, int expectedPixelCount) {
        return NativeFrameReadbackFingerprint.unavailable(
                "Persistent frame inspection is unavailable");
    }

    default void closeD3D12PersistentFrameSession(long sessionId) {
    }

    default NativePersistentMotionFrameSessionInfo openD3D12PersistentMotionFrameSession(
            int width, int height) {
        return NativePersistentMotionFrameSessionInfo.unavailable(
                "Persistent motion frame interop is unavailable");
    }

    default boolean submitD3D12PersistentMotionFrameReadback(
            long sessionId, long waitValue, long signalValue) {
        return false;
    }

    default NativeMotionFrameReadbackFingerprint inspectD3D12PersistentMotionFrameReadback(
            long sessionId, long signalValue, int expectedPixelCount) {
        return NativeMotionFrameReadbackFingerprint.unavailable(
                "Persistent motion frame inspection is unavailable");
    }

    default void closeD3D12PersistentMotionFrameSession(long sessionId) {
    }

    default NativeLiveDlssSessionInfo openLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath) {
        return openLiveDlssSession(outputWidth, outputHeight, pluginPath, logPath,
                DlssQualityMode.QUALITY);
    }

    default NativeLiveDlssSessionInfo openLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath,
            DlssQualityMode qualityMode) {
        return NativeLiveDlssSessionInfo.unavailable("Live DLSS is unavailable");
    }

    default NativeLiveDlssSessionInfo openLiveFsr3Session(
            int outputWidth, int outputHeight, String loaderPath,
            DlssQualityMode qualityMode) {
        return NativeLiveDlssSessionInfo.unavailable("Live FSR3 is unavailable");
    }

    default boolean submitLiveDlssEvaluation(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants) {
        return submitLiveDlssEvaluation(
                sessionId, slotIndex, waitValue, signalValue,
                constants, NativeUpscalerExecutionMode.VALIDATING);
    }

    default boolean submitLiveDlssEvaluation(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants, NativeUpscalerExecutionMode mode) {
        return false;
    }

    default boolean submitLiveFsr3Upscale(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants, float frameTimeMilliseconds,
            boolean generateFrame) {
        return false;
    }

    default int presentLiveFsr3DxgiFrame(
            long sessionId, long windowHandle, int slotIndex, long signalValue,
            int width, int height) {
        return 0;
    }

    default boolean isLiveUpscalerSlotReady(long sessionId, int slotIndex) {
        return false;
    }

    default NativeLiveDlssFrameResult inspectLiveDlssEvaluation(
            long sessionId, int slotIndex, long signalValue) {
        return NativeLiveDlssFrameResult.failure(
                "UNAVAILABLE", "Live DLSS inspection is unavailable", 0, 0);
    }

    default void closeLiveDlssSession(long sessionId) {
    }

    default void shutdownLiveDlssProcess() {
    }
}
