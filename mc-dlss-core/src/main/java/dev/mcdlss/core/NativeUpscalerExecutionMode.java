package dev.mcdlss.core;

public enum NativeUpscalerExecutionMode {
    VALIDATING(0),
    FAST(1);

    private final int code;

    NativeUpscalerExecutionMode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
