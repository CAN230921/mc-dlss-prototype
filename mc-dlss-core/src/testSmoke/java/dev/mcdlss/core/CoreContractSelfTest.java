package dev.mcdlss.core;

public final class CoreContractSelfTest {
    public static void main(String[] args) throws Exception {
        requiresQualityModes();
        requiresProbeResultFields();
        requiresOpenGlBackendDiagnostic();
        IrisRenderTargetContractSelfTest.runAll();
        NativePluginDirectoryResolverSelfTest.runAll();
        DlssUserConfigSelfTest.runAll();
        NativeLiveDlssBridgeSelfTest.main(new String[0]);
        DlssRenderPolicySelfTest.runAll();
        DlssSlotSchedulerSelfTest.runAll();
        DlssInternalResolutionSelfTest.runAll();
        FrameTimeStatisticsSelfTest.runAll();
        StableCandidateGateSelfTest.runAll();
        TemporalDiscontinuitySelfTest.runAll();
        FrameGenerationActivationPolicySelfTest.runAll();
        System.out.println("CoreContractSelfTest passed");
    }

    private static void requiresQualityModes() {
        if (DlssQualityMode.values().length != 5) {
            throw new AssertionError("Expected five DLSS quality modes");
        }
        if (DlssQualityMode.valueOf("QUALITY") != DlssQualityMode.QUALITY) {
            throw new AssertionError("QUALITY mode missing");
        }
    }

    private static void requiresProbeResultFields() {
        NativeProbeResult result = NativeProbeResult.available("0.1.0", "probe ok");
        if (!result.available()) {
            throw new AssertionError("Expected available probe result");
        }
        if (!"0.1.0".equals(result.version())) {
            throw new AssertionError("Version was not preserved");
        }
        if (!"probe ok".equals(result.message())) {
            throw new AssertionError("Message was not preserved");
        }
    }

    private static void requiresOpenGlBackendDiagnostic() {
        DlssBackendDiagnostic diagnostic = DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked();
        if (diagnostic.backend() != RenderBackendKind.OPENGL) {
            throw new AssertionError("Expected current Iris/Sodium backend to report OpenGL");
        }
        if (diagnostic.resourcePathStatus() != DlssResourcePathStatus.BLOCKED_UNSUPPORTED_BACKEND) {
            throw new AssertionError("Expected OpenGL resource path to be blocked");
        }
        if (diagnostic.dlssEvaluationReady()) {
            throw new AssertionError("OpenGL diagnostic must not report DLSS evaluation ready");
        }
        if (diagnostic.nativeDeviceAvailable()) {
            throw new AssertionError("OpenGL diagnostic must not report a Streamline native device");
        }
        if (diagnostic.nativeCommandContextAvailable()) {
            throw new AssertionError("OpenGL diagnostic must not report a Streamline command context");
        }
        if (diagnostic.nativeColorResourcesAvailable()) {
            throw new AssertionError("OpenGL diagnostic must not report native color resources");
        }
        if (diagnostic.nativeDepthResourceAvailable()) {
            throw new AssertionError("OpenGL diagnostic must not report a native depth resource");
        }
        if (diagnostic.motionVectorTextureAvailable()) {
            throw new AssertionError("OpenGL diagnostic must not report a motion-vector texture");
        }
        if (!diagnostic.uiCompositionAfterUpscalePossible()) {
            throw new AssertionError("The conceptual UI-after-upscale timing should remain visible");
        }
        if (!diagnostic.message().contains("OpenGL") || !diagnostic.message().contains("D3D12 or Vulkan")) {
            throw new AssertionError("Diagnostic message must explain the backend mismatch");
        }
        if (RenderBackendKind.OPENGL.supportsStreamlineDlss()) {
            throw new AssertionError("OpenGL must not be marked Streamline-compatible");
        }
        if (!RenderBackendKind.D3D12.supportsStreamlineDlss()) {
            throw new AssertionError("D3D12 should be marked Streamline-compatible");
        }
        if (!RenderBackendKind.VULKAN.supportsStreamlineDlss()) {
            throw new AssertionError("Vulkan should be marked Streamline-compatible");
        }
    }

    private CoreContractSelfTest() {
    }
}
