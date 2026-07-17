package dev.mcdlss.neoforge.mixin;

import dev.mcdlss.neoforge.DlssWorldRenderTargetController;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererWorldTargetMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void mcDlss$beforeWorldRender(CallbackInfo ci) {
        DlssWorldRenderTargetController.beforeWorldRender();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void mcDlss$afterWorldRender(CallbackInfo ci) {
        DlssWorldRenderTargetController.afterWorldRender();
    }
}
