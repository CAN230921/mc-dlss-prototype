package dev.mcdlss.neoforge.mixin;

import dev.mcdlss.neoforge.FrameGenerationHudlessCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiFrameGenerationCaptureMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void mcDlss$beforeGui(
            GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        FrameGenerationHudlessCapture.captureBeforeGui();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mcDlss$afterGui(
            GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        guiGraphics.flush();
        FrameGenerationHudlessCapture.captureAfterGui();
    }
}
