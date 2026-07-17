package dev.mcdlss.fabric;

public record MinecraftLiveInteropSnapshot(
        boolean attempted,
        boolean sessionCreated,
        boolean memoryImported,
        boolean semaphoreImported,
        boolean openGlWriteSubmitted,
        boolean d3d12ReadbackSubmitted,
        boolean openGlWaitCompleted,
        boolean readbackMatched,
        boolean resourcesReleased,
        String message) {
    public MinecraftLiveInteropSnapshot {
        message = message == null ? "" : message.trim();
    }

    public static MinecraftLiveInteropSnapshot failure(String message) {
        return new MinecraftLiveInteropSnapshot(
                true, false, false, false, false,
                false, false, false, false, message);
    }

    public boolean success() {
        return attempted
                && sessionCreated
                && memoryImported
                && semaphoreImported
                && openGlWriteSubmitted
                && d3d12ReadbackSubmitted
                && openGlWaitCompleted
                && readbackMatched
                && resourcesReleased;
    }
}
