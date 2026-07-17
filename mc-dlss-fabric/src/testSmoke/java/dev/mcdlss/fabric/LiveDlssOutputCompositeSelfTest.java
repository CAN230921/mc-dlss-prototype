package dev.mcdlss.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class LiveDlssOutputCompositeSelfTest {
    public static void main(String[] args) {
        ByteBuffer pixels = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        put(pixels, 0x3c00, 0x3800, 0x3400, 0x3c00);
        put(pixels, 0x3800, 0x3800, 0x3400, 0x3c00);
        Fp16ColorFingerprint result = Fp16ColorFingerprint.fromRgba16f(pixels.array());
        if (!result.validForOutput() || !result.nonUniform()
                || result.nonBlackPixelCount() != 2 || result.finiteChannelCount() != 8) {
            throw new AssertionError("FP16 output fingerprint mismatch");
        }
        ByteBuffer invalid = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        put(invalid, 0x7c00, 0x3800, 0x3400, 0x3c00);
        if (Fp16ColorFingerprint.fromRgba16f(invalid.array()).validForOutput()) {
            throw new AssertionError("Infinite FP16 output was accepted");
        }
        LiveDlssOutputComposite.validateExactOutput(854, 480, 854, 480);
        expectFailure(() -> LiveDlssOutputComposite.validateExactOutput(854, 480, 1280, 720));
        LiveDlssOutputComposite.validateFastOperations(List.of(
                LiveDlssOutputComposite.Operation.WAIT,
                LiveDlssOutputComposite.Operation.RESTORE_MAIN,
                LiveDlssOutputComposite.Operation.BLIT));
        expectFailure(() -> LiveDlssOutputComposite.validateFastOperations(List.of(
                LiveDlssOutputComposite.Operation.WAIT,
                LiveDlssOutputComposite.Operation.FINISH,
                LiveDlssOutputComposite.Operation.BLIT)));
        expectFailure(() -> LiveDlssOutputComposite.validateFastOperations(List.of(
                LiveDlssOutputComposite.Operation.WAIT,
                LiveDlssOutputComposite.Operation.READ_PIXELS,
                LiveDlssOutputComposite.Operation.BLIT)));
        System.out.println("LiveDlssOutputCompositeSelfTest passed");
    }

    private static void put(ByteBuffer target, int r, int g, int b, int a) {
        target.putShort((short) r).putShort((short) g)
                .putShort((short) b).putShort((short) a);
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Mismatched output dimensions were accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private LiveDlssOutputCompositeSelfTest() {
    }
}
