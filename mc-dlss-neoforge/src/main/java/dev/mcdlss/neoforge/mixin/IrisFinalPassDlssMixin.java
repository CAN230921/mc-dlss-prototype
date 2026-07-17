package dev.mcdlss.neoforge.mixin;

import dev.mcdlss.neoforge.IrisDlssSessionController;
import dev.mcdlss.neoforge.DlssInternalResolutionState;
import dev.mcdlss.neoforge.DlssWorldRenderTargetController;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisFinalPassDlssMixin {
    @Inject(
            method = "finalizeLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/FinalPassRenderer;renderFinalPass()V",
                    shift = At.Shift.BEFORE),
            require = 1)
    private void mcDlss$beforeFinalPass(CallbackInfo ci) {
        DlssWorldRenderTargetController.restoreForFinalPass();
        DlssInternalResolutionState.applyOutputViewport();
        IrisDlssSessionController.beforeIrisFinalPass();
    }

    @Inject(
            method = "finalizeLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/FinalPassRenderer;renderFinalPass()V",
                    shift = At.Shift.AFTER),
            require = 1)
    private void mcDlss$afterFinalPass(CallbackInfo ci) {
        IrisDlssSessionController.afterIrisFinalPass();
    }
}
