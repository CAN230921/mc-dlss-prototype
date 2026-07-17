package dev.mcdlss.core;

public final class DlssSlotScheduler {
    public static int select(int preferred, boolean slot0Ready, boolean slot1Ready) {
        if (preferred < 0 || preferred > 1) {
            throw new IllegalArgumentException("Preferred slot must be zero or one");
        }
        if (preferred == 0) {
            if (slot0Ready) return 0;
            return slot1Ready ? 1 : -1;
        }
        if (slot1Ready) return 1;
        return slot0Ready ? 0 : -1;
    }

    private DlssSlotScheduler() {
    }
}
