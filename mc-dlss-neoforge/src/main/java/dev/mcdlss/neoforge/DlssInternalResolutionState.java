package dev.mcdlss.neoforge;

import dev.mcdlss.core.DlssInternalResolution;
import dev.mcdlss.core.DlssUserConfig;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;

public final class DlssInternalResolutionState {
    private static volatile Snapshot current = Snapshot.inactive();
    private static volatile boolean worldViewportActive;

    public static boolean synchronize(DlssUserConfig config) {
        Minecraft client = Minecraft.getInstance();
        // Pipeline destruction temporarily makes isPackInUseQuick() false. Using it here
        // would toggle this state every tick and continuously rebuild the Iris pipeline.
        boolean active = config.enabled() && client.level != null
                && Iris.getCurrentPack().isPresent();
        Snapshot next = Snapshot.inactive();
        if (active) {
            int outputWidth = client.getWindow().getWidth();
            int outputHeight = client.getWindow().getHeight();
            if (outputWidth > 0 && outputHeight > 0) {
                DlssInternalResolution internal = DlssInternalResolution.forOutput(
                        outputWidth, outputHeight, config.qualityMode());
                next = new Snapshot(true, outputWidth, outputHeight,
                        internal.width(), internal.height());
            }
        }
        Snapshot previous = current;
        if (previous.equals(next)) return false;
        current = next;
        Iris.getPipelineManager().destroyPipeline();
        return true;
    }

    public static int widthOr(int original) {
        Snapshot snapshot = current;
        return snapshot.active ? snapshot.internalWidth : original;
    }

    public static int heightOr(int original) {
        Snapshot snapshot = current;
        return snapshot.active ? snapshot.internalHeight : original;
    }

    public static Snapshot current() {
        return current;
    }

    public static void applyWorldViewport() {
        Snapshot snapshot = current;
        if (snapshot.active) {
            worldViewportActive = true;
            RenderSystem.viewport(0, 0, snapshot.internalWidth, snapshot.internalHeight);
        }
    }

    public static void applyOutputViewport() {
        worldViewportActive = false;
        Snapshot snapshot = current;
        if (snapshot.active) {
            RenderSystem.viewport(0, 0, snapshot.outputWidth, snapshot.outputHeight);
        }
    }

    public static int remapViewportWidth(int requestedWidth, int requestedHeight) {
        Snapshot snapshot = current;
        return DlssWorldRenderTargetController.redirected() && snapshot.active
                && requestedWidth == snapshot.outputWidth
                && requestedHeight == snapshot.outputHeight
                ? snapshot.internalWidth : requestedWidth;
    }

    public static int remapViewportHeight(int requestedWidth, int requestedHeight) {
        Snapshot snapshot = current;
        return DlssWorldRenderTargetController.redirected() && snapshot.active
                && requestedWidth == snapshot.outputWidth
                && requestedHeight == snapshot.outputHeight
                ? snapshot.internalHeight : requestedHeight;
    }

    public record Snapshot(boolean active, int outputWidth, int outputHeight,
                           int internalWidth, int internalHeight) {
        private static Snapshot inactive() {
            return new Snapshot(false, 0, 0, 0, 0);
        }
    }

    private DlssInternalResolutionState() {
    }
}
