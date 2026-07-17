package dev.mcdlss.fabric;

public final class LiveDlssFramebufferRedirectorSelfTest {
    public static void main(String[] args) {
        LiveDlssFramebufferRedirector redirector = new LiveDlssFramebufferRedirector();
        redirector.begin("full", "low");
        if (!redirector.active() || !"full".equals(redirector.originalFramebuffer())
                || !"low".equals(redirector.lowResolutionFramebuffer())) {
            throw new AssertionError("Framebuffer redirection state mismatch");
        }
        expectFailure(() -> redirector.begin("other", "low"));
        if (!"full".equals(redirector.restore()) || redirector.active()
                || redirector.restore() != null) {
            throw new AssertionError("Framebuffer restoration is not idempotent");
        }
        redirector.markResize();
        if (!redirector.consumeResetRequired() || redirector.consumeResetRequired()) {
            throw new AssertionError("Resize reset was not consumed exactly once");
        }
        try {
            redirector.runRedirected("full", "low", () -> {
                throw new IllegalStateException("injected");
            });
        } catch (IllegalStateException expected) {
            if (redirector.active()) {
                throw new AssertionError("Exception did not restore framebuffer state");
            }
        }
        System.out.println("LiveDlssFramebufferRedirectorSelfTest passed");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Nested redirection was accepted");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private LiveDlssFramebufferRedirectorSelfTest() {
    }
}
