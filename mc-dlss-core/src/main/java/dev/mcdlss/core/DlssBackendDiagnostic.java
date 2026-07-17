package dev.mcdlss.core;

public record DlssBackendDiagnostic(
        RenderBackendKind backend,
        DlssResourcePathStatus resourcePathStatus,
        boolean nativeDeviceAvailable,
        boolean nativeCommandContextAvailable,
        boolean nativeColorResourcesAvailable,
        boolean nativeDepthResourceAvailable,
        boolean motionVectorTextureAvailable,
        boolean uiCompositionAfterUpscalePossible,
        String message) {
    public DlssBackendDiagnostic {
        backend = backend == null ? RenderBackendKind.UNKNOWN : backend;
        resourcePathStatus = resourcePathStatus == null ? DlssResourcePathStatus.UNKNOWN : resourcePathStatus;
        message = normalize(message);
    }

    public static DlssBackendDiagnostic currentIrisSodiumOpenGlBlocked() {
        return new DlssBackendDiagnostic(
                RenderBackendKind.OPENGL,
                DlssResourcePathStatus.BLOCKED_UNSUPPORTED_BACKEND,
                false,
                false,
                false,
                false,
                false,
                true,
                "Current Iris/Sodium backend exposes OpenGL texture/framebuffer ids; "
                        + "Streamline DLSS SR requires D3D12 or Vulkan native resources and command context.");
    }

    public static DlssBackendDiagnostic ready(RenderBackendKind backend, String message) {
        return new DlssBackendDiagnostic(
                backend,
                DlssResourcePathStatus.READY,
                true,
                true,
                true,
                true,
                true,
                true,
                message);
    }

    public boolean dlssEvaluationReady() {
        return backend.supportsStreamlineDlss()
                && resourcePathStatus == DlssResourcePathStatus.READY
                && nativeDeviceAvailable
                && nativeCommandContextAvailable
                && nativeColorResourcesAvailable
                && nativeDepthResourceAvailable
                && motionVectorTextureAvailable
                && uiCompositionAfterUpscalePossible;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
