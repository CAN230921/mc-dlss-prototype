package dev.mcdlss.neoforge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class FrameGenerationHudlessCapture {
    private static TextureTarget hudlessTarget;
    private static TextureTarget uiTarget;
    private static TextureTarget compositedTarget;
    private static RenderTarget mainTarget;
    private static long hudlessGeneration;
    private static long compositedGeneration;

    public static void captureBeforeGui() {
        if (!FrameGenerationRuntimeState.captureDecision().active()) {
            hudlessGeneration = 0;
            compositedGeneration = 0;
            return;
        }
        RenderSystem.assertOnRenderThread();
        Minecraft client = Minecraft.getInstance();
        RenderTarget source = client.getMainRenderTarget();
        if (source == null || source.width <= 0 || source.height <= 0) return;
        ensureTargets(source.width, source.height);
        copyColor(source, hudlessTarget);
        hudlessGeneration++;
        compositedGeneration = 0;

        uiTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        uiTarget.clear(Minecraft.ON_OSX);
        mainTarget = source;
        uiTarget.bindWrite(true);
    }

    public static void captureAfterGui() {
        if (!FrameGenerationRuntimeState.captureDecision().active()
                || hudlessGeneration == 0) return;
        RenderSystem.assertOnRenderThread();
        RenderTarget source = mainTarget;
        mainTarget = null;
        if (source == null || hudlessTarget == null || uiTarget == null
                || source.width != hudlessTarget.width
                || source.height != hudlessTarget.height) return;

        source.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        uiTarget.blitToScreen(source.width, source.height, false);
        RenderSystem.disableBlend();
        source.bindWrite(true);

        copyColor(source, compositedTarget);
        compositedGeneration = hudlessGeneration;
    }

    private static void copyColor(RenderTarget source, RenderTarget destination) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
            GL30.glBlitFramebuffer(
                    0, 0, source.width, source.height,
                    0, 0, destination.width, destination.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    public static boolean available() {
        return hudlessTarget != null && uiTarget != null
                && compositedTarget != null
                && hudlessGeneration > 0
                && compositedGeneration == hudlessGeneration;
    }

    public static int hudlessTextureId() {
        return available() ? hudlessTarget.getColorTextureId() : 0;
    }

    public static int compositedTextureId() {
        return available() ? compositedTarget.getColorTextureId() : 0;
    }

    public static int uiColorAndAlphaTextureId() {
        return available() ? uiTarget.getColorTextureId() : 0;
    }

    public static long pairedGeneration() {
        return available() ? hudlessGeneration : 0;
    }

    public static void compositeUiToMain() {
        if (!available() || mainTarget != null) return;
        RenderSystem.assertOnRenderThread();
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        target.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        uiTarget.blitToScreen(target.width, target.height, false);
        RenderSystem.disableBlend();
        target.bindWrite(true);
    }

    public static void restoreCurrentFrame() {
        if (!available()) return;
        RenderSystem.assertOnRenderThread();
        copyColor(compositedTarget, Minecraft.getInstance().getMainRenderTarget());
    }

    public static void shutdown() {
        if (hudlessTarget != null) hudlessTarget.destroyBuffers();
        if (uiTarget != null) uiTarget.destroyBuffers();
        if (compositedTarget != null) compositedTarget.destroyBuffers();
        hudlessTarget = null;
        uiTarget = null;
        compositedTarget = null;
        mainTarget = null;
        hudlessGeneration = 0;
        compositedGeneration = 0;
    }

    private static void ensureTargets(int width, int height) {
        if (hudlessTarget != null && uiTarget != null && compositedTarget != null
                && hudlessTarget.width == width && hudlessTarget.height == height) return;
        shutdown();
        hudlessTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        uiTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        compositedTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        hudlessTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        uiTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        compositedTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    private FrameGenerationHudlessCapture() {
    }
}
