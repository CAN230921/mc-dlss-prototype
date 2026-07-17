package dev.mcdlss.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public record DlssUserConfig(
        boolean enabled, UpscalerBackendMode backend,
        DlssQualityMode qualityMode, boolean fsr3FrameGeneration,
        boolean diagnosticValidation) {
    public DlssUserConfig(boolean enabled, DlssQualityMode qualityMode) {
        this(enabled, UpscalerBackendMode.DLSS, qualityMode, false, false);
    }

    public DlssUserConfig(
            boolean enabled, DlssQualityMode qualityMode, boolean diagnosticValidation) {
        this(enabled, UpscalerBackendMode.DLSS, qualityMode, false,
                diagnosticValidation);
    }

    public DlssUserConfig {
        if (backend == null) backend = UpscalerBackendMode.DLSS;
        if (qualityMode == null) qualityMode = DlssQualityMode.QUALITY;
    }

    public static DlssUserConfig load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new DlssUserConfig(false, UpscalerBackendMode.DLSS,
                    DlssQualityMode.QUALITY, false, false);
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
            boolean enabled = Boolean.parseBoolean(values.getProperty("enabled", "false"));
            UpscalerBackendMode backend;
            try {
                backend = UpscalerBackendMode.valueOf(values.getProperty(
                        "backend", UpscalerBackendMode.DLSS.name()));
            } catch (IllegalArgumentException error) {
                backend = UpscalerBackendMode.DLSS;
            }
            DlssQualityMode mode;
            try {
                mode = DlssQualityMode.valueOf(values.getProperty(
                        "qualityMode", DlssQualityMode.QUALITY.name()));
            } catch (IllegalArgumentException error) {
                mode = DlssQualityMode.QUALITY;
            }
            boolean diagnostic = Boolean.parseBoolean(
                    values.getProperty("diagnosticValidation", "false"));
            boolean frameGeneration = Boolean.parseBoolean(
                    values.getProperty("fsr3FrameGeneration", "false"));
            return new DlssUserConfig(
                    enabled, backend, mode, frameGeneration, diagnostic);
        } catch (Exception error) {
            return new DlssUserConfig(false, UpscalerBackendMode.DLSS,
                    DlssQualityMode.QUALITY, false, false);
        }
    }

    public void save(Path file) throws Exception {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Properties values = new Properties();
        values.setProperty("enabled", Boolean.toString(enabled));
        values.setProperty("backend", backend.name());
        values.setProperty("qualityMode", qualityMode.name());
        values.setProperty("fsr3FrameGeneration", Boolean.toString(fsr3FrameGeneration));
        values.setProperty("diagnosticValidation", Boolean.toString(diagnosticValidation));
        try (OutputStream output = Files.newOutputStream(temporary)) {
            values.store(output, "MC DLSS settings");
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
