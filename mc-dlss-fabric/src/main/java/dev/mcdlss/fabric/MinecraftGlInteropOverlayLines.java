package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftGlInteropOverlayLines {
    private static final int MAX_VALUE_LENGTH = 72;

    public static List<String> fromSnapshot(MinecraftGlInteropSnapshot snapshot) {
        MinecraftGlInteropSnapshot safeSnapshot = snapshot == null
                ? MinecraftGlInteropSnapshot.failure("Live OpenGL probe has not run")
                : snapshot;
        String luid = safeSnapshot.openGlLuid().isEmpty()
                ? "unavailable"
                : safeSnapshot.openGlLuid();
        return List.of(
                "gl-live: " + truncate(safeSnapshot.openGlRenderer()),
                "interop-ready: " + safeSnapshot.interopPrerequisitesReady()
                        + " luid=" + luid,
                "interop: " + truncate(safeSnapshot.message()));
    }

    private static String truncate(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.length() <= MAX_VALUE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }

    private MinecraftGlInteropOverlayLines() {
    }
}
