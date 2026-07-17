package dev.mcdlss.core;

public record NativePersistentFrameSessionInfo(
        boolean available,
        long sessionId,
        long colorTextureHandle,
        long depthTextureHandle,
        long fenceHandle,
        int width,
        int height,
        String message) {
    public NativePersistentFrameSessionInfo {
        message = message == null ? "" : message.trim();
    }

    public static NativePersistentFrameSessionInfo available(
            long sessionId,
            long colorTextureHandle,
            long depthTextureHandle,
            long fenceHandle,
            int width,
            int height) {
        if (sessionId <= 0 || colorTextureHandle <= 0 || depthTextureHandle <= 0
                || fenceHandle <= 0 || width <= 0 || height <= 0
                || width > 8192 || height > 8192) {
            return unavailable("Invalid persistent frame session");
        }
        return new NativePersistentFrameSessionInfo(
                true, sessionId, colorTextureHandle, depthTextureHandle,
                fenceHandle, width, height, "Persistent frame session ready");
    }

    public static NativePersistentFrameSessionInfo unavailable(String message) {
        return new NativePersistentFrameSessionInfo(
                false, 0, 0, 0, 0, 0, 0, message);
    }
}
