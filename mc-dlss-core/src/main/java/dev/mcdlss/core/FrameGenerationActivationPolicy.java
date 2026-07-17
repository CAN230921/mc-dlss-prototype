package dev.mcdlss.core;

public final class FrameGenerationActivationPolicy {
    public enum Reason {
        ACTIVE,
        NOT_REQUESTED,
        APPLICATION_ID_REQUIRED,
        NO_WORLD,
        SCREEN_OPEN,
        OVERLAY_OPEN,
        HUD_HIDDEN
    }

    public record Decision(boolean active, Reason reason) {
    }

    public static Decision decide(
            boolean requested,
            boolean applicationIdAvailable,
            boolean worldLoaded,
            boolean screenOpen,
            boolean overlayOpen,
            boolean hudHidden) {
        if (!requested) return inactive(Reason.NOT_REQUESTED);
        if (!applicationIdAvailable) return inactive(Reason.APPLICATION_ID_REQUIRED);
        if (!worldLoaded) return inactive(Reason.NO_WORLD);
        if (screenOpen) return inactive(Reason.SCREEN_OPEN);
        if (overlayOpen) return inactive(Reason.OVERLAY_OPEN);
        if (hudHidden) return inactive(Reason.HUD_HIDDEN);
        return new Decision(true, Reason.ACTIVE);
    }

    private static Decision inactive(Reason reason) {
        return new Decision(false, reason);
    }

    private FrameGenerationActivationPolicy() {
    }
}
