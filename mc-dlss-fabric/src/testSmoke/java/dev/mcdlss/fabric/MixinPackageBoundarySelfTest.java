package dev.mcdlss.fabric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class MixinPackageBoundarySelfTest {
    public static void main(String[] args) throws Exception {
        String mixins = Files.readString(Path.of(
                "mc-dlss-fabric", "src", "main", "resources", "mc_dlss.mixins.json"));
        String fabric = Files.readString(Path.of(
                "mc-dlss-fabric", "src", "main", "resources", "fabric.mod.json"));
        String mixinPackage = value(mixins, "package");
        var entrypointMatcher = Pattern.compile(
                "dev\\.mcdlss\\.fabric\\.[A-Za-z0-9_$]+")
                .matcher(fabric);
        int checked = 0;
        while (entrypointMatcher.find()) {
            String entrypoint = entrypointMatcher.group();
            if (entrypoint.startsWith(mixinPackage + ".")) {
                throw new AssertionError(
                        "Entrypoint is inside the reserved mixin package: " + entrypoint);
            }
            checked++;
        }
        if (checked < 2 || !mixinPackage.endsWith(".mixin")) {
            throw new AssertionError("Mixin package boundary is not narrowly scoped");
        }
        System.out.println("MixinPackageBoundarySelfTest passed");
    }

    private static String value(String json, String key) {
        var matcher = Pattern.compile(
                "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing JSON key: " + key);
        return matcher.group(1);
    }

    private MixinPackageBoundarySelfTest() {
    }
}
