package dev.mcdlss.neoforge;

import dev.mcdlss.debug.LoaderStartupDiagnostics;
import dev.mcdlss.core.NativeBundleBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.Path;

@Mod(McDlssNeoForgeMod.MOD_ID)
public final class McDlssNeoForgeMod {
    public static final String MOD_ID = "mc_dlss";

    public McDlssNeoForgeMod(IEventBus modEventBus) {
        var packagedNative = NativeBundleBootstrap.load(Path.of("."));
        System.out.println("[mc_dlss/native-bundle] stage=" + packagedNative.stage()
                + " ready=" + packagedNative.available()
                + " message=" + packagedNative.message());
        modEventBus.addListener(McDlssNeoForgeClientOverlay::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(McDlssNeoForgeIrisDiagnostics::onClientTick);
        NeoForge.EVENT_BUS.addListener(IrisDlssSessionController::onClientTick);
        NeoForge.EVENT_BUS.addListener(IrisDlssSessionController::onRenderLevelStage);
        System.out.println("[mc_dlss/neoforge] "
                + LoaderStartupDiagnostics.format("neoforge", new McDlssNeoForgeEntrypoint().debugSnapshot()));
    }
}
