package dev.mcdlss.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.mcdlss.fabric.LiveDlssRenderWorldHooks;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class GameRendererWorldFramebufferMixin {
    @WrapMethod(method = "renderWorld")
    private void mcDlss$wrapWorldRender(
            RenderTickCounter tickCounter, Operation<Void> original) {
        LiveDlssRenderWorldHooks.beforeWorldRender(tickCounter);
        try {
            original.call(tickCounter);
        } finally {
            LiveDlssRenderWorldHooks.afterWorldRender();
        }
    }
}
