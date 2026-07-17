package dev.mcdlss.core;

public final class NativeLiveDlssSessionInfo {
    private final boolean available;
    private final long sessionId;
    private final int renderWidth;
    private final int renderHeight;
    private final int outputWidth;
    private final int outputHeight;
    private final long[] colorTextureHandles;
    private final long[] depthTextureHandles;
    private final long[] motionTextureHandles;
    private final long[] outputTextureHandles;
    private final long[] fenceHandles;
    private final long[] generatedTextureHandles;
    private final String message;

    private NativeLiveDlssSessionInfo(
            boolean available, long sessionId,
            int renderWidth, int renderHeight, int outputWidth, int outputHeight,
            long[] color, long[] depth, long[] motion, long[] output, long[] fences,
            long[] generated,
            String message) {
        this.available = available;
        this.sessionId = sessionId;
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.colorTextureHandles = color.clone();
        this.depthTextureHandles = depth.clone();
        this.motionTextureHandles = motion.clone();
        this.outputTextureHandles = output.clone();
        this.fenceHandles = fences.clone();
        this.generatedTextureHandles = generated.clone();
        this.message = message == null ? "" : message.trim();
    }

    public static NativeLiveDlssSessionInfo available(
            long sessionId, int renderWidth, int renderHeight,
            int outputWidth, int outputHeight,
            long[] color, long[] depth, long[] motion, long[] output, long[] fences) {
        if (sessionId <= 0 || renderWidth <= 0 || renderHeight <= 0
                || outputWidth <= renderWidth || outputHeight <= renderHeight
                || !validHandles(color) || !validHandles(depth) || !validHandles(motion)
                || !validHandles(output) || !validHandles(fences)) {
            return unavailable("Invalid live DLSS session");
        }
        return new NativeLiveDlssSessionInfo(
                true, sessionId, renderWidth, renderHeight, outputWidth, outputHeight,
                color, depth, motion, output, fences, new long[] {0, 0},
                "Live DLSS session ready");
    }

    public static NativeLiveDlssSessionInfo availableWithGeneratedFrames(
            long sessionId, int renderWidth, int renderHeight,
            int outputWidth, int outputHeight,
            long[] color, long[] depth, long[] motion, long[] output,
            long[] fences, long[] generated) {
        if (!validHandles(generated)) return unavailable("Invalid FSR3 generated handles");
        NativeLiveDlssSessionInfo base = available(
                sessionId, renderWidth, renderHeight, outputWidth, outputHeight,
                color, depth, motion, output, fences);
        if (!base.available()) return base;
        return new NativeLiveDlssSessionInfo(
                true, sessionId, renderWidth, renderHeight, outputWidth, outputHeight,
                color, depth, motion, output, fences, generated, "Live FSR3 session ready");
    }

    public static NativeLiveDlssSessionInfo unavailable(String message) {
        long[] empty = {0, 0};
        return new NativeLiveDlssSessionInfo(
                false, 0, 0, 0, 0, 0, empty, empty, empty, empty, empty, empty, message);
    }

    public boolean available() { return available; }
    public long sessionId() { return sessionId; }
    public int renderWidth() { return renderWidth; }
    public int renderHeight() { return renderHeight; }
    public int outputWidth() { return outputWidth; }
    public int outputHeight() { return outputHeight; }
    public long[] colorTextureHandles() { return colorTextureHandles.clone(); }
    public long[] depthTextureHandles() { return depthTextureHandles.clone(); }
    public long[] motionTextureHandles() { return motionTextureHandles.clone(); }
    public long[] outputTextureHandles() { return outputTextureHandles.clone(); }
    public long[] fenceHandles() { return fenceHandles.clone(); }
    public long[] generatedTextureHandles() { return generatedTextureHandles.clone(); }
    public String message() { return message; }

    private static boolean validHandles(long[] handles) {
        return handles != null && handles.length == 2 && handles[0] > 0 && handles[1] > 0;
    }
}
