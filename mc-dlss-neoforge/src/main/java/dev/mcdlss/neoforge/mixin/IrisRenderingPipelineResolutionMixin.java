package dev.mcdlss.neoforge.mixin;

import dev.mcdlss.neoforge.DlssInternalResolutionState;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisRenderingPipelineResolutionMixin {
    @ModifyArgs(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/targets/RenderTargets;<init>(IIIILnet/irisshaders/iris/gl/texture/DepthBufferFormat;Ljava/util/Map;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)V"),
            require = 1)
    private void mcDlss$useInternalResolution(Args args) {
        args.set(0, DlssInternalResolutionState.widthOr(args.get(0)));
        args.set(1, DlssInternalResolutionState.heightOr(args.get(1)));
    }

    @ModifyArgs(
            method = "beginLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/targets/RenderTargets;resizeIfNeeded(IIIILnet/irisshaders/iris/gl/texture/DepthBufferFormat;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Z"),
            require = 1)
    private void mcDlss$keepInternalResolution(Args args) {
        args.set(2, DlssInternalResolutionState.widthOr(args.get(2)));
        args.set(3, DlssInternalResolutionState.heightOr(args.get(3)));
    }

    @Inject(method = "beginLevelRendering", at = @At("RETURN"))
    private void mcDlss$applyInternalViewport(CallbackInfo ci) {
        DlssInternalResolutionState.applyWorldViewport();
    }
}
