package dev.mcdlss.fabric;

import dev.mcdlss.debug.DlssOverlayLines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public final class McDlssFabricClientMod implements ClientModInitializer {
    private static final int X = 6;
    private static final int Y = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int COLOR = 0xFFE6F2FF;

    private final McDlssFabricEntrypoint entrypoint = new McDlssFabricEntrypoint();
    private MinecraftGlInteropSnapshot glInteropSnapshot;
    private MinecraftLiveInteropSnapshot liveInteropSnapshot;
    private MinecraftWorldColorCaptureSnapshot worldColorCaptureSnapshot;
    private MinecraftPersistentColorCaptureProbe persistentColorCaptureProbe;
    private MinecraftPersistentColorCaptureSnapshot persistentColorCaptureSnapshot;
    private PersistentColorCaptureState lastLoggedPersistentState;
    private MinecraftPersistentFrameCaptureProbe persistentFrameCaptureProbe;
    private MinecraftPersistentMotionFrameCaptureSnapshot persistentFrameCaptureSnapshot;
    private PersistentFrameCaptureState lastLoggedPersistentFrameState;
    private LiveDlssPresentationController liveDlssController;
    private LiveDlssPresentationSnapshot liveDlssSnapshot;
    private LiveDlssPresentationState lastLoggedLiveDlssState;
    private int lastLoggedLiveDlssFrames = -1;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("mcDlss.livePresentation")) {
            liveDlssController = new LiveDlssPresentationController(entrypoint);
            LiveDlssRenderWorldHooks.install(liveDlssController);
            liveDlssSnapshot = liveDlssController.snapshot();
            logLiveDlssTransition();
        }

        WorldRenderEvents.START.register(context -> {
            if (liveDlssController != null) {
                liveDlssController.restoreLowResolutionViewport();
            }
        });
        WorldRenderEvents.AFTER_SETUP.register(context -> restoreLiveDlssViewport());
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> restoreLiveDlssViewport());
        WorldRenderEvents.AFTER_ENTITIES.register(context -> restoreLiveDlssViewport());
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hitResult) -> {
            restoreLiveDlssViewport();
            return true;
        });
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outlineContext) -> {
            restoreLiveDlssViewport();
            return true;
        });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> restoreLiveDlssViewport());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> restoreLiveDlssViewport());
        WorldRenderEvents.LAST.register(context -> restoreLiveDlssViewport());

        WorldRenderEvents.END.register(context -> {
            if (liveDlssController != null) {
                liveDlssController.onWorldRenderEnd(context);
                liveDlssSnapshot = liveDlssController.snapshot();
                logLiveDlssTransition();
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (worldColorCaptureSnapshot == null
                    && liveInteropSnapshot != null
                    && liveInteropSnapshot.success()
                    && client.world != null) {
                worldColorCaptureSnapshot = MinecraftWorldColorCaptureProbe.probe(entrypoint);
                System.out.println("[mc_dlss/fabric/world-color] "
                        + MinecraftWorldColorCaptureProbe.toLogLine(worldColorCaptureSnapshot));
            }
            if (worldColorCaptureSnapshot != null && worldColorCaptureSnapshot.success()) {
                if (persistentColorCaptureProbe == null) {
                    persistentColorCaptureProbe =
                            new MinecraftPersistentColorCaptureProbe(entrypoint);
                }
                persistentColorCaptureSnapshot = persistentColorCaptureProbe.onWorldFrame();
                logPersistentTransition();
            }
            if (persistentColorCaptureSnapshot != null
                    && persistentColorCaptureSnapshot.success()) {
                if (persistentFrameCaptureProbe == null) {
                    persistentFrameCaptureProbe =
                            new MinecraftPersistentFrameCaptureProbe(entrypoint);
                }
                persistentFrameCaptureSnapshot = persistentFrameCaptureProbe.onWorldFrame(context);
                logPersistentFrameTransition();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (liveDlssController != null) {
                liveDlssController.onClientTick(client);
                liveDlssSnapshot = liveDlssController.snapshot();
                logLiveDlssTransition();
                return;
            }
            if (persistentColorCaptureProbe != null) {
                persistentColorCaptureSnapshot = persistentColorCaptureProbe.onClientTick();
                logPersistentTransition();
            }
            if (persistentFrameCaptureProbe != null) {
                persistentFrameCaptureSnapshot = persistentFrameCaptureProbe.onClientTick();
                logPersistentFrameTransition();
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (liveDlssController != null) {
                liveDlssSnapshot = liveDlssController.snapshot();
                MinecraftClient client = MinecraftClient.getInstance();
                int y = Y;
                for (String line : LiveDlssPresentationOverlayLines.fromSnapshot(
                        liveDlssSnapshot)) {
                    drawContext.drawText(client.textRenderer, line, X, y, COLOR, true);
                    y += LINE_HEIGHT;
                }
                return;
            }
            if (glInteropSnapshot == null) {
                try {
                    glInteropSnapshot = MinecraftGlInteropProbe.probe(
                            entrypoint.probeD3D12Adapter());
                } catch (RuntimeException error) {
                    glInteropSnapshot = MinecraftGlInteropSnapshot.failure(
                            error.getMessage() == null
                                    ? error.getClass().getSimpleName()
                                    : error.getMessage());
                }
                System.out.println("[mc_dlss/fabric/live-gl] "
                        + MinecraftGlInteropProbe.toLogLine(glInteropSnapshot));
            }
            if (liveInteropSnapshot == null) {
                if (glInteropSnapshot.interopPrerequisitesReady()) {
                    liveInteropSnapshot = MinecraftLiveInteropProbe.probe(entrypoint);
                } else {
                    liveInteropSnapshot = new MinecraftLiveInteropSnapshot(
                            false, false, false, false, false,
                            false, false, false, false,
                            "4D-A live OpenGL prerequisites are unavailable");
                }
                System.out.println("[mc_dlss/fabric/live-share] "
                        + MinecraftLiveInteropProbe.toLogLine(liveInteropSnapshot));
            }
            MinecraftClient client = MinecraftClient.getInstance();
            int y = Y;
            for (String line : DlssOverlayLines.fromSnapshot(entrypoint.debugSnapshot())) {
                drawContext.drawText(client.textRenderer, line, X, y, COLOR, true);
                y += LINE_HEIGHT;
            }
            for (String line : MinecraftGlInteropOverlayLines.fromSnapshot(glInteropSnapshot)) {
                drawContext.drawText(client.textRenderer, line, X, y, COLOR, true);
                y += LINE_HEIGHT;
            }
            for (String line : MinecraftLiveInteropOverlayLines.fromSnapshot(liveInteropSnapshot)) {
                drawContext.drawText(client.textRenderer, line, X, y, COLOR, true);
                y += LINE_HEIGHT;
            }
            for (String line : MinecraftWorldColorCaptureOverlayLines.fromSnapshot(
                    worldColorCaptureSnapshot)) {
                drawContext.drawText(client.textRenderer, line, X, y, COLOR, true);
                y += LINE_HEIGHT;
            }
            for (String line : MinecraftPersistentColorCaptureOverlayLines.fromSnapshot(
                    persistentColorCaptureSnapshot)) {
                drawContext.drawText(client.textRenderer, line, X, y, COLOR, true);
                y += LINE_HEIGHT;
            }
            for (String line : MinecraftPersistentMotionFrameCaptureOverlayLines.fromSnapshot(
                    persistentFrameCaptureSnapshot)) {
                drawContext.drawText(client.textRenderer, line, X, y, COLOR, true);
                y += LINE_HEIGHT;
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (liveDlssController != null) {
                liveDlssController.close();
                liveDlssSnapshot = liveDlssController.snapshot();
                logLiveDlssTransition();
                LiveDlssRenderWorldHooks.install(null);
            }
        });
    }

    private void restoreLiveDlssViewport() {
        if (liveDlssController != null) {
            liveDlssController.restoreLowResolutionViewport();
        }
    }

    private void logLiveDlssTransition() {
        if (liveDlssSnapshot == null) return;
        int frames = liveDlssSnapshot.successfulFrames();
        boolean milestone = frames == 30 || frames == 120;
        if (liveDlssSnapshot.state() != lastLoggedLiveDlssState
                || (milestone && frames != lastLoggedLiveDlssFrames)) {
            lastLoggedLiveDlssState = liveDlssSnapshot.state();
            lastLoggedLiveDlssFrames = frames;
            System.out.println("[mc_dlss/fabric/live-dlss] "
                    + toLiveDlssLogLine(liveDlssSnapshot));
        }
    }

    private static String toLiveDlssLogLine(LiveDlssPresentationSnapshot snapshot) {
        return "state=" + snapshot.state()
                + " ready=" + snapshot.dlssReady()
                + " gate=" + snapshot.initialGatePassed()
                + " size=" + snapshot.renderWidth() + "x" + snapshot.renderHeight()
                + "->" + snapshot.outputWidth() + "x" + snapshot.outputHeight()
                + " frames=" + snapshot.successfulFrames() + "/" + snapshot.targetFrames()
                + " retained=" + snapshot.retainedFrames() + "/"
                        + snapshot.targetRetainedFrames()
                + " slotAUses=" + snapshot.slotAUses()
                + " slotBUses=" + snapshot.slotBUses()
                + " resizeCount=" + snapshot.resizeCount()
                + " hashChanges=" + snapshot.outputHashChanges()
                + " glHash=" + snapshot.openGlOutputHash()
                + " d3d12Hash=" + snapshot.d3d12OutputHash()
                + " finite=" + snapshot.finiteChannelCount()
                + " nonBlack=" + snapshot.nonBlackPixelCount()
                + " nonUniform=" + snapshot.outputNonUniform()
                + " resets=" + snapshot.resetFrameCount()
                + " fallbackFrames=" + snapshot.fallbackFrameCount()
                + " framebufferRestored=" + snapshot.originalFramebufferRestored()
                + " resourcesRetained=" + snapshot.resourcesRetained()
                + " resourcesReleased=" + snapshot.resourcesReleased()
                + " failureStage=" + snapshot.failureStage()
                + " averageMs=" + snapshot.averageMilliseconds()
                + " maximumMs=" + snapshot.maximumMilliseconds()
                + " message=" + snapshot.message();
    }

    private void logPersistentTransition() {
        if (persistentColorCaptureSnapshot != null
                && persistentColorCaptureSnapshot.state() != lastLoggedPersistentState) {
            lastLoggedPersistentState = persistentColorCaptureSnapshot.state();
            System.out.println("[mc_dlss/fabric/persistent-color] "
                    + toPersistentLogLine(persistentColorCaptureSnapshot));
        }
    }

    private static String toPersistentLogLine(
            MinecraftPersistentColorCaptureSnapshot snapshot) {
        return "state=" + snapshot.state()
                + " success=" + snapshot.success()
                + " size=" + snapshot.width() + "x" + snapshot.height()
                + " frames=" + snapshot.successfulFrames() + "/" + snapshot.targetFrames()
                + " retained=" + snapshot.retainedFrames() + "/" + snapshot.targetRetainedFrames()
                + " slotAUses=" + snapshot.slotAUses()
                + " slotBUses=" + snapshot.slotBUses()
                + " resizeCount=" + snapshot.resizeCount()
                + " hashChanges=" + snapshot.hashChanges()
                + " glHash=" + snapshot.openGlHash()
                + " d3d12Hash=" + snapshot.d3d12Hash()
                + " averageMs=" + snapshot.averageMilliseconds()
                + " maximumMs=" + snapshot.maximumMilliseconds()
                + " resourcesReleased=" + snapshot.resourcesReleased()
                + " message=" + snapshot.message();
    }

    private void logPersistentFrameTransition() {
        if (persistentFrameCaptureSnapshot != null
                && persistentFrameCaptureSnapshot.frame().state()
                        != lastLoggedPersistentFrameState) {
            lastLoggedPersistentFrameState = persistentFrameCaptureSnapshot.frame().state();
            System.out.println("[mc_dlss/fabric/persistent-motion] "
                    + toPersistentFrameLogLine(persistentFrameCaptureSnapshot));
        }
    }

    private static String toPersistentFrameLogLine(
            MinecraftPersistentMotionFrameCaptureSnapshot snapshot) {
        MinecraftPersistentFrameCaptureSnapshot frame = snapshot.frame();
        return "state=" + frame.state()
                + " success=" + snapshot.success()
                + " size=" + frame.width() + "x" + frame.height()
                + " frames=" + frame.successfulFrames() + "/" + frame.targetFrames()
                + " retained=" + frame.retainedFrames() + "/" + frame.targetRetainedFrames()
                + " slotAUses=" + frame.slotAUses() + " slotBUses=" + frame.slotBUses()
                + " colorHashChanges=" + frame.colorHashChanges()
                + " depthHashChanges=" + frame.depthHashChanges()
                + " motionHashChanges=" + snapshot.motionHashChanges()
                + " glMotionHash=" + snapshot.openGlMotionHash()
                + " d3d12MotionHash=" + snapshot.d3d12MotionHash()
                + " motionFinite=" + snapshot.finiteVectorCount()
                + " motionNonZero=" + snapshot.nonZeroVectorCount()
                + " motionOut=" + snapshot.outOfBoundsVectorCount()
                + " stationary=" + snapshot.stationaryPhaseObserved()
                + " moving=" + snapshot.cameraMotionPhaseObserved()
                + " resets=" + snapshot.resetFrameCount()
                + " averageMs=" + frame.averageMilliseconds()
                + " maximumMs=" + frame.maximumMilliseconds()
                + " resourcesReleased=" + frame.resourcesReleased()
                + " message=" + snapshot.message();
    }
}
