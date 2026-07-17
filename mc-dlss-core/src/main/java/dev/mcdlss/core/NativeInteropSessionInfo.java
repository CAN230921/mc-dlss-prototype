package dev.mcdlss.core;

public record NativeInteropSessionInfo(
        boolean available,
        long sessionId,
        long textureHandle,
        long fenceHandle,
        int width,
        int height,
        String message) {
    public NativeInteropSessionInfo {
        message = message == null ? "" : message;
    }

    public static NativeInteropSessionInfo available(
            long sessionId,
            long textureHandle,
            long fenceHandle,
            int width,
            int height) {
        if (sessionId <= 0L || textureHandle <= 0L || fenceHandle <= 0L || width != 64 || height != 64) {
            return unavailable("Invalid 64 x 64 D3D12 interop session");
        }
        return new NativeInteropSessionInfo(
                true, sessionId, textureHandle, fenceHandle, width, height, "Interop session ready");
    }

    public static NativeInteropSessionInfo unavailable(String message) {
        return new NativeInteropSessionInfo(false, 0L, 0L, 0L, 0, 0, message);
    }
}
