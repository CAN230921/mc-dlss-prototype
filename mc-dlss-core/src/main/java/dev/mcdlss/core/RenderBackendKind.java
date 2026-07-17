package dev.mcdlss.core;

public enum RenderBackendKind {
    UNKNOWN(false),
    OPENGL(false),
    D3D12(true),
    VULKAN(true);

    private final boolean streamlineDlssSupported;

    RenderBackendKind(boolean streamlineDlssSupported) {
        this.streamlineDlssSupported = streamlineDlssSupported;
    }

    public boolean supportsStreamlineDlss() {
        return streamlineDlssSupported;
    }
}
