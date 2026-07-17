package dev.mcdlss.core;

import java.util.List;

public final class IrisRenderTargetDiagnostics {
    public static List<String> lines(IrisRenderTargetSnapshot snapshot) {
        return List.of(
                "Iris targets: " + snapshot.status() + " pack=" + snapshot.shaderPackActive(),
                "pipeline=" + shortPipelineName(snapshot.pipelineClass())
                        + " size=" + snapshot.width() + "x" + snapshot.height(),
                "color=" + snapshot.colorMainTexture() + "/" + snapshot.colorAltTexture()
                        + " depth=" + snapshot.depthTexture() + "/"
                        + snapshot.depthNoTranslucentsTexture() + "/" + snapshot.depthNoHandTexture()
                        + " generation=" + snapshot.generation());
    }

    public static String format(IrisRenderTargetSnapshot snapshot) {
        return String.join(" | ", lines(snapshot));
    }

    private static String shortPipelineName(String pipelineClass) {
        int separator = pipelineClass.lastIndexOf('.');
        return separator >= 0 ? pipelineClass.substring(separator + 1) : pipelineClass;
    }

    private IrisRenderTargetDiagnostics() {
    }
}
