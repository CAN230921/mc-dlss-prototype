package dev.mcdlss.neoforge;

import dev.mcdlss.core.IrisRenderTargetDiagnostics;
import dev.mcdlss.core.IrisRenderTargetSnapshot;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class McDlssNeoForgeIrisDiagnostics {
    private static IrisRenderTargetSnapshot current = IrisRenderTargetSnapshot.absent();
    private static String lastDiagnostic = "";

    public static void onClientTick(ClientTickEvent.Post event) {
        current = ModList.get().isLoaded("iris")
                ? IrisRenderTargetAdapter.snapshot()
                : IrisRenderTargetSnapshot.absent();
        String diagnostic = IrisRenderTargetDiagnostics.format(current);
        if (!diagnostic.equals(lastDiagnostic)) {
            lastDiagnostic = diagnostic;
            System.out.println("[mc_dlss/iris] " + diagnostic);
        }
    }

    public static IrisRenderTargetSnapshot current() {
        return current;
    }

    private McDlssNeoForgeIrisDiagnostics() {
    }
}
