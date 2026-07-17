package dev.mcdlss.neoforge;

import dev.mcdlss.core.IrisRenderTargetSnapshot;
import dev.mcdlss.neoforge.mixin.IrisRenderingPipelineAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;

import java.util.Optional;

public final class IrisRenderTargetAdapter {
    public static IrisRenderTargetSnapshot snapshot() {
        try {
            String version = Iris.getVersion();
            if (!IrisRenderTargetSnapshot.SUPPORTED_IRIS_VERSION.equals(version)) {
                return IrisRenderTargetSnapshot.wrongVersion(version);
            }

            PipelineManager manager = Iris.getPipelineManager();
            Optional<WorldRenderingPipeline> current = manager.getPipeline();
            if (current.isEmpty() || !(current.get() instanceof IrisRenderingPipeline pipeline)) {
                String pipelineClass = current.map(value -> value.getClass().getName()).orElse("");
                return IrisRenderTargetSnapshot.vanillaPipeline(
                        version,
                        pipelineClass,
                        manager.getVersionCounterForSodiumShaderReload());
            }

            RenderTargets targets = ((IrisRenderingPipelineAccessor) pipeline)
                    .mcDlss$getRenderTargets();
            RenderTarget color = targets.get(0);
            long generation = ((long) manager.getVersionCounterForSodiumShaderReload() << 32)
                    | Integer.toUnsignedLong(System.identityHashCode(targets));
            return IrisRenderTargetSnapshot.irisPipeline(
                    version,
                    pipeline.getClass().getName(),
                    targets.getCurrentWidth(),
                    targets.getCurrentHeight(),
                    color.getMainTexture(),
                    color.getAltTexture(),
                    targets.getDepthTexture(),
                    targets.getDepthTextureNoTranslucents().getTextureId(),
                    targets.getDepthTextureNoHand().getTextureId(),
                    Iris.isPackInUseQuick(),
                    generation);
        } catch (LinkageError error) {
            return IrisRenderTargetSnapshot.absent();
        } catch (RuntimeException error) {
            return IrisRenderTargetSnapshot.vanillaPipeline(
                    IrisRenderTargetSnapshot.SUPPORTED_IRIS_VERSION,
                    "unavailable:" + error.getClass().getSimpleName(),
                    0);
        }
    }

    private IrisRenderTargetAdapter() {
    }
}
