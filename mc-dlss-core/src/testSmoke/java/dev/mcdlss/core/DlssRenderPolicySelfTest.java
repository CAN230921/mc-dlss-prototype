package dev.mcdlss.core;

public final class DlssRenderPolicySelfTest {
    public static void runAll() {
        DlssRenderPolicy production = DlssRenderPolicy.from(
                new DlssUserConfig(true, DlssQualityMode.QUALITY, false));
        require(production.executionMode() == NativeUpscalerExecutionMode.FAST,
                "Production mode must use FAST evaluation");
        require(!production.colorBuffersHdr() && !production.autoExposure(),
                "Post-tonemap Iris input must use LDR without auto exposure");
        require(!production.diagnosticReadback(),
                "Production mode must not request diagnostic readback");

        DlssRenderPolicy diagnostic = DlssRenderPolicy.from(
                new DlssUserConfig(true, DlssQualityMode.QUALITY, true));
        require(diagnostic.executionMode() == NativeUpscalerExecutionMode.VALIDATING,
                "Diagnostic mode must use validating evaluation");
        require(diagnostic.diagnosticReadback(),
                "Diagnostic mode must request readback");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private DlssRenderPolicySelfTest() {
    }
}
