package dev.mcdlss.core;

import java.util.Objects;

public record IrisRenderTargetSnapshot(
        IrisRenderTargetStatus status,
        String irisVersion,
        String pipelineClass,
        int width,
        int height,
        int colorMainTexture,
        int colorAltTexture,
        int depthTexture,
        int depthNoTranslucentsTexture,
        int depthNoHandTexture,
        boolean shaderPackActive,
        long generation) {
    public static final String SUPPORTED_IRIS_VERSION = "1.8.14-beta.1+mc1.21.1";

    public IrisRenderTargetSnapshot {
        Objects.requireNonNull(status, "status");
        irisVersion = Objects.requireNonNullElse(irisVersion, "");
        pipelineClass = Objects.requireNonNullElse(pipelineClass, "");
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        if (status == IrisRenderTargetStatus.READY
                && (width <= 0 || height <= 0 || colorMainTexture <= 0 || colorAltTexture <= 0
                || depthTexture <= 0 || depthNoTranslucentsTexture <= 0 || depthNoHandTexture <= 0)) {
            throw new IllegalArgumentException("ready Iris targets require valid dimensions and texture ids");
        }
    }

    public static IrisRenderTargetSnapshot absent() {
        return empty(IrisRenderTargetStatus.IRIS_ABSENT, "", "", false, 0);
    }

    public static IrisRenderTargetSnapshot wrongVersion(String version) {
        return empty(IrisRenderTargetStatus.VERSION_MISMATCH, version, "", false, 0);
    }

    public static IrisRenderTargetSnapshot vanillaPipeline(
            String version,
            String pipelineClass,
            long generation) {
        return empty(IrisRenderTargetStatus.VANILLA_PIPELINE, version, pipelineClass, false, generation);
    }

    public static IrisRenderTargetSnapshot irisPipeline(
            String version,
            String pipelineClass,
            int width,
            int height,
            int colorMainTexture,
            int colorAltTexture,
            int depthTexture,
            int depthNoTranslucentsTexture,
            int depthNoHandTexture,
            boolean shaderPackActive,
            long generation) {
        if (!SUPPORTED_IRIS_VERSION.equals(version)) {
            return wrongVersion(version);
        }
        return new IrisRenderTargetSnapshot(
                IrisRenderTargetStatus.READY,
                version,
                pipelineClass,
                width,
                height,
                colorMainTexture,
                colorAltTexture,
                depthTexture,
                depthNoTranslucentsTexture,
                depthNoHandTexture,
                shaderPackActive,
                generation);
    }

    public boolean targetsUsable() {
        return status == IrisRenderTargetStatus.READY;
    }

    private static IrisRenderTargetSnapshot empty(
            IrisRenderTargetStatus status,
            String version,
            String pipelineClass,
            boolean shaderPackActive,
            long generation) {
        return new IrisRenderTargetSnapshot(
                status, version, pipelineClass, 0, 0, 0, 0, 0, 0, 0, shaderPackActive, generation);
    }
}
