package dev.mcdlss.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class McDlssNeoForgeClientOverlay {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(
            McDlssNeoForgeMod.MOD_ID,
            "diagnostic_overlay");
    private static final int X = 6;
    private static final int Y = 6;
    private static final int COLOR = 0xFFE6F2FF;

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.DEBUG_OVERLAY, LAYER_ID, McDlssNeoForgeClientOverlay::render);
    }

    private static void render(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        guiGraphics.drawString(client.font, IrisDlssSessionController.basicStatus(),
                X, Y, COLOR, true);
    }

    private McDlssNeoForgeClientOverlay() {
    }
}
