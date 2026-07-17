package dev.mcdlss.core;

import java.nio.file.Path;

public final class NativeLibraryBridge implements NativeBridge {
    private static final String LIBRARY_NAME = "mc_dlss_native";

    private final boolean loaded;
    private final String loadMessage;

    private NativeLibraryBridge(boolean loaded, String loadMessage) {
        this.loaded = loaded;
        this.loadMessage = loadMessage == null ? "" : loadMessage;
    }

    public static NativeLibraryBridge loadDefault() {
        String packagedPath = System.getProperty("mcDlss.packagedNativePath", "");
        if (!packagedPath.isBlank()) {
            return loadFromPath(Path.of(packagedPath));
        }
        try {
            System.loadLibrary(LIBRARY_NAME);
            return new NativeLibraryBridge(true, "Loaded " + LIBRARY_NAME);
        } catch (UnsatisfiedLinkError error) {
            return new NativeLibraryBridge(false, error.getMessage());
        } catch (SecurityException error) {
            return new NativeLibraryBridge(false, error.getMessage());
        }
    }

    public static NativeLibraryBridge loadFromPath(Path libraryPath) {
        if (libraryPath == null) {
            return new NativeLibraryBridge(false, "Native library path is null");
        }

        try {
            System.load(libraryPath.toAbsolutePath().toString());
            return new NativeLibraryBridge(true, "Loaded " + libraryPath.toAbsolutePath());
        } catch (UnsatisfiedLinkError error) {
            return new NativeLibraryBridge(false, error.getMessage());
        } catch (SecurityException error) {
            return new NativeLibraryBridge(false, error.getMessage());
        }
    }

    public boolean loaded() {
        return loaded;
    }

    public String loadMessage() {
        return loadMessage;
    }

    @Override
    public NativeProbeResult probeSystem() {
        if (!loaded) {
            return NativeProbeResult.unavailable("not-loaded", loadMessage);
        }

        try {
            return NativeProbeResult.available(nativeGetVersion(), nativeProbeSystem());
        } catch (UnsatisfiedLinkError error) {
            return NativeProbeResult.unavailable("jni-error", error.getMessage());
        } catch (RuntimeException error) {
            return NativeProbeResult.unavailable("native-error", error.getMessage());
        }
    }

    @Override
    public NativeAdapterIdentity probeD3D12Adapter() {
        if (!loaded) {
            return NativeAdapterIdentity.unavailable(loadMessage);
        }

        try {
            String luid = nativeGetD3D12AdapterLuid();
            if (luid == null || luid.isBlank()) {
                return NativeAdapterIdentity.unavailable("No hardware D3D12 adapter is available");
            }
            return NativeAdapterIdentity.available(luid);
        } catch (UnsatisfiedLinkError error) {
            return NativeAdapterIdentity.unavailable(error.getMessage());
        } catch (RuntimeException error) {
            return NativeAdapterIdentity.unavailable(error.getMessage());
        }
    }

    @Override
    public NativeInteropSessionInfo openD3D12InteropSession(int width, int height) {
        if (!loaded) {
            return NativeInteropSessionInfo.unavailable(loadMessage);
        }

        try {
            long[] values = nativeOpenD3D12InteropSession(width, height);
            if (values == null || values.length != 3) {
                return NativeInteropSessionInfo.unavailable("Native interop session returned invalid data");
            }
            return NativeInteropSessionInfo.available(values[0], values[1], values[2], width, height);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeInteropSessionInfo.unavailable(error.getMessage());
        }
    }

    @Override
    public boolean submitD3D12InteropReadback(long sessionId) {
        if (!loaded || sessionId <= 0L) {
            return false;
        }
        try {
            return nativeSubmitD3D12InteropReadback(sessionId);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public boolean verifyD3D12InteropReadback(long sessionId) {
        if (!loaded || sessionId <= 0L) {
            return false;
        }
        try {
            return nativeVerifyD3D12InteropReadback(sessionId);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public NativeReadbackFingerprint inspectD3D12InteropReadback(long sessionId) {
        if (!loaded || sessionId <= 0L) {
            return NativeReadbackFingerprint.unavailable(
                    loaded ? "Invalid interop session ID" : loadMessage);
        }
        try {
            long[] values = nativeInspectD3D12InteropReadback(sessionId);
            if (values == null || values.length != 3
                    || values[2] < 0L || values[2] > Integer.MAX_VALUE) {
                return NativeReadbackFingerprint.unavailable(
                        "Native readback fingerprint returned invalid data");
            }
            return NativeReadbackFingerprint.available(
                    values[0], values[1] != 0L, (int) values[2]);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeReadbackFingerprint.unavailable(error.getMessage());
        }
    }

    @Override
    public void closeD3D12InteropSession(long sessionId) {
        if (!loaded || sessionId <= 0L) {
            return;
        }
        try {
            nativeCloseD3D12InteropSession(sessionId);
        } catch (UnsatisfiedLinkError | RuntimeException ignored) {
            // Cleanup is best effort so the Minecraft HUD remains available.
        }
    }

    @Override
    public NativePersistentInteropSessionInfo openD3D12PersistentInteropSession(
            int width, int height) {
        if (!loaded) {
            return NativePersistentInteropSessionInfo.unavailable(loadMessage);
        }
        try {
            long[] values = nativeOpenD3D12PersistentInteropSession(width, height);
            if (values == null || values.length != 3) {
                return NativePersistentInteropSessionInfo.unavailable(
                        "Native persistent session returned invalid data");
            }
            return NativePersistentInteropSessionInfo.available(
                    values[0], values[1], values[2], width, height);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativePersistentInteropSessionInfo.unavailable(error.getMessage());
        }
    }

    @Override
    public boolean submitD3D12PersistentReadback(
            long sessionId, long waitValue, long signalValue) {
        if (!loaded || sessionId <= 0 || waitValue <= 0 || signalValue <= 0) {
            return false;
        }
        try {
            return nativeSubmitD3D12PersistentReadback(
                    sessionId, waitValue, signalValue);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public NativeReadbackFingerprint inspectD3D12PersistentReadback(
            long sessionId, long signalValue) {
        if (!loaded || sessionId <= 0 || signalValue <= 0) {
            return NativeReadbackFingerprint.unavailable("Invalid persistent inspection values");
        }
        try {
            long[] values = nativeInspectD3D12PersistentReadback(sessionId, signalValue);
            return decodePersistentFingerprint(values, signalValue);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeReadbackFingerprint.unavailable(error.getMessage());
        }
    }

    static NativeReadbackFingerprint decodePersistentFingerprint(
            long[] values, long requestedSignal) {
        if (values == null || values.length != 6
                || values[3] < 0 || values[3] > Integer.MAX_VALUE) {
            return NativeReadbackFingerprint.unavailable(
                    "Native persistent fingerprint returned invalid data");
        }
        long status = values[0];
        if (status == 1) {
            return NativeReadbackFingerprint.available(
                    values[1], values[2] != 0, (int) values[3]);
        }
        String label = switch ((int) status) {
            case 2 -> "invalid request";
            case 3 -> "timeout";
            case 4 -> "missing session";
            case 5 -> "native exception";
            default -> "unavailable";
        };
        return NativeReadbackFingerprint.unavailable(
                "Persistent readback " + label
                        + " requested=" + requestedSignal
                        + " completed=" + values[4]
                        + " lastSubmitted=" + values[5]);
    }

    @Override
    public void closeD3D12PersistentInteropSession(long sessionId) {
        if (!loaded || sessionId <= 0) {
            return;
        }
        try {
            nativeCloseD3D12PersistentInteropSession(sessionId);
        } catch (UnsatisfiedLinkError | RuntimeException ignored) {
        }
    }

    @Override
    public NativePersistentFrameSessionInfo openD3D12PersistentFrameSession(
            int width, int height) {
        if (!loaded) {
            return NativePersistentFrameSessionInfo.unavailable(loadMessage);
        }
        try {
            long[] values = nativeOpenD3D12PersistentFrameSession(width, height);
            if (values == null || values.length != 4) {
                return NativePersistentFrameSessionInfo.unavailable(
                        "Native persistent frame session returned invalid data");
            }
            return NativePersistentFrameSessionInfo.available(
                    values[0], values[1], values[2], values[3], width, height);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativePersistentFrameSessionInfo.unavailable(error.getMessage());
        }
    }

    @Override
    public boolean submitD3D12PersistentFrameReadback(
            long sessionId, long waitValue, long signalValue) {
        if (!loaded || sessionId <= 0 || waitValue <= 0 || signalValue <= 0) {
            return false;
        }
        try {
            return nativeSubmitD3D12PersistentFrameReadback(
                    sessionId, waitValue, signalValue);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public NativeFrameReadbackFingerprint inspectD3D12PersistentFrameReadback(
            long sessionId, long signalValue, int expectedPixelCount) {
        if (!loaded || sessionId <= 0 || signalValue <= 0 || expectedPixelCount <= 0) {
            return NativeFrameReadbackFingerprint.unavailable(
                    "Invalid persistent frame inspection values");
        }
        try {
            return decodePersistentFrameFingerprint(
                    nativeInspectD3D12PersistentFrameReadback(sessionId, signalValue),
                    signalValue,
                    expectedPixelCount);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeFrameReadbackFingerprint.unavailable(error.getMessage());
        }
    }

    static NativeFrameReadbackFingerprint decodePersistentFrameFingerprint(
            long[] values, long requestedSignal, int expectedPixelCount) {
        if (values == null || values.length != 14 || expectedPixelCount <= 0) {
            return NativeFrameReadbackFingerprint.unavailable(
                    "Native persistent frame fingerprint returned invalid data");
        }
        if (values[0] == 1) {
            for (int index : new int[] {3, 6, 7, 8, 9}) {
                if (values[index] < 0 || values[index] > Integer.MAX_VALUE) {
                    return NativeFrameReadbackFingerprint.unavailable(
                            "Native persistent frame counts are invalid");
                }
            }
            if (values[6] > expectedPixelCount || values[7] > expectedPixelCount
                    || values[8] > expectedPixelCount || values[9] > expectedPixelCount) {
                return NativeFrameReadbackFingerprint.unavailable(
                        "Native persistent frame counts exceed dimensions");
            }
            return NativeFrameReadbackFingerprint.available(
                    values[1], values[2] != 0, (int) values[3],
                    values[4], values[5] != 0, (int) values[6], (int) values[7],
                    (int) values[8], (int) values[9],
                    Float.intBitsToFloat((int) values[10]),
                    Float.intBitsToFloat((int) values[11]));
        }
        String label = switch ((int) values[0]) {
            case 2 -> "invalid request";
            case 3 -> "timeout";
            case 4 -> "missing session";
            case 5 -> "native exception";
            default -> "unavailable";
        };
        return NativeFrameReadbackFingerprint.unavailable(
                "Persistent frame readback " + label
                        + " requested=" + requestedSignal
                        + " completed=" + values[12]
                        + " lastSubmitted=" + values[13]);
    }

    static NativeMotionFrameReadbackFingerprint decodePersistentMotionFrameFingerprint(
            long[] values, long requestedSignal, int expectedPixelCount) {
        if (values == null || values.length != 23 || expectedPixelCount <= 0) {
            return NativeMotionFrameReadbackFingerprint.unavailable(
                    "Native persistent motion frame fingerprint returned invalid data");
        }
        if (values[0] != 1) {
            String label = switch ((int) values[0]) {
                case 2 -> "invalid request";
                case 3 -> "timeout";
                case 4 -> "missing session";
                case 5 -> "native exception";
                default -> "unavailable";
            };
            return NativeMotionFrameReadbackFingerprint.unavailable(
                    "Persistent motion frame readback " + label
                            + " requested=" + requestedSignal
                            + " completed=" + values[21]
                            + " lastSubmitted=" + values[22]);
        }
        long[] frameValues = new long[14];
        System.arraycopy(values, 0, frameValues, 0, 12);
        frameValues[12] = values[21];
        frameValues[13] = values[22];
        NativeFrameReadbackFingerprint frame = decodePersistentFrameFingerprint(
                frameValues, requestedSignal, expectedPixelCount);
        for (int index : new int[] {13, 14, 15}) {
            if (values[index] < 0 || values[index] > expectedPixelCount) {
                return NativeMotionFrameReadbackFingerprint.unavailable(
                        "Native persistent motion counts exceed dimensions");
            }
        }
        return NativeMotionFrameReadbackFingerprint.available(
                frame, values[12], (int) values[13], (int) values[14], (int) values[15],
                Float.intBitsToFloat((int) values[16]),
                Float.intBitsToFloat((int) values[17]),
                Float.intBitsToFloat((int) values[18]),
                Float.intBitsToFloat((int) values[19]),
                Float.intBitsToFloat((int) values[20]));
    }

    static NativeLiveDlssSessionInfo decodeLiveDlssSession(
            long[] values, int expectedOutputWidth, int expectedOutputHeight) {
        if (values == null || values.length != 15
                || values[3] != expectedOutputWidth || values[4] != expectedOutputHeight
                || values[1] <= 0 || values[2] <= 0
                || values[1] >= values[3] || values[2] >= values[4]) {
            int length = values == null ? -1 : values.length;
            String returned = values != null && values.length >= 5
                    ? values[1] + "x" + values[2] + "->" + values[3] + "x" + values[4]
                    : "unavailable";
            return NativeLiveDlssSessionInfo.unavailable(
                    "Native live DLSS session returned invalid data length=" + length
                            + " returned=" + returned
                            + " expectedOutput=" + expectedOutputWidth + "x" + expectedOutputHeight);
        }
        return NativeLiveDlssSessionInfo.available(
                values[0], (int) values[1], (int) values[2],
                (int) values[3], (int) values[4],
                new long[] {values[5], values[10]},
                new long[] {values[6], values[11]},
                new long[] {values[7], values[12]},
                new long[] {values[8], values[13]},
                new long[] {values[9], values[14]});
    }

    static NativeLiveDlssSessionInfo decodeLiveFsr3Session(
            long[] values, int expectedOutputWidth, int expectedOutputHeight) {
        if (values == null || values.length != 17
                || values[3] != expectedOutputWidth || values[4] != expectedOutputHeight) {
            return NativeLiveDlssSessionInfo.unavailable(
                    "Native live FSR3 session returned invalid data");
        }
        return NativeLiveDlssSessionInfo.availableWithGeneratedFrames(
                values[0], (int) values[1], (int) values[2],
                (int) values[3], (int) values[4],
                new long[] {values[5], values[11]},
                new long[] {values[6], values[12]},
                new long[] {values[7], values[13]},
                new long[] {values[8], values[14]},
                new long[] {values[9], values[15]},
                new long[] {values[10], values[16]});
    }

    static NativeLiveDlssFrameResult decodeLiveDlssFrame(long[] values) {
        if (values == null || values.length != 9) {
            return NativeLiveDlssFrameResult.failure(
                    "DECODE", "Native live DLSS frame returned invalid data", 0, 0);
        }
        String stage = liveDlssStageName((int) values[1]);
        boolean diagnosticReadback = values[8] != 0;
        if (values[0] != 1 && !("READY".equals(stage) && !diagnosticReadback)) {
            return NativeLiveDlssFrameResult.failure(
                    stage, "Live DLSS frame failed at " + stage, values[5], values[6]);
        }
        if (values[4] < 0 || values[4] > Integer.MAX_VALUE) {
            return NativeLiveDlssFrameResult.failure(
                    "VALIDATE", "Live DLSS output count is invalid", values[5], values[6]);
        }
        if (diagnosticReadback) {
            return NativeLiveDlssFrameResult.ready(
                    values[2], values[3] != 0, (int) values[4],
                    values[5], values[6], Double.longBitsToDouble(values[7]),
                    "Live DLSS output fingerprint ready");
        }
        return NativeLiveDlssFrameResult.fastCompleted(
                values[5], values[6], Double.longBitsToDouble(values[7]),
                "Live upscaler fast evaluation completed");
    }

    static float[] encodeLiveDlssConstants(NativeTemporalConstants constants) {
        if (constants == null) {
            throw new IllegalArgumentException("Live DLSS constants are required");
        }
        float[] values = new float[85];
        int offset = 0;
        for (float[] matrix : new float[][] {
                constants.cameraViewToClip(), constants.clipToCameraView(),
                constants.clipToPrevClip(), constants.prevClipToClip()}) {
            System.arraycopy(matrix, 0, values, offset, matrix.length);
            offset += matrix.length;
        }
        for (float[] vector : new float[][] {
                constants.cameraPos(), constants.cameraUp(),
                constants.cameraRight(), constants.cameraFwd()}) {
            System.arraycopy(vector, 0, values, offset, vector.length);
            offset += vector.length;
        }
        values[offset++] = constants.cameraNear();
        values[offset++] = constants.cameraFar();
        values[offset++] = constants.cameraFov();
        values[offset++] = constants.cameraAspectRatio();
        values[offset++] = constants.jitterX();
        values[offset++] = constants.jitterY();
        values[offset++] = constants.motionVectorScaleX();
        values[offset++] = constants.motionVectorScaleY();
        values[offset] = constants.reset() ? 1.0f : 0.0f;
        return values;
    }

    private static String liveDlssStageName(int stage) {
        return switch (stage) {
            case 1 -> "INITIALIZE";
            case 2 -> "DEVICE";
            case 3 -> "SUPPORT";
            case 4 -> "SETTINGS";
            case 5 -> "OPTIONS";
            case 6 -> "TOKEN";
            case 7 -> "CONSTANTS";
            case 8 -> "TAGS";
            case 9 -> "EVALUATE";
            case 10 -> "FREE_RESOURCES";
            case 11 -> "READY";
            default -> "UNAVAILABLE";
        };
    }

    @Override
    public void closeD3D12PersistentFrameSession(long sessionId) {
        if (!loaded || sessionId <= 0) {
            return;
        }
        try {
            nativeCloseD3D12PersistentFrameSession(sessionId);
        } catch (UnsatisfiedLinkError | RuntimeException ignored) {
        }
    }

    @Override
    public NativePersistentMotionFrameSessionInfo openD3D12PersistentMotionFrameSession(
            int width, int height) {
        if (!loaded) {
            return NativePersistentMotionFrameSessionInfo.unavailable(loadMessage);
        }
        try {
            long[] values = nativeOpenD3D12PersistentMotionFrameSession(width, height);
            if (values == null || values.length != 5) {
                return NativePersistentMotionFrameSessionInfo.unavailable(
                        "Native persistent motion frame session returned invalid data");
            }
            return NativePersistentMotionFrameSessionInfo.available(
                    values[0], values[1], values[2], values[3], values[4], width, height);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativePersistentMotionFrameSessionInfo.unavailable(error.getMessage());
        }
    }

    @Override
    public boolean submitD3D12PersistentMotionFrameReadback(
            long sessionId, long waitValue, long signalValue) {
        if (!loaded || sessionId <= 0 || waitValue <= 0 || signalValue <= 0) {
            return false;
        }
        try {
            return nativeSubmitD3D12PersistentMotionFrameReadback(
                    sessionId, waitValue, signalValue);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public NativeMotionFrameReadbackFingerprint inspectD3D12PersistentMotionFrameReadback(
            long sessionId, long signalValue, int expectedPixelCount) {
        if (!loaded || sessionId <= 0 || signalValue <= 0 || expectedPixelCount <= 0) {
            return NativeMotionFrameReadbackFingerprint.unavailable(
                    "Invalid persistent motion frame inspection values");
        }
        try {
            return decodePersistentMotionFrameFingerprint(
                    nativeInspectD3D12PersistentMotionFrameReadback(sessionId, signalValue),
                    signalValue, expectedPixelCount);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeMotionFrameReadbackFingerprint.unavailable(error.getMessage());
        }
    }

    @Override
    public void closeD3D12PersistentMotionFrameSession(long sessionId) {
        if (!loaded || sessionId <= 0) {
            return;
        }
        try {
            nativeCloseD3D12PersistentMotionFrameSession(sessionId);
        } catch (UnsatisfiedLinkError | RuntimeException ignored) {
        }
    }

    @Override
    public NativeLiveDlssSessionInfo openLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath) {
        return openLiveDlssSession(outputWidth, outputHeight, pluginPath, logPath,
                DlssQualityMode.QUALITY);
    }

    @Override
    public NativeLiveDlssSessionInfo openLiveFsr3Session(
            int outputWidth, int outputHeight, String loaderPath,
            DlssQualityMode qualityMode) {
        if (!loaded) return NativeLiveDlssSessionInfo.unavailable(loadMessage);
        try {
            return decodeLiveFsr3Session(nativeOpenLiveFsr3Session(
                    outputWidth, outputHeight, loaderPath,
                    qualityMode == null ? 0 : qualityMode.nativeCode()),
                    outputWidth, outputHeight);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeLiveDlssSessionInfo.unavailable(error.getMessage());
        }
    }

    @Override
    public NativeLiveDlssSessionInfo openLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath,
            DlssQualityMode qualityMode) {
        if (!loaded || outputWidth <= 0 || outputHeight <= 0) {
            return NativeLiveDlssSessionInfo.unavailable(loadMessage);
        }
        try {
            return decodeLiveDlssSession(nativeOpenLiveDlssSession(
                    outputWidth, outputHeight, pluginPath, logPath,
                    qualityMode == null ? DlssQualityMode.QUALITY.nativeCode()
                            : qualityMode.nativeCode()),
                    outputWidth, outputHeight);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeLiveDlssSessionInfo.unavailable(error.getMessage());
        }
    }

    @Override
    public boolean submitLiveDlssEvaluation(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants, NativeUpscalerExecutionMode mode) {
        if (!loaded || sessionId <= 0 || slotIndex < 0 || slotIndex > 1
                || waitValue <= 0 || signalValue <= 0
                || constants == null || mode == null) {
            return false;
        }
        try {
            return nativeSubmitLiveDlssEvaluation(
                    sessionId, slotIndex, waitValue, signalValue,
                    encodeLiveDlssConstants(constants), mode.code());
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public boolean submitLiveFsr3Upscale(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            NativeTemporalConstants constants, float frameTimeMilliseconds,
            boolean generateFrame) {
        if (!loaded || sessionId <= 0 || slotIndex < 0 || slotIndex > 1
                || waitValue <= 0 || signalValue <= 0 || constants == null
                || !Float.isFinite(frameTimeMilliseconds)
                || frameTimeMilliseconds <= 0.0F) return false;
        try {
            return nativeSubmitLiveFsr3Upscale(
                    sessionId, slotIndex, waitValue, signalValue,
                    encodeLiveDlssConstants(constants), frameTimeMilliseconds,
                    generateFrame);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public int presentLiveFsr3DxgiFrame(
            long sessionId, long windowHandle, int slotIndex, long signalValue,
            int width, int height) {
        if (!loaded || sessionId <= 0 || windowHandle == 0 ||
                slotIndex < 0 || slotIndex > 1 || signalValue <= 0 ||
                width <= 0 || height <= 0) return 0;
        try {
            return nativePresentLiveFsr3DxgiFrame(
                    sessionId, windowHandle, slotIndex, signalValue, width, height);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return 3;
        }
    }

    @Override
    public boolean isLiveUpscalerSlotReady(long sessionId, int slotIndex) {
        if (!loaded || sessionId <= 0 || slotIndex < 0 || slotIndex > 1) return false;
        try {
            return nativeIsLiveUpscalerSlotReady(sessionId, slotIndex);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return false;
        }
    }

    @Override
    public NativeLiveDlssFrameResult inspectLiveDlssEvaluation(
            long sessionId, int slotIndex, long signalValue) {
        if (!loaded || sessionId <= 0 || slotIndex < 0 || slotIndex > 1
                || signalValue <= 0) {
            return NativeLiveDlssFrameResult.failure(
                    "VALIDATE", "Invalid live DLSS inspection values", 0, 0);
        }
        try {
            return decodeLiveDlssFrame(
                    nativeInspectLiveDlssEvaluation(sessionId, slotIndex, signalValue));
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            return NativeLiveDlssFrameResult.failure(
                    "JNI", error.getMessage(), 0, 0);
        }
    }

    @Override
    public void closeLiveDlssSession(long sessionId) {
        if (!loaded || sessionId <= 0) return;
        try {
            nativeCloseLiveDlssSession(sessionId);
        } catch (UnsatisfiedLinkError | RuntimeException ignored) {
        }
    }

    @Override
    public void shutdownLiveDlssProcess() {
        if (!loaded) return;
        try {
            nativeShutdownLiveDlssProcess();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static native String nativeGetVersion();

    private static native String nativeProbeSystem();

    private static native String nativeGetD3D12AdapterLuid();

    private static native long[] nativeOpenD3D12InteropSession(int width, int height);

    private static native boolean nativeSubmitD3D12InteropReadback(long sessionId);

    private static native boolean nativeVerifyD3D12InteropReadback(long sessionId);

    private static native long[] nativeInspectD3D12InteropReadback(long sessionId);

    private static native void nativeCloseD3D12InteropSession(long sessionId);

    private static native long[] nativeOpenD3D12PersistentInteropSession(int width, int height);

    private static native boolean nativeSubmitD3D12PersistentReadback(
            long sessionId, long waitValue, long signalValue);

    private static native long[] nativeInspectD3D12PersistentReadback(
            long sessionId, long signalValue);

    private static native void nativeCloseD3D12PersistentInteropSession(long sessionId);

    private static native long[] nativeOpenD3D12PersistentFrameSession(
            int width, int height);

    private static native boolean nativeSubmitD3D12PersistentFrameReadback(
            long sessionId, long waitValue, long signalValue);

    private static native long[] nativeInspectD3D12PersistentFrameReadback(
            long sessionId, long signalValue);

    private static native void nativeCloseD3D12PersistentFrameSession(long sessionId);

    private static native long[] nativeOpenD3D12PersistentMotionFrameSession(
            int width, int height);

    private static native boolean nativeSubmitD3D12PersistentMotionFrameReadback(
            long sessionId, long waitValue, long signalValue);

    private static native long[] nativeInspectD3D12PersistentMotionFrameReadback(
            long sessionId, long signalValue);

    private static native void nativeCloseD3D12PersistentMotionFrameSession(long sessionId);

    private static native long[] nativeOpenLiveDlssSession(
            int outputWidth, int outputHeight, String pluginPath, String logPath,
            int qualityMode);

    private static native long[] nativeOpenLiveFsr3Session(
            int outputWidth, int outputHeight, String loaderPath,
            int qualityMode);

    private static native boolean nativeSubmitLiveDlssEvaluation(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            float[] constants, int executionMode);

    private static native boolean nativeSubmitLiveFsr3Upscale(
            long sessionId, int slotIndex, long waitValue, long signalValue,
            float[] constants, float frameTimeMilliseconds,
            boolean generateFrame);

    private static native int nativePresentLiveFsr3DxgiFrame(
            long sessionId, long windowHandle, int slotIndex, long signalValue,
            int width, int height);

    private static native boolean nativeIsLiveUpscalerSlotReady(
            long sessionId, int slotIndex);

    private static native long[] nativeInspectLiveDlssEvaluation(
            long sessionId, int slotIndex, long signalValue);

    private static native void nativeCloseLiveDlssSession(long sessionId);

    private static native void nativeShutdownLiveDlssProcess();
}
