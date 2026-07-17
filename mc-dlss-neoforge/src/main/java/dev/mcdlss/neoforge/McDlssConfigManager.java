package dev.mcdlss.neoforge;

import dev.mcdlss.core.DlssQualityMode;
import dev.mcdlss.core.DlssUserConfig;
import dev.mcdlss.core.UpscalerBackendMode;

import java.nio.file.Path;

public final class McDlssConfigManager {
    private static final Path FILE = Path.of("config", "mc-dlss.properties");
    private static volatile DlssUserConfig current = DlssUserConfig.load(FILE);
    private static volatile long revision;

    public static DlssUserConfig current() {
        return current;
    }

    public static long revision() {
        return revision;
    }

    public static synchronized void setEnabled(boolean enabled) {
        update(new DlssUserConfig(enabled, current.backend(), current.qualityMode(),
                current.fsr3FrameGeneration(), current.diagnosticValidation()));
    }

    public static synchronized void setQualityMode(DlssQualityMode qualityMode) {
        update(new DlssUserConfig(current.enabled(), current.backend(), qualityMode,
                current.fsr3FrameGeneration(), current.diagnosticValidation()));
    }

    public static synchronized void setBackend(UpscalerBackendMode backend) {
        update(new DlssUserConfig(current.enabled(), backend, current.qualityMode(),
                current.fsr3FrameGeneration(), current.diagnosticValidation()));
    }

    public static synchronized void setFsr3FrameGeneration(boolean enabled) {
        update(new DlssUserConfig(current.enabled(), current.backend(), current.qualityMode(),
                enabled, current.diagnosticValidation()));
    }

    public static synchronized void setDiagnosticValidation(boolean enabled) {
        update(new DlssUserConfig(current.enabled(), current.backend(), current.qualityMode(),
                current.fsr3FrameGeneration(), enabled));
    }

    private static void update(DlssUserConfig next) {
        if (next.equals(current)) return;
        try {
            next.save(FILE);
            current = next;
            revision++;
        } catch (Exception error) {
            throw new IllegalStateException("保存 MC DLSS 设置失败", error);
        }
    }

    private McDlssConfigManager() {
    }
}
