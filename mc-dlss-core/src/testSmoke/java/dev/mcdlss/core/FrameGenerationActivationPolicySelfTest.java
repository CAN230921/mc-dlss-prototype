package dev.mcdlss.core;

public final class FrameGenerationActivationPolicySelfTest {
    public static void runAll() {
        require(false, true, true, false, false, false, false,
                FrameGenerationActivationPolicy.Reason.NOT_REQUESTED);
        require(true, true, true, false, false, false, true,
                FrameGenerationActivationPolicy.Reason.ACTIVE);
        require(true, false, true, false, false, false, false,
                FrameGenerationActivationPolicy.Reason.APPLICATION_ID_REQUIRED);
        require(true, true, true, true, false, false, false,
                FrameGenerationActivationPolicy.Reason.SCREEN_OPEN);
        require(true, true, true, false, true, false, false,
                FrameGenerationActivationPolicy.Reason.OVERLAY_OPEN);
        require(true, true, true, false, false, true, false,
                FrameGenerationActivationPolicy.Reason.HUD_HIDDEN);
        require(true, true, false, false, false, false, false,
                FrameGenerationActivationPolicy.Reason.NO_WORLD);
    }

    private static void require(
            boolean requested,
            boolean applicationIdAvailable,
            boolean worldLoaded,
            boolean screenOpen,
            boolean overlayOpen,
            boolean hudHidden,
            boolean expectedActive,
            FrameGenerationActivationPolicy.Reason expectedReason) {
        var decision = FrameGenerationActivationPolicy.decide(
                requested, applicationIdAvailable, worldLoaded,
                screenOpen, overlayOpen, hudHidden);
        if (decision.active() != expectedActive || decision.reason() != expectedReason) {
            throw new AssertionError("Unexpected frame-generation decision: " + decision);
        }
    }

    private FrameGenerationActivationPolicySelfTest() {
    }
}
