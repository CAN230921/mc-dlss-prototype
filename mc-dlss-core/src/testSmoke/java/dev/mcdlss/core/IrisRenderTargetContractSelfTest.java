package dev.mcdlss.core;

public final class IrisRenderTargetContractSelfTest {
    public static void runAll() {
        reportsIrisAbsent();
        rejectsWrongVersion();
        reportsVanillaPipelineWithoutTargets();
        acceptsExactIrisPipelineTargets();
        formatsReadyTargetDiagnostics();
        selectsColorByFinalizationBoundary();
        detectsDlssSessionReopenConditions();
        resetsTemporalHistoryAtSequenceBoundaries();
        selectsPackagedNativePlatform();
    }

    private static void reportsIrisAbsent() {
        IrisRenderTargetSnapshot snapshot = IrisRenderTargetSnapshot.absent();
        require(snapshot.status() == IrisRenderTargetStatus.IRIS_ABSENT, "Expected Iris absent status");
        require(!snapshot.targetsUsable(), "Absent Iris cannot expose usable targets");
    }

    private static void rejectsWrongVersion() {
        IrisRenderTargetSnapshot snapshot = IrisRenderTargetSnapshot.wrongVersion("1.8.13");
        require(snapshot.status() == IrisRenderTargetStatus.VERSION_MISMATCH, "Expected version mismatch");
        require(!snapshot.targetsUsable(), "Wrong Iris version cannot expose usable targets");
    }

    private static void reportsVanillaPipelineWithoutTargets() {
        IrisRenderTargetSnapshot snapshot = IrisRenderTargetSnapshot.vanillaPipeline(
                IrisRenderTargetSnapshot.SUPPORTED_IRIS_VERSION,
                "net.irisshaders.iris.pipeline.VanillaRenderingPipeline",
                7L);
        require(snapshot.status() == IrisRenderTargetStatus.VANILLA_PIPELINE, "Expected vanilla pipeline");
        require(!snapshot.shaderPackActive(), "Vanilla pipeline must report no active shaderpack");
        require(!snapshot.targetsUsable(), "Vanilla pipeline must not provide Iris targets");
    }

    private static void acceptsExactIrisPipelineTargets() {
        IrisRenderTargetSnapshot snapshot = IrisRenderTargetSnapshot.irisPipeline(
                IrisRenderTargetSnapshot.SUPPORTED_IRIS_VERSION,
                "net.irisshaders.iris.pipeline.IrisRenderingPipeline",
                1920,
                1080,
                41,
                42,
                51,
                52,
                53,
                true,
                8L);
        require(snapshot.status() == IrisRenderTargetStatus.READY, "Expected ready Iris targets");
        require(snapshot.targetsUsable(), "Exact Iris pipeline targets should be usable");
        require(snapshot.colorMainTexture() == 41 && snapshot.colorAltTexture() == 42,
                "Color targets were not preserved");
        require(snapshot.depthTexture() == 51 && snapshot.depthNoTranslucentsTexture() == 52
                        && snapshot.depthNoHandTexture() == 53,
                "Depth targets were not preserved");
    }

    private static void formatsReadyTargetDiagnostics() {
        IrisRenderTargetSnapshot snapshot = IrisRenderTargetSnapshot.irisPipeline(
                IrisRenderTargetSnapshot.SUPPORTED_IRIS_VERSION,
                "net.irisshaders.iris.pipeline.IrisRenderingPipeline",
                1280, 720, 11, 12, 21, 22, 23, true, 9L);
        String formatted = IrisRenderTargetDiagnostics.format(snapshot);
        require(formatted.contains("READY") && formatted.contains("1280x720"),
                "Diagnostic must include status and dimensions");
        require(formatted.contains("color=11/12") && formatted.contains("depth=21/22/23"),
                "Diagnostic must include Iris texture ids");
        require(formatted.contains("generation=9"), "Diagnostic must include generation");
    }

    private static void selectsColorByFinalizationBoundary() {
        IrisRenderTargetSnapshot snapshot = IrisRenderTargetSnapshot.irisPipeline(
                IrisRenderTargetSnapshot.SUPPORTED_IRIS_VERSION,
                "net.irisshaders.iris.pipeline.IrisRenderingPipeline",
                1280, 720, 11, 12, 21, 22, 23, true, 9L);
        IrisDlssInputSelection main = IrisDlssInputSelection.select(snapshot, false, false);
        IrisDlssInputSelection alt = IrisDlssInputSelection.select(snapshot, true, false);
        IrisDlssInputSelection normalized = IrisDlssInputSelection.select(snapshot, true, true);
        require(main.colorTexture() == 11, "Unflipped pre-final input must use main color");
        require(alt.colorTexture() == 12, "Flipped pre-final input must use alt color");
        require(normalized.colorTexture() == 11, "Post-final input must use normalized main color");
        require(normalized.depthTexture() == 21 && normalized.generation() == 9L,
                "Selection must preserve depth and generation");
    }

    private static void detectsDlssSessionReopenConditions() {
        IrisDlssSessionKey initial = new IrisDlssSessionKey(9L, 1280, 720, 1920, 1080);
        require(!initial.requiresReopen(new IrisDlssSessionKey(9L, 1280, 720, 1920, 1080)),
                "Stable Iris targets must retain the DLSS session");
        require(initial.requiresReopen(new IrisDlssSessionKey(10L, 1280, 720, 1920, 1080)),
                "Pipeline generation change must reopen the DLSS session");
        require(initial.requiresReopen(new IrisDlssSessionKey(9L, 1280, 720, 2560, 1440)),
                "Output resize must reopen the DLSS session");
    }

    private static void resetsTemporalHistoryAtSequenceBoundaries() {
        IrisTemporalFrameSequence sequence = new IrisTemporalFrameSequence();
        require(sequence.advance(7L, 569, 320).reset(), "First temporal frame must reset");
        require(!sequence.advance(7L, 569, 320).reset(),
                "Continuous frame in one generation must retain history");
        require(sequence.advance(8L, 569, 320).reset(),
                "Iris generation change must reset temporal history");
        require(sequence.advance(8L, 640, 360).reset(),
                "Render-size change must reset temporal history");
    }

    private static void selectsPackagedNativePlatform() {
        require(NativeBundlePlatform.select("Windows 11", "amd64")
                        == NativeBundlePlatform.WINDOWS_X86_64,
                "Windows amd64 must select the packaged native bundle");
        require(NativeBundlePlatform.select("Linux", "amd64")
                        == NativeBundlePlatform.UNSUPPORTED,
                "Unsupported platforms must not attempt native extraction");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private IrisRenderTargetContractSelfTest() {
    }
}
