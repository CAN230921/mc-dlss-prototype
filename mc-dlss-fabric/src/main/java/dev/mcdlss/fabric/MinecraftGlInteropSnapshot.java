package dev.mcdlss.fabric;

import java.util.Locale;

public record MinecraftGlInteropSnapshot(
        boolean probed,
        String openGlVendor,
        String openGlRenderer,
        String openGlVersion,
        boolean memoryObjectExtension,
        boolean memoryObjectWin32Extension,
        boolean semaphoreExtension,
        boolean semaphoreWin32Extension,
        String openGlLuid,
        String d3d12Luid,
        String message) {
    private static final String LUID_PATTERN = "[0-9a-f]{16}";

    public MinecraftGlInteropSnapshot {
        openGlVendor = normalize(openGlVendor);
        openGlRenderer = normalize(openGlRenderer);
        openGlVersion = normalize(openGlVersion);
        openGlLuid = normalize(openGlLuid).toLowerCase(Locale.ROOT);
        d3d12Luid = normalize(d3d12Luid).toLowerCase(Locale.ROOT);
        message = normalize(message);
    }

    public static MinecraftGlInteropSnapshot failure(String message) {
        return new MinecraftGlInteropSnapshot(
                true, "", "", "",
                false, false, false, false,
                "", "", message);
    }

    public boolean adapterLuidMatched() {
        return openGlLuid.matches(LUID_PATTERN)
                && d3d12Luid.matches(LUID_PATTERN)
                && openGlLuid.equals(d3d12Luid);
    }

    public boolean interopPrerequisitesReady() {
        return probed
                && memoryObjectExtension
                && memoryObjectWin32Extension
                && semaphoreExtension
                && semaphoreWin32Extension
                && adapterLuidMatched();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
