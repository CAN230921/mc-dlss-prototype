package dev.mcdlss.core;

import java.util.Locale;

public enum NativeBundlePlatform {
    WINDOWS_X86_64,
    UNSUPPORTED;

    public static NativeBundlePlatform current() {
        return select(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    public static NativeBundlePlatform select(String osName, String architecture) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
        return os.startsWith("windows") && (arch.equals("amd64") || arch.equals("x86_64"))
                ? WINDOWS_X86_64 : UNSUPPORTED;
    }
}
