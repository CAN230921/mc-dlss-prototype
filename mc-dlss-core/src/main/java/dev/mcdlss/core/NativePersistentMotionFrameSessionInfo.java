package dev.mcdlss.core;

public record NativePersistentMotionFrameSessionInfo(
        boolean available, long sessionId, long colorTextureHandle,
        long depthTextureHandle, long motionTextureHandle, long fenceHandle,
        int width, int height, String message) {
    public NativePersistentMotionFrameSessionInfo {
        message = message == null ? "" : message.trim();
    }

    public static NativePersistentMotionFrameSessionInfo available(
            long sessionId, long colorTextureHandle, long depthTextureHandle,
            long motionTextureHandle, long fenceHandle, int width, int height) {
        if (sessionId <= 0 || colorTextureHandle <= 0 || depthTextureHandle <= 0
                || motionTextureHandle <= 0 || fenceHandle <= 0
                || width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            return unavailable("Invalid persistent motion frame session");
        }
        return new NativePersistentMotionFrameSessionInfo(
                true, sessionId, colorTextureHandle, depthTextureHandle,
                motionTextureHandle, fenceHandle, width, height,
                "Persistent motion frame session ready");
    }

    public static NativePersistentMotionFrameSessionInfo unavailable(String message) {
        return new NativePersistentMotionFrameSessionInfo(
                false, 0, 0, 0, 0, 0, 0, 0, message);
    }
}
