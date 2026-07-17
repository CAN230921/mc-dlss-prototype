package dev.mcdlss.core;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NativePluginDirectoryResolverSelfTest {
    public static void runAll() throws Exception {
        Path root = Files.createTempDirectory("mc-dlss-native-directory-test");
        Path packaged = Files.createDirectories(root.resolve("packaged"));
        Path development = Files.createDirectories(root.resolve("development"));
        Path mainDll = Files.createFile(packaged.resolve("mc_dlss_native.dll"));

        Path selected = NativePluginDirectoryResolver.resolve(
                mainDll.toString(), development.toString());
        require(selected.equals(packaged), "Packaged bundle must take precedence");

        selected = NativePluginDirectoryResolver.resolve("", development.toString());
        require(selected.equals(development), "Development library path must remain supported");

        try {
            NativePluginDirectoryResolver.resolve(
                    root.resolve("missing.dll").toString(), root.resolve("missing").toString());
            throw new AssertionError("Invalid native paths were accepted");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("找不到"),
                    "Failure must provide a Chinese diagnostic");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private NativePluginDirectoryResolverSelfTest() {
    }
}
