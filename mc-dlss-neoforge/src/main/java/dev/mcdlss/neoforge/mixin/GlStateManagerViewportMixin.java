package dev.mcdlss.neoforge.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.mcdlss.neoforge.DlssInternalResolutionState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerViewportMixin {
    @ModifyArgs(
            method = "_viewport",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V"),
            require = 1,
            remap = false)
    private static void mcDlss$remapWorldViewport(Args args) {
        int width = args.get(2);
        int height = args.get(3);
        args.set(2, DlssInternalResolutionState.remapViewportWidth(width, height));
        args.set(3, DlssInternalResolutionState.remapViewportHeight(width, height));
    }
}
