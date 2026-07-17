package dev.mcdlss.core;

public record DlssRenderPolicy(
        NativeUpscalerExecutionMode executionMode,
        boolean colorBuffersHdr,
        boolean autoExposure,
        boolean diagnosticReadback) {
    public static DlssRenderPolicy from(DlssUserConfig config) {
        boolean diagnostic = config != null && config.diagnosticValidation();
        return new DlssRenderPolicy(
                diagnostic ? NativeUpscalerExecutionMode.VALIDATING
                        : NativeUpscalerExecutionMode.FAST,
                false,
                false,
                diagnostic);
    }
}
