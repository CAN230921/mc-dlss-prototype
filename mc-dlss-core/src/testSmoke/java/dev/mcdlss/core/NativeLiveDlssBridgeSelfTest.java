package dev.mcdlss.core;

public final class NativeLiveDlssBridgeSelfTest {
    public static void main(String[] args) {
        long[] sessionValues = {
            7, 569, 320, 854, 480,
            11, 12, 13, 14, 15,
            21, 22, 23, 24, 25
        };
        NativeLiveDlssSessionInfo session =
                NativeLibraryBridge.decodeLiveDlssSession(sessionValues, 854, 480);
        if (!session.available() || session.renderWidth() != 569
                || session.outputTextureHandles()[1] != 24) {
            throw new AssertionError("Live DLSS session decode mismatch");
        }
        long[] fsr3Values = {
            8, 569, 320, 854, 480,
            31, 32, 33, 34, 35, 36,
            41, 42, 43, 44, 45, 46
        };
        NativeLiveDlssSessionInfo fsr3 =
                NativeLibraryBridge.decodeLiveFsr3Session(fsr3Values, 854, 480);
        if (!fsr3.available() || fsr3.generatedTextureHandles()[0] != 36
                || fsr3.generatedTextureHandles()[1] != 46) {
            throw new AssertionError("Live FSR3 generated handle decode mismatch");
        }
        if (NativeLibraryBridge.decodeLiveFsr3Session(new long[15], 854, 480)
                .available()) {
            throw new AssertionError("DLSS ABI was accepted as FSR3 ABI");
        }
        NativeLiveDlssSessionInfo malformed = NativeLibraryBridge.decodeLiveDlssSession(
                new long[14], 854, 480);
        if (malformed.available()) {
            throw new AssertionError("Malformed live DLSS session was accepted");
        }
        if (!malformed.message().contains("length=14")
                || !malformed.message().contains("expectedOutput=854x480")) {
            throw new AssertionError("Malformed session diagnostic lacks returned shape");
        }

        long[] frameValues = {
            1, 11, 0x1234, 1, 300, 2, 2,
            Double.doubleToRawLongBits(4.25), 1
        };
        NativeLiveDlssFrameResult frame =
                NativeLibraryBridge.decodeLiveDlssFrame(frameValues);
        if (!frame.available() || !frame.completed()
                || !frame.diagnosticReadbackRequested()
                || frame.outputHash() != 0x1234
                || frame.evaluationMilliseconds() != 4.25) {
            throw new AssertionError("Live DLSS frame decode mismatch");
        }
        long[] fastValues = {
            0, 11, 0, 0, 0, 4, 4,
            Double.doubleToRawLongBits(1.5), 0
        };
        NativeLiveDlssFrameResult fast =
                NativeLibraryBridge.decodeLiveDlssFrame(fastValues);
        if (!fast.completed() || fast.available()
                || fast.diagnosticReadbackRequested()
                || fast.completedFenceValue() != 4) {
            throw new AssertionError("Fast live upscaler result decode mismatch");
        }
        frameValues[0] = 0;
        frameValues[1] = 9;
        if (NativeLibraryBridge.decodeLiveDlssFrame(frameValues).available()) {
            throw new AssertionError("Failed live DLSS frame was accepted");
        }

        float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        NativeTemporalConstants constants = new NativeTemporalConstants(
                identity, identity, identity, identity,
                new float[3], new float[] {0, 1, 0}, new float[] {1, 0, 0},
                new float[] {0, 0, -1}, 0.1f, 1000, 1.0f, 1.778f,
                0, 0, 1.0f / 569, 1.0f / 320, true);
        float[] encoded = NativeLibraryBridge.encodeLiveDlssConstants(constants);
        if (encoded.length != 85 || encoded[0] != 1.0f
                || encoded[84] != 1.0f) {
            throw new AssertionError("Live DLSS constants encoding mismatch");
        }

        int[] capturedMode = {-1};
        NativeBridge bridge = new NativeBridge() {
            @Override
            public NativeProbeResult probeSystem() { return null; }

            @Override
            public boolean submitLiveDlssEvaluation(
                    long sessionId, int slotIndex, long waitValue, long signalValue,
                    NativeTemporalConstants submitted,
                    NativeUpscalerExecutionMode mode) {
                capturedMode[0] = mode.code();
                return mode == NativeUpscalerExecutionMode.FAST;
            }

            @Override
            public boolean isLiveUpscalerSlotReady(long sessionId, int slotIndex) {
                return sessionId == 7 && slotIndex == 1;
            }
        };
        if (!bridge.submitLiveDlssEvaluation(
                7, 1, 3, 4, constants, NativeUpscalerExecutionMode.FAST)
                || capturedMode[0] != 1 || !bridge.isLiveUpscalerSlotReady(7, 1)) {
            throw new AssertionError("Fast bridge forwarding mismatch");
        }
        bridge.submitLiveDlssEvaluation(7, 0, 1, 2, constants);
        if (capturedMode[0] != 0) {
            throw new AssertionError("Legacy submission did not default to validation");
        }
        System.out.println("NativeLiveDlssBridgeSelfTest passed");
    }

    private NativeLiveDlssBridgeSelfTest() {
    }
}
