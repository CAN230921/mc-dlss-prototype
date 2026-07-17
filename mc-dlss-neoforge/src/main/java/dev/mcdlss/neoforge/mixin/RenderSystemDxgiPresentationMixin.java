package dev.mcdlss.neoforge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcdlss.neoforge.IrisDlssSessionController;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderSystem.class)
public abstract class RenderSystemDxgiPresentationMixin {
    @Redirect(
            method = "flipFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private static void mcDlss$presentThroughDxgi(long windowHandle) {
        if (!IrisDlssSessionController.presentDxgiRealFrame(windowHandle)) {
            GLFW.glfwSwapBuffers(windowHandle);
        }
    }
}
