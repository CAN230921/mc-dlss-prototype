package dev.mcdlss.fabric;

public final class LiveUpscalerSlotScheduler {
    public static int select(boolean slot0Ready, boolean slot1Ready, int preferred) {
        if (preferred < 0 || preferred > 1) {
            throw new IllegalArgumentException("Preferred slot must be 0 or 1");
        }
        boolean preferredReady = preferred == 0 ? slot0Ready : slot1Ready;
        if (preferredReady) return preferred;
        boolean alternateReady = preferred == 0 ? slot1Ready : slot0Ready;
        return alternateReady ? 1 - preferred : -1;
    }

    private LiveUpscalerSlotScheduler() {
    }
}
