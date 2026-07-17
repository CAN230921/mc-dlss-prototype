package dev.mcdlss.neoforge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.mcdlss.neoforge.mixin.MinecraftMainRenderTargetAccessor;
import net.minecraft.client.Minecraft;

public final class DlssWorldRenderTargetController {
    private static TextureTarget lowResolutionTarget;
    private static RenderTarget originalTarget;
    private static boolean redirected;

    public static void beforeWorldRender() {
        Minecraft client = Minecraft.getInstance();
        DlssInternalResolutionState.Snapshot state = DlssInternalResolutionState.current();
        if (!state.active() || client.level == null || redirected) return;
        ensureTarget(state.internalWidth(), state.internalHeight());
        RenderTarget current = client.getMainRenderTarget();
        if (current == null || current == lowResolutionTarget) return;
        originalTarget = current;
        lowResolutionTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        lowResolutionTarget.clear(Minecraft.ON_OSX);
        setMainTarget(client, lowResolutionTarget);
        redirected = true;
        lowResolutionTarget.bindWrite(true);
    }

    public static void restoreForFinalPass() {
        restore();
    }

    public static void afterWorldRender() {
        restore();
    }

    public static void update() {
        if (!DlssInternalResolutionState.current().active()) {
            restore();
            releaseTarget();
        }
    }

    public static boolean redirected() {
        return redirected;
    }

    public static void shutdown() {
        restore();
        releaseTarget();
    }

    private static void ensureTarget(int width, int height) {
        if (lowResolutionTarget != null
                && lowResolutionTarget.width == width
                && lowResolutionTarget.height == height) return;
        releaseTarget();
        lowResolutionTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
    }

    private static void restore() {
        if (!redirected) return;
        Minecraft client = Minecraft.getInstance();
        RenderTarget target = originalTarget;
        originalTarget = null;
        redirected = false;
        if (target != null) {
            setMainTarget(client, target);
            target.bindWrite(true);
        }
    }

    private static void releaseTarget() {
        if (lowResolutionTarget != null) lowResolutionTarget.destroyBuffers();
        lowResolutionTarget = null;
    }

    private static void setMainTarget(Minecraft client, RenderTarget target) {
        ((MinecraftMainRenderTargetAccessor) (Object) client)
                .mcDlss$setMainRenderTarget(target);
    }

    private DlssWorldRenderTargetController() {
    }
}
