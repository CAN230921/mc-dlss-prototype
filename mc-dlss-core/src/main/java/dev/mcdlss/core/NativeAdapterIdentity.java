package dev.mcdlss.core;

import java.util.Locale;

public record NativeAdapterIdentity(
        boolean available,
        String luid,
        String message) {
    private static final String LUID_PATTERN = "[0-9a-f]{16}";

    public NativeAdapterIdentity {
        luid = normalize(luid).toLowerCase(Locale.ROOT);
        message = normalize(message);
        if (available && !luid.matches(LUID_PATTERN)) {
            throw new IllegalArgumentException("Adapter LUID must contain 16 hexadecimal characters");
        }
        if (!available) {
            luid = "";
        }
    }

    public static NativeAdapterIdentity available(String luid) {
        return new NativeAdapterIdentity(true, luid, "");
    }

    public static NativeAdapterIdentity unavailable(String message) {
        return new NativeAdapterIdentity(false, "", message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
