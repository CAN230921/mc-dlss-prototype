package dev.mcdlss.neoforge.mixin;

import dev.mcdlss.neoforge.IrisDlssSessionController;
import net.irisshaders.iris.targets.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderTarget.class, remap = false)
public abstract class IrisRenderTargetTextureMixin {
    @Inject(method = "getMainTexture", at = @At("RETURN"), cancellable = true)
    private void mcDlss$replaceFinalColor(CallbackInfoReturnable<Integer> cir) {
        int replacement = IrisDlssSessionController.finalPassTextureOverride(cir.getReturnValue());
        if (replacement != 0) cir.setReturnValue(replacement);
    }
}
