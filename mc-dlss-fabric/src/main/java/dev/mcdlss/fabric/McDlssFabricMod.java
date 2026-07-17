package dev.mcdlss.fabric;

import dev.mcdlss.debug.LoaderStartupDiagnostics;
import net.fabricmc.api.ModInitializer;

public final class McDlssFabricMod implements ModInitializer {
    public static final String MOD_ID = "mc_dlss";

    @Override
    public void onInitialize() {
        System.out.println("[mc_dlss/fabric] "
                + LoaderStartupDiagnostics.format("fabric", new McDlssFabricEntrypoint().debugSnapshot()));
    }
}
