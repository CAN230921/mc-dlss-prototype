package dev.mcdlss.fabric;

public final class LiveDlssFramebufferRedirector {
    private Object originalFramebuffer;
    private Object lowResolutionFramebuffer;
    private boolean resetRequired;

    public void begin(Object originalFramebuffer, Object lowResolutionFramebuffer) {
        if (active()) {
            throw new IllegalStateException("Framebuffer redirection is already active");
        }
        if (originalFramebuffer == null || lowResolutionFramebuffer == null
                || originalFramebuffer == lowResolutionFramebuffer) {
            throw new IllegalArgumentException("Distinct framebuffers are required");
        }
        this.originalFramebuffer = originalFramebuffer;
        this.lowResolutionFramebuffer = lowResolutionFramebuffer;
    }

    public Object restore() {
        Object restored = originalFramebuffer;
        originalFramebuffer = null;
        lowResolutionFramebuffer = null;
        return restored;
    }

    public void runRedirected(
            Object originalFramebuffer, Object lowResolutionFramebuffer, Runnable action) {
        begin(originalFramebuffer, lowResolutionFramebuffer);
        try {
            action.run();
        } finally {
            restore();
        }
    }

    public boolean active() {
        return originalFramebuffer != null;
    }

    public Object originalFramebuffer() {
        return originalFramebuffer;
    }

    public Object lowResolutionFramebuffer() {
        return lowResolutionFramebuffer;
    }

    public void markResize() {
        resetRequired = true;
    }

    public boolean consumeResetRequired() {
        boolean result = resetRequired;
        resetRequired = false;
        return result;
    }
}
