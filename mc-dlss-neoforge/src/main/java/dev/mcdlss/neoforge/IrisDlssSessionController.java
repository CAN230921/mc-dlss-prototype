package dev.mcdlss.neoforge;

import dev.mcdlss.core.IrisDlssSessionKey;
import dev.mcdlss.core.IrisRenderTargetSnapshot;
import dev.mcdlss.core.NativeLiveDlssSessionInfo;
import dev.mcdlss.core.NativeLiveDlssFrameResult;
import dev.mcdlss.core.NativePluginDirectoryResolver;
import dev.mcdlss.core.DlssQualityMode;
import dev.mcdlss.core.DlssRenderPolicy;
import dev.mcdlss.core.DlssSlotScheduler;
import dev.mcdlss.core.DlssRetryGate;
import dev.mcdlss.core.DlssUserConfig;
import dev.mcdlss.core.NativeTemporalConstants;
import dev.mcdlss.core.NativeUpscalerExecutionMode;
import dev.mcdlss.core.FrameTimeStatistics;
import dev.mcdlss.core.StableCandidateGate;
import dev.mcdlss.core.UpscalerBackendMode;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.nio.file.Files;
import java.nio.file.Path;

public final class IrisDlssSessionController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final McDlssNeoForgeEntrypoint ENTRYPOINT = new McDlssNeoForgeEntrypoint();
    private static final boolean DXGI_STAGE2_ENABLED =
            Boolean.getBoolean("mcDlss.experimentalDxgiPresentation");

    private static IrisDlssSessionKey key;
    private static NativeLiveDlssSessionInfo session;
    private static IrisDepthDownsampleShader depthShader;
    private static IrisMotionVectorShader motionShader;
    private static final IrisDlssGlInputSlot[] slots = new IrisDlssGlInputSlot[2];
    private static long populatedFrames;
    private static long completedEvaluations;
    private static int lastCompletedSlot = -1;
    private static final int[] slotUses = new int[2];
    private static final boolean[] pendingOutputs = new boolean[2];
    private static final long[] pendingSignals = new long[2];
    private static final long[] submissionOrdinals = new long[2];
    private static long nextSubmissionOrdinal;
    private static int transitionalEvaluations;
    private static long resetFrames;
    private static long droppedFrames;
    private static String status = "waiting for Iris targets";
    private static final DlssRetryGate<OpenAttemptKey> RETRY_GATE = new DlssRetryGate<>(10_000L);
    private static DlssQualityMode activeQualityMode;
    private static NativeUpscalerExecutionMode activeExecutionMode;
    private static UpscalerBackendMode activeBackend;
    private static boolean activeFsr3FrameGeneration;
    private static long lastFrameNanos;
    private static float frameTimeMilliseconds = 16.67F;
    private static long appliedConfigRevision = -1;
    private static IrisTemporalCapture.Frame pendingTemporalFrame;
    private static int finalPassSourceTexture;
    private static int finalPassReplacementTexture;
    private static boolean finalPassOverrideActive;
    private static int dxgiPresentationSlot = -1;
    private static long dxgiPresentationSignal;
    private static final FrameTimeStatistics FRAME_TIMES = new FrameTimeStatistics(300);
    private static final StableCandidateGate<OpenAttemptKey> TARGET_STABILITY =
            new StableCandidateGate<>(2);

    public static void onClientTick(ClientTickEvent.Post event) {
        DlssUserConfig config = McDlssConfigManager.current();
        long configRevision = McDlssConfigManager.revision();
        if (DlssInternalResolutionState.synchronize(config)) {
            closeSession("Iris internal resolution changed");
            return;
        }
        DlssWorldRenderTargetController.update();
        if (!config.enabled()) {
            TARGET_STABILITY.clear();
            if (session != null || key != null) closeSession("DLSS 已关闭");
            status = "DLSS 已关闭";
            appliedConfigRevision = configRevision;
            return;
        }
        IrisRenderTargetSnapshot targets = McDlssNeoForgeIrisDiagnostics.current();
        if (!targets.targetsUsable()) {
            closeSession("Iris targets unavailable");
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            closeSession("waiting for a rendered world");
            return;
        }
        int outputWidth = client.getWindow().getWidth();
        int outputHeight = client.getWindow().getHeight();
        if (outputWidth <= 0 || outputHeight <= 0) {
            closeSession("output window unavailable");
            return;
        }
        IrisDlssSessionKey next = new IrisDlssSessionKey(
                targets.generation(), targets.width(), targets.height(), outputWidth, outputHeight);
        OpenAttemptKey attempt = new OpenAttemptKey(next, config.qualityMode(), configRevision);
        if (key != null && !key.requiresReopen(next) && session != null && session.available()
                && activeQualityMode == config.qualityMode()
                && appliedConfigRevision == configRevision) {
            return;
        }
        if (!TARGET_STABILITY.observe(attempt)) {
            status = "REBUILDING";
            return;
        }
        long now = System.currentTimeMillis();
        if (!RETRY_GATE.canAttempt(attempt, now)) {
            status = "DLSS 初始化失败，稍后自动重试";
            return;
        }
        closeSession("Iris target generation or output size changed");
        if (openSession(next, config, configRevision)) {
            RETRY_GATE.clear();
            TARGET_STABILITY.clear();
        } else {
            RETRY_GATE.recordFailure(attempt, now);
        }
    }

    public static String status() {
        return status + " | " + FRAME_TIMES.snapshot().compact();
    }

    public static String basicStatus() {
        DlssUserConfig config = McDlssConfigManager.current();
        String performance = FRAME_TIMES.snapshot().compact();
        if (!config.enabled()) return "DLSS：关闭 | " + performance;
        if (session != null && session.available() && completedEvaluations > 0) {
            return "DLSS：开启 | " + qualityLabel(config.qualityMode())
                    + " | " + session.renderWidth() + "x" + session.renderHeight()
                    + " -> " + session.outputWidth() + "x" + session.outputHeight()
                    + " | " + performance;
        }
        if ("REBUILDING".equals(status) || status.startsWith("waiting")) {
            return "DLSS：正在准备 | " + qualityLabel(config.qualityMode()) + " | " + performance;
        }
        if (status.startsWith("OPEN")) {
            return "DLSS：已初始化，等待首帧 | " + qualityLabel(config.qualityMode())
                    + " | " + performance;
        }
        return "DLSS：初始化失败，将自动重试 | " + performance;
    }

    public static void shutdown() {
        FrameGenerationHudlessCapture.shutdown();
        System.out.println("[mc_dlss/shutdown] BEGIN world target");
        DlssWorldRenderTargetController.shutdown();
        System.out.println("[mc_dlss/shutdown] END world target; BEGIN live session");
        closeSession("Minecraft client closing");
        ENTRYPOINT.shutdownLiveDlssProcess();
        System.out.println("[mc_dlss/shutdown] END live session");
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || session == null || key == null) return;
        IrisRenderTargetSnapshot targets = McDlssNeoForgeIrisDiagnostics.current();
        if (!targets.targetsUsable() || targets.generation() != key.generation()) return;
        try {
            pendingTemporalFrame = IrisTemporalCapture.capture(
                    event, targets.generation(), session.renderWidth(), session.renderHeight());
        } catch (RuntimeException error) {
            closeSession("temporal frame failed: " + messageOf(error));
        }
    }

    public static void beforeIrisFinalPass() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            frameTimeMilliseconds = Math.max(1.0F,
                    Math.min(1000.0F, (now - lastFrameNanos) / 1_000_000.0F));
        }
        lastFrameNanos = now;
        FRAME_TIMES.record(now);
        finalPassOverrideActive = false;
        if (session == null || key == null || pendingTemporalFrame == null) return;
        IrisRenderTargetSnapshot targets = McDlssNeoForgeIrisDiagnostics.current();
        if (!targets.targetsUsable() || targets.generation() != key.generation()) return;
        try {
            int presentationSlot = activeExecutionMode == NativeUpscalerExecutionMode.FAST
                    ? acquireReadyFastOutput() : -1;
            long previousCompleted = completedEvaluations;
            populateInputs(targets, pendingTemporalFrame, presentationSlot);
            if (activeExecutionMode != NativeUpscalerExecutionMode.FAST
                    && completedEvaluations > previousCompleted) {
                presentationSlot = lastCompletedSlot;
            }
            if (presentationSlot >= 0) {
                IrisDlssGlInputSlot slot = slots[presentationSlot];
                finalPassSourceTexture = targets.colorMainTexture();
                finalPassReplacementTexture = slot.outputTexture();
                finalPassOverrideActive = true;
                if (DXGI_STAGE2_ENABLED && activeBackend == UpscalerBackendMode.FSR3
                        && activeFsr3FrameGeneration) {
                    dxgiPresentationSlot = presentationSlot;
                    dxgiPresentationSignal = pendingSignals[presentationSlot];
                }
            }
        } catch (RuntimeException error) {
            closeSession("pre-final DLSS failed: " + messageOf(error));
        }
    }

    private static int acquireReadyFastOutput() {
        int selected = -1;
        long newest = Long.MIN_VALUE;
        for (int index = 0; index < 2; index++) {
            if (pendingOutputs[index]
                    && ENTRYPOINT.isLiveUpscalerSlotReady(session.sessionId(), index)
                    && submissionOrdinals[index] > newest) {
                selected = index;
                newest = submissionOrdinals[index];
            }
        }
        if (selected < 0) return -1;
        for (int index = 0; index < 2; index++) {
            if (index != selected && pendingOutputs[index]
                    && ENTRYPOINT.isLiveUpscalerSlotReady(session.sessionId(), index)) {
                pendingOutputs[index] = false;
                droppedFrames++;
            }
        }
        IrisDlssGlInputSlot slot = slots[selected];
        if (slot == null) return -1;
        slot.waitForOutput(pendingSignals[selected]);
        pendingOutputs[selected] = false;
        completeFastFrame(selected, pendingSignals[selected]);
        return selected;
    }

    public static void afterIrisFinalPass() {
        finalPassOverrideActive = false;
        finalPassSourceTexture = 0;
        finalPassReplacementTexture = 0;
    }

    public static int finalPassTextureOverride(int originalTexture) {
        return finalPassOverrideActive && originalTexture == finalPassSourceTexture
                ? finalPassReplacementTexture : 0;
    }

    public static boolean presentDxgiRealFrame(long glfwWindowHandle) {
        int slotIndex = dxgiPresentationSlot;
        long signalValue = dxgiPresentationSignal;
        dxgiPresentationSlot = -1;
        dxgiPresentationSignal = 0L;
        if (!DXGI_STAGE2_ENABLED || session == null || glfwWindowHandle == 0L
                || slotIndex < 0 || slotIndex >= slots.length || signalValue <= 0L
                || activeBackend != UpscalerBackendMode.FSR3
                || !activeFsr3FrameGeneration) return false;
        try {
            long windowHandle = GLFWNativeWin32.glfwGetWin32Window(glfwWindowHandle);
            int state = ENTRYPOINT.presentLiveFsr3DxgiFrame(
                    session.sessionId(), windowHandle, slotIndex, signalValue,
                    session.outputWidth(), session.outputHeight());
            if (state == 3) {
                activeFsr3FrameGeneration = false;
                status = "FSR3 FG fallback: DXGI presentation failed";
                LOGGER.warn("[mc_dlss/dxgi] presentation entered fallback");
            } else if (state == 2 && completedEvaluations % 120 == 0) {
                LOGGER.info("[mc_dlss/dxgi] ACTIVE slot={} signal={}",
                        slotIndex, signalValue);
            }
            return state == 2;
        } catch (RuntimeException error) {
            activeFsr3FrameGeneration = false;
            status = "FSR3 FG fallback: " + messageOf(error);
            return false;
        }
    }

    private static boolean openSession(
            IrisDlssSessionKey next, DlssUserConfig config, long configRevision) {
        try {
            Path nativeDirectory = firstNativeDirectory();
            Path logDirectory = Path.of("work", "streamline-iris-neoforge").toAbsolutePath();
            Files.createDirectories(logDirectory);
            NativeLiveDlssSessionInfo opened = config.backend() == UpscalerBackendMode.FSR3
                    ? ENTRYPOINT.openLiveFsr3Session(
                            next.outputWidth(), next.outputHeight(),
                            nativeDirectory.resolve("amd_fidelityfx_loader_dx12.dll").toString(),
                            config.qualityMode())
                    : ENTRYPOINT.openLiveDlssSession(
                            next.outputWidth(), next.outputHeight(),
                            nativeDirectory.toString(), logDirectory.toString(),
                            config.qualityMode());
            if (opened == null || !opened.available()) {
                status = opened == null ? "native session returned null" : opened.message();
                System.out.println("[mc_dlss/iris-session] BLOCKED " + status);
                LOGGER.warn("[mc_dlss/iris-session] BLOCKED {}", status);
                return false;
            }
            long[] color = opened.colorTextureHandles();
            long[] depth = opened.depthTextureHandles();
            long[] motion = opened.motionTextureHandles();
            long[] output = opened.outputTextureHandles();
            long[] fences = opened.fenceHandles();
            long[] generated = opened.generatedTextureHandles();
            IrisDepthDownsampleShader newDepthShader = new IrisDepthDownsampleShader();
            IrisMotionVectorShader newMotionShader = new IrisMotionVectorShader();
            IrisDlssGlInputSlot first = null;
            IrisDlssGlInputSlot second = null;
            try {
                first = new IrisDlssGlInputSlot(
                        opened.renderWidth(), opened.renderHeight(),
                        opened.outputWidth(), opened.outputHeight(),
                        color[0], depth[0], motion[0], output[0], fences[0],
                        generated[0]);
                second = new IrisDlssGlInputSlot(
                        opened.renderWidth(), opened.renderHeight(),
                        opened.outputWidth(), opened.outputHeight(),
                        color[1], depth[1], motion[1], output[1], fences[1],
                        generated[1]);
            } catch (RuntimeException error) {
                closeQuietly(second);
                closeQuietly(first);
                closeQuietly(newDepthShader);
                closeQuietly(newMotionShader);
                ENTRYPOINT.closeLiveDlssSession(opened.sessionId());
                throw error;
            }
            key = next;
            session = opened;
            activeQualityMode = config.qualityMode();
            activeBackend = config.backend();
            activeFsr3FrameGeneration = config.fsr3FrameGeneration();
            activeExecutionMode = config.backend() == UpscalerBackendMode.FSR3
                    ? NativeUpscalerExecutionMode.FAST
                    : DlssRenderPolicy.from(config).executionMode();
            appliedConfigRevision = configRevision;
            depthShader = newDepthShader;
            motionShader = newMotionShader;
            slots[0] = first;
            slots[1] = second;
            populatedFrames = 0;
            completedEvaluations = 0;
            lastCompletedSlot = -1;
            slotUses[0] = 0;
            slotUses[1] = 0;
            pendingOutputs[0] = false;
            pendingOutputs[1] = false;
            pendingSignals[0] = 0;
            pendingSignals[1] = 0;
            submissionOrdinals[0] = 0;
            submissionOrdinals[1] = 0;
            nextSubmissionOrdinal = 0;
            transitionalEvaluations = 0;
            resetFrames = 0;
            droppedFrames = 0;
            status = "OPEN id=" + opened.sessionId()
                    + " backend=" + activeBackend
                    + " input=" + opened.renderWidth() + "x" + opened.renderHeight()
                    + " output=" + opened.outputWidth() + "x" + opened.outputHeight()
                    + " irisGeneration=" + next.generation();
            System.out.println("[mc_dlss/iris-session] " + status);
            return true;
        } catch (Exception error) {
            status = "BLOCKED " + messageOf(error);
            System.out.println("[mc_dlss/iris-session] " + status);
            LOGGER.warn("[mc_dlss/iris-session] {}", status, error);
            return false;
        }
    }

    private static void closeSession(String reason) {
        closeQuietly(slots[1]);
        closeQuietly(slots[0]);
        slots[0] = null;
        slots[1] = null;
        closeQuietly(depthShader);
        depthShader = null;
        closeQuietly(motionShader);
        motionShader = null;
        if (session != null && session.available()) {
            ENTRYPOINT.closeLiveDlssSession(session.sessionId());
            System.out.println("[mc_dlss/iris-session] CLOSED id="
                    + session.sessionId() + " reason=" + reason);
        }
        session = null;
        key = null;
        activeQualityMode = null;
        activeExecutionMode = null;
        activeBackend = null;
        activeFsr3FrameGeneration = false;
        lastCompletedSlot = -1;
        pendingOutputs[0] = false;
        pendingOutputs[1] = false;
        pendingTemporalFrame = null;
        dxgiPresentationSlot = -1;
        dxgiPresentationSignal = 0L;
        afterIrisFinalPass();
        status = reason;
    }

    private static void populateInputs(
            IrisRenderTargetSnapshot targets,
            IrisTemporalCapture.Frame temporalFrame,
            int excludedSlot) {
        int preferredSlot = (int) (populatedFrames & 1L);
        boolean slot0Ready = excludedSlot != 0 && !pendingOutputs[0]
                && ENTRYPOINT.isLiveUpscalerSlotReady(session.sessionId(), 0);
        boolean slot1Ready = excludedSlot != 1 && !pendingOutputs[1]
                && ENTRYPOINT.isLiveUpscalerSlotReady(session.sessionId(), 1);
        int slotIndex = DlssSlotScheduler.select(
                preferredSlot,
                slot0Ready, slot1Ready);
        if (slotIndex < 0) {
            droppedFrames++;
            if (droppedFrames == 1 || droppedFrames % 120 == 0) {
                status = "ACTIVE waiting for free DLSS slot dropped=" + droppedFrames;
            }
            return;
        }
        IrisDlssGlInputSlot slot = slots[slotIndex];
        if (slot == null || depthShader == null || motionShader == null) return;
        try {
            slot.populate(
                    targets.colorMainTexture(), targets.depthTexture(),
                    targets.width(), targets.height(), depthShader, motionShader, temporalFrame);
            if (temporalFrame.constants().reset()) resetFrames++;
            populatedFrames++;
            if (populatedFrames == 1) {
                status += " sharedInputs=READY";
                System.out.println("[mc_dlss/iris-session] " + status);
            }
            evaluateFrame(slotIndex, slot, temporalFrame.constants());
        } catch (RuntimeException error) {
            closeSession("shared input copy failed: " + messageOf(error));
        }
    }

    private static void evaluateFrame(
            int slotIndex,
            IrisDlssGlInputSlot slot,
            NativeTemporalConstants constants) {
        long waitValue = slotUses[slotIndex] * 2L + 1L;
        long signalValue = 2L;
        signalValue = waitValue + 1L;
        slot.signalInputs(waitValue);
        boolean submitted = activeBackend == UpscalerBackendMode.FSR3
                ? ENTRYPOINT.submitLiveFsr3Upscale(
                        session.sessionId(), slotIndex, waitValue, signalValue,
                        constants, frameTimeMilliseconds, activeFsr3FrameGeneration)
                : ENTRYPOINT.submitLiveDlssEvaluation(
                        session.sessionId(), slotIndex, waitValue, signalValue,
                        constants,
                        activeExecutionMode == null ? NativeUpscalerExecutionMode.FAST
                                : activeExecutionMode);
        if (!submitted) {
            throw new IllegalStateException("native reset-frame submission failed");
        }
        slotUses[slotIndex]++;
        if (activeExecutionMode == NativeUpscalerExecutionMode.FAST) {
            pendingOutputs[slotIndex] = true;
            pendingSignals[slotIndex] = signalValue;
            submissionOrdinals[slotIndex] = ++nextSubmissionOrdinal;
            return;
        }
        slot.waitForOutput(signalValue);
        NativeLiveDlssFrameResult result = ENTRYPOINT.inspectLiveDlssEvaluation(
                session.sessionId(), slotIndex, signalValue);
        if (result == null) {
            throw new IllegalStateException("native reset-frame inspection returned null");
        }
        if (!result.available() && "READY".equals(result.stage())
                && transitionalEvaluations < 8) {
            transitionalEvaluations++;
            status = "OPEN id=" + session.sessionId()
                    + " transitional=" + transitionalEvaluations + "/8";
            return;
        }
        if (!result.available()) {
            throw new IllegalStateException(result == null
                    ? "native reset-frame inspection returned null" : result.message());
        }
        completedEvaluations++;
        lastCompletedSlot = slotIndex;
        status = "ACTIVE input=" + session.renderWidth() + "x" + session.renderHeight()
                + " output=" + session.outputWidth() + "x" + session.outputHeight()
                + " frames=" + completedEvaluations + " resets=" + resetFrames
                + " dropped=" + droppedFrames
                + " slot=" + slotIndex;
        if (completedEvaluations == 1 || completedEvaluations % 30 == 0) {
            System.out.println("[mc_dlss/iris-session] " + status
                    + " completedFence=" + result.completedFenceValue());
        }
    }

    private static void completeFastFrame(int slotIndex, long signalValue) {
        completedEvaluations++;
        lastCompletedSlot = slotIndex;
        status = "ACTIVE FAST HDR input=" + session.renderWidth() + "x" + session.renderHeight()
                + " output=" + session.outputWidth() + "x" + session.outputHeight()
                + " frames=" + completedEvaluations + " resets=" + resetFrames
                + " slot=" + slotIndex;
        if (completedEvaluations == 1 || completedEvaluations % 120 == 0) {
            System.out.println("[mc_dlss/iris-session] " + status
                    + " queuedFence=" + signalValue);
        }
    }

    private static Path firstNativeDirectory() {
        return NativePluginDirectoryResolver.resolve(
                System.getProperty("mcDlss.packagedNativePath", ""),
                System.getProperty("java.library.path", ""));
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String qualityLabel(DlssQualityMode qualityMode) {
        return switch (qualityMode) {
            case QUALITY -> "质量";
            case BALANCED -> "平衡";
            case PERFORMANCE -> "性能";
            case ULTRA_PERFORMANCE -> "超级性能";
            case DLAA -> "DLAA";
        };
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private IrisDlssSessionController() {
    }

    private record OpenAttemptKey(
            IrisDlssSessionKey sessionKey, DlssQualityMode qualityMode, long configRevision) {
    }
}
