package dev.mcdlss.fabric;

public record PersistentFenceValues(long waitValue, long signalValue) {
    public static PersistentFenceValues forUse(int zeroBasedUse) {
        if (zeroBasedUse < 0) {
            throw new IllegalArgumentException("Slot use index must be non-negative");
        }
        long waitValue = Math.addExact(Math.multiplyExact((long) zeroBasedUse, 2L), 1L);
        return new PersistentFenceValues(waitValue, Math.addExact(waitValue, 1L));
    }
}
