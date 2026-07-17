package dev.mcdlss.fabric;

public record MinecraftInteropSemaphoreBarriers(
        int[] buffers,
        int[] textures,
        int[] layouts) {
    public static MinecraftInteropSemaphoreBarriers forTexture(int texture, int layout) {
        if (texture <= 0) {
            throw new IllegalArgumentException("Texture ID must be positive");
        }
        return new MinecraftInteropSemaphoreBarriers(
                new int[0],
                new int[] {texture},
                new int[] {layout});
    }
}
