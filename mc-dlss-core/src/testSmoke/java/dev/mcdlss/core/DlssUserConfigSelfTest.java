package dev.mcdlss.core;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DlssUserConfigSelfTest {
    public static void runAll() throws Exception {
        Path file = Files.createTempDirectory("mc-dlss-config-test").resolve("mc-dlss.properties");
        DlssUserConfig defaults = DlssUserConfig.load(file);
        require(!defaults.enabled(), "DLSS must default disabled");
        require(defaults.qualityMode() == DlssQualityMode.QUALITY,
                "DLSS must default to quality mode");

        new DlssUserConfig(true, DlssQualityMode.BALANCED).save(file);
        DlssUserConfig loaded = DlssUserConfig.load(file);
        require(loaded.enabled() && loaded.qualityMode() == DlssQualityMode.BALANCED,
                "DLSS config round trip failed");

        require("质量".equals(DlssQualityMode.QUALITY.chineseName()),
                "Quality label must be Chinese");
        require("超级性能".equals(DlssQualityMode.ULTRA_PERFORMANCE.chineseName()),
                "Ultra performance label must be Chinese");
        require(DlssQualityMode.QUALITY.nativeCode() == 0
                        && DlssQualityMode.BALANCED.nativeCode() == 1
                        && DlssQualityMode.PERFORMANCE.nativeCode() == 2
                        && DlssQualityMode.ULTRA_PERFORMANCE.nativeCode() == 3,
                "DLSS native quality codes changed");

        DlssRetryGate<String> gate = new DlssRetryGate<>(10_000L);
        require(gate.canAttempt("same", 1_000L), "New key must be allowed");
        gate.recordFailure("same", 1_000L);
        require(!gate.canAttempt("same", 10_999L), "Same key retried before cooldown");
        require(gate.canAttempt("same", 11_000L), "Cooldown did not expire");
        require(gate.canAttempt("changed", 1_001L), "Changed key must bypass cooldown");
        gate.clear();
        require(gate.canAttempt("same", 1_001L), "Clear must release cooldown");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private DlssUserConfigSelfTest() {
    }
}
