package dev.mcdlss.neoforge.mixin;

import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public interface IrisRenderingPipelineAccessor {
    @Accessor("renderTargets")
    RenderTargets mcDlss$getRenderTargets();
}
