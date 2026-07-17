package dev.mcdlss.core;

public record NativePersistentInteropSessionInfo(
        boolean available,
        long sessionId,
        long textureHandle,
        long fenceHandle,
        int width,
        int height,
        String message) {
    private static final int MAX_DIMENSION = 8192;

    public NativePersistentInteropSessionInfo {
        message = message == null ? "" : message.trim();
    }

    public static NativePersistentInteropSessionInfo available(
            long sessionId, long textureHandle, long fenceHandle,
            int width, int height) {
        if (sessionId <= 0 || textureHandle <= 0 || fenceHandle <= 0
                || width <= 0 || height <= 0
                || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return unavailable("Invalid persistent interop session");
        }
        return new NativePersistentInteropSessionInfo(
                true, sessionId, textureHandle, fenceHandle,
                width, height, "Persistent interop session ready");
    }

    public static NativePersistentInteropSessionInfo unavailable(String message) {
        return new NativePersistentInteropSessionInfo(
                false, 0, 0, 0, 0, 0, message);
    }
}
