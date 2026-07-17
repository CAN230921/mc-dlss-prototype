package dev.mcdlss.core;

public enum NativeUpscalerBackend {
    STREAMLINE_DLSS(0),
    INTEL_XESS(1),
    AMD_FSR3(2);

    private final int code;

    NativeUpscalerBackend(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
