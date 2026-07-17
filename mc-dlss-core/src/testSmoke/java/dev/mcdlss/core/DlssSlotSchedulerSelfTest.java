package dev.mcdlss.core;

public final class DlssSlotSchedulerSelfTest {
    public static void runAll() {
        require(DlssSlotScheduler.select(0, true, true) == 0,
                "Preferred ready slot was not selected");
        require(DlssSlotScheduler.select(0, false, true) == 1,
                "Ready alternate slot was not selected");
        require(DlssSlotScheduler.select(1, true, false) == 0,
                "Ready alternate slot was not selected from slot one");
        require(DlssSlotScheduler.select(1, false, false) == -1,
                "Busy slots must drop the DLSS frame");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private DlssSlotSchedulerSelfTest() {
    }
}
