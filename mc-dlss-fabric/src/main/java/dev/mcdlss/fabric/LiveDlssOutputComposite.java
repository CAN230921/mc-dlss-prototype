package dev.mcdlss.fabric;

import java.util.List;

public final class LiveDlssOutputComposite {
    public enum Operation {
        WAIT,
        RESTORE_MAIN,
        BLIT,
        FINISH,
        READ_PIXELS
    }

    public static void validateExactOutput(
            int sharedWidth, int sharedHeight, int targetWidth, int targetHeight) {
        if (sharedWidth <= 0 || sharedHeight <= 0
                || sharedWidth != targetWidth || sharedHeight != targetHeight) {
            throw new IllegalArgumentException(
                    "Live DLSS output must exactly match the target framebuffer");
        }
    }

    public static void validateFastOperations(List<Operation> operations) {
        if (!operations.equals(List.of(Operation.WAIT, Operation.RESTORE_MAIN, Operation.BLIT))) {
            throw new IllegalArgumentException("Fast presentation operations are invalid");
        }
    }

    private LiveDlssOutputComposite() {
    }
}
