package dev.mcdlss.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcdlss.core.NativeLiveDlssFrameResult;
import dev.mcdlss.core.NativeLiveDlssSessionInfo;
import dev.mcdlss.core.NativeTemporalConstants;
import dev.mcdlss.core.NativeUpscalerExecutionMode;
import dev.mcdlss.fabric.mixin.access.MinecraftClientFramebufferAccessor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class LiveDlssPresentationController
        implements LiveDlssRenderWorldHooks.Handler, AutoCloseable {
    private static final int TARGET_FRAMES = 30;
    private static final int TARGET_RETAINED_FRAMES = 300;

    private final McDlssFabricEntrypoint entrypoint;
    private final LiveDlssFramebufferRedirector redirector =
            new LiveDlssFramebufferRedirector();
    private final LiveDlssFrameBoundary<TemporalCapture> frameBoundary =
            new LiveDlssFrameBoundary<>();
    private final LiveDlssGlSlot[] slots = new LiveDlssGlSlot[2];
    private final int[] slotUseCounts = new int[2];
    private final LiveUpscalerModeTracker modeTracker = new LiveUpscalerModeTracker();
    private final int injectedFailureFrame =
            Integer.getInteger("mcDlss.liveFailureFrame", -1);

    private LiveDlssPresentationState state = LiveDlssPresentationState.INITIALIZING;
    private NativeLiveDlssSessionInfo session;
    private int renderWidth;
    private int renderHeight;
    private int outputWidth;
    private int outputHeight;
    private SimpleFramebuffer lowResolutionFramebuffer;
    private MinecraftDepthExtractionShader depthShader;
    private MinecraftMotionVectorShader motionShader;
    private CameraTemporalFrame previousTemporalFrame;
    private Object activeWorld;
    private long temporalFrameIndex;
    private long evaluationFrameIndex;
    private int successfulFrames;
    private int retainedFrames;
    private int slotAUses;
    private int slotBUses;
    private int resizeCount;
    private int outputHashChanges;
    private long previousOutputHash;
    private boolean hasPreviousOutputHash;
    private String openGlOutputHash = "";
    private String d3d12OutputHash = "";
    private int finiteChannelCount;
    private int nonBlackPixelCount;
    private boolean outputNonUniform;
    private int resetFrameCount;
    private int fallbackFrameCount;
    private int transientTemporalFailures;
    private boolean originalFramebufferRestored = true;
    private boolean resourcesRetained;
    private boolean resourcesReleased;
    private String failureStage = "";
    private double totalMilliseconds;
    private double maximumMilliseconds;
    private boolean automaticMotionApplied;
    private boolean automaticMotionRestored;
    private float automaticMotionOriginalYaw;
    private String message = "Initializing live DLSS presentation";
    private RuntimeException pendingTemporalFailure;
    private volatile LiveDlssPresentationSnapshot snapshot = createSnapshot();

    public LiveDlssPresentationController(McDlssFabricEntrypoint entrypoint) {
        if (entrypoint == null) {
            throw new IllegalArgumentException("Fabric entrypoint is required");
        }
        this.entrypoint = entrypoint;
    }

    @Override
    public void beforeWorldRender(RenderTickCounter tickCounter) {
        if (state == LiveDlssPresentationState.FAILED
                || state == LiveDlssPresentationState.DISABLED) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !RenderSystem.isOnRenderThread()) return;
        try {
            Framebuffer original = client.getFramebuffer();
            if (original == null || original.fbo <= 0
                    || original.textureWidth <= 0 || original.textureHeight <= 0) {
                throw new FrameFailure("FRAMEBUFFER", "Main framebuffer is unavailable");
            }
            if (client.world != activeWorld) {
                activeWorld = client.world;
                resetTemporalHistory();
            }
            ensureResources(original.textureWidth, original.textureHeight);
            lowResolutionFramebuffer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            lowResolutionFramebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            redirector.begin(original, lowResolutionFramebuffer);
            frameBoundary.begin();
            pendingTemporalFailure = null;
            setClientFramebuffer(client, lowResolutionFramebuffer);
            lowResolutionFramebuffer.beginWrite(true);
            originalFramebufferRestored = false;
            publishSnapshot();
        } catch (RuntimeException error) {
            restoreOriginalIfNeeded(client);
            failPermanently(stageOf(error, "INITIALIZE"), messageOf(error), false);
        }
    }

    public void onWorldRenderEnd(WorldRenderContext context) {
        if (!redirector.active()) return;
        try {
            frameBoundary.captureAtWorldEnd(captureTemporalFrame(context));
        } catch (RuntimeException error) {
            pendingTemporalFailure = error;
        }
    }

    public void restoreLowResolutionViewport() {
        if (!redirector.active()) return;
        RenderSystem.viewport(0, 0, renderWidth, renderHeight);
    }

    private void presentWorldFrame(
            TemporalCapture temporal, RuntimeException temporalFailure) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer source = (Framebuffer) redirector.lowResolutionFramebuffer();
        Framebuffer original = (Framebuffer) redirector.originalFramebuffer();
        long started = System.nanoTime();
        String stage = "TEMPORAL";
        try {
            if (temporalFailure != null) throw temporalFailure;
            if (temporal == null) {
                throw new FrameFailure("TEMPORAL", "Temporal frame was not captured");
            }
            int preferredSlot = (int) (evaluationFrameIndex & 1L);
            int slotIndex = LiveUpscalerSlotScheduler.select(
                    entrypoint.isLiveUpscalerSlotReady(session.sessionId(), 0),
                    entrypoint.isLiveUpscalerSlotReady(session.sessionId(), 1),
                    preferredSlot);
            if (slotIndex < 0) {
                restoreOriginalIfNeeded(client);
                original.beginWrite(true);
                linearFallback(source, original);
                fallbackFrameCount++;
                modeTracker.recordBusyFallback();
                resetTemporalHistory();
                if (modeTracker.failed()) {
                    failPermanently(
                            "SLOT_BUSY", "Both live upscaler slots remained busy", true);
                    return;
                }
                message = "Live upscaler slots busy; presented fallback frame";
                publishSnapshot();
                return;
            }
            LiveDlssGlSlot slot = slots[slotIndex];
            if (slot == null) {
                throw new FrameFailure("GL_RESOURCES", "Live DLSS GL slot is unavailable");
            }

            stage = "INPUTS";
            slot.populateInputs(source, depthShader, motionShader, temporal.reprojection());
            if (evaluationFrameIndex + 1L == injectedFailureFrame) {
                throw new FrameFailure(
                        "INJECTED_EVALUATE", "Injected live DLSS evaluation failure");
            }

            PersistentFenceValues fence =
                    PersistentFenceValues.forUse(slotUseCounts[slotIndex]);
            stage = "GL_SIGNAL";
            slot.signalInputs(fence.waitValue());
            stage = "EVALUATE";
            NativeUpscalerExecutionMode executionMode = modeTracker.executionMode();
            if (!entrypoint.submitLiveDlssEvaluation(
                    session.sessionId(), slotIndex, fence.waitValue(),
                    fence.signalValue(), temporal.constants(), executionMode)) {
                throw new FrameFailure("EVALUATE", "Native live DLSS submission failed");
            }
            stage = "GL_WAIT";
            NativeLiveDlssFrameResult nativeFrame = null;
            Fp16ColorFingerprint glFrame = null;
            if (executionMode == NativeUpscalerExecutionMode.VALIDATING) {
                slot.waitForOutput(fence.signalValue());
                stage = "INSPECT";
                nativeFrame = entrypoint.inspectLiveDlssEvaluation(
                        session.sessionId(), slotIndex, fence.signalValue());
                glFrame = slot.readOutputFingerprint();
                validateFrame(nativeFrame, glFrame, fence.signalValue());
            } else {
                slot.queueWaitForOutput(fence.signalValue());
            }

            stage = "PRESENT";
            restoreOriginalIfNeeded(client);
            original.beginWrite(true);
            slot.compositeTo(original);
            recordSuccessfulFrame(slotIndex, temporal, nativeFrame, glFrame,
                    executionMode, (System.nanoTime() - started) / 1_000_000.0);
        } catch (RuntimeException error) {
            String failureStage = stageOf(error, stage);
            restoreOriginalIfNeeded(client);
            boolean fallbackPresented = false;
            try {
                original.beginWrite(true);
                linearFallback(source, original);
                fallbackFrameCount++;
                fallbackPresented = true;
            } catch (RuntimeException fallbackError) {
                message = messageOf(error) + "; fallback: " + messageOf(fallbackError);
            }
            if (fallbackPresented && LiveDlssStartupFramePolicy.shouldRetry(
                    failureStage, evaluationFrameIndex, transientTemporalFailures)) {
                transientTemporalFailures++;
                resetTemporalHistory();
                state = LiveDlssPresentationState.CAPTURING;
                this.failureStage = "";
                message = "Skipped transitional world frame "
                        + transientTemporalFailures + "/8";
                resourcesRetained = true;
                resourcesReleased = false;
                publishSnapshot();
                return;
            }
            failPermanently(failureStage, messageOf(error), true);
        }
    }

    @Override
    public void afterWorldRender() {
        if (!redirector.active()) return;
        TemporalCapture temporal = frameBoundary.consumeAfterRenderWorld();
        RuntimeException temporalFailure = pendingTemporalFailure;
        pendingTemporalFailure = null;
        if (temporal != null || temporalFailure != null) {
            presentWorldFrame(temporal, temporalFailure);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer source = (Framebuffer) redirector.lowResolutionFramebuffer();
        Framebuffer original = (Framebuffer) redirector.originalFramebuffer();
        restoreOriginalIfNeeded(client);
        try {
            original.beginWrite(true);
            linearFallback(source, original);
            fallbackFrameCount++;
        } catch (RuntimeException error) {
            message = "World END callback missing; fallback failed: " + messageOf(error);
        }
        failPermanently(
                "WORLD_END", "World END callback did not present the DLSS frame", true);
    }

    @Override
    public boolean redirected() {
        return redirector.active();
    }

    public void onClientTick(MinecraftClient client) {
        if (client == null || client.world == null) {
            activeWorld = null;
            resetTemporalHistory();
        }
        publishSnapshot();
    }

    public LiveDlssPresentationSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        restoreOriginalIfNeeded(client);
        releaseResources();
        if (state != LiveDlssPresentationState.FAILED) {
            state = LiveDlssPresentationState.DISABLED;
            message = "Live DLSS presentation closed";
        }
        publishSnapshot();
    }

    private void ensureResources(int outputWidth, int outputHeight) {
        if (session != null
                && session.outputWidth() == outputWidth
                && session.outputHeight() == outputHeight) {
            return;
        }
        if (session != null) {
            resizeCount++;
            releaseResources();
            resetValidationProgress();
        }
        state = LiveDlssPresentationState.INITIALIZING;
        message = "Opening live DLSS Quality session";
        publishSnapshot();

        Path pluginDirectory = firstNativeDirectory();
        Path logDirectory = Path.of("work", "streamline-live").toAbsolutePath();
        try {
            Files.createDirectories(logDirectory);
        } catch (Exception error) {
            throw new FrameFailure("LOG_PATH", "Cannot create Streamline log directory");
        }
        NativeLiveDlssSessionInfo opened = entrypoint.openLiveDlssSession(
                outputWidth, outputHeight,
                pluginDirectory.toString(), logDirectory.toString());
        if (opened == null || !opened.available()) {
            throw new FrameFailure("INITIALIZE", opened == null
                    ? "Native live DLSS session is null" : opened.message());
        }
        LiveDlssResolutionContract.create(
                opened.renderWidth(), opened.renderHeight(),
                opened.outputWidth(), opened.outputHeight());

        LiveDlssGlSlot first = null;
        LiveDlssGlSlot second = null;
        SimpleFramebuffer low = null;
        MinecraftDepthExtractionShader newDepthShader = null;
        MinecraftMotionVectorShader newMotionShader = null;
        try {
            long[] color = opened.colorTextureHandles();
            long[] depth = opened.depthTextureHandles();
            long[] motion = opened.motionTextureHandles();
            long[] output = opened.outputTextureHandles();
            long[] fences = opened.fenceHandles();
            first = new LiveDlssGlSlot(
                    opened.renderWidth(), opened.renderHeight(), outputWidth, outputHeight,
                    color[0], depth[0], motion[0], output[0], fences[0]);
            second = new LiveDlssGlSlot(
                    opened.renderWidth(), opened.renderHeight(), outputWidth, outputHeight,
                    color[1], depth[1], motion[1], output[1], fences[1]);
            low = new SimpleFramebuffer(
                    opened.renderWidth(), opened.renderHeight(), true,
                    MinecraftClient.IS_SYSTEM_MAC);
            newDepthShader = new MinecraftDepthExtractionShader();
            newMotionShader = new MinecraftMotionVectorShader();
        } catch (RuntimeException error) {
            closeQuietly(newMotionShader);
            closeQuietly(newDepthShader);
            if (low != null) low.delete();
            closeQuietly(second);
            closeQuietly(first);
            entrypoint.closeLiveDlssSession(opened.sessionId());
            throw new FrameFailure("GL_RESOURCES", messageOf(error));
        }
        session = opened;
        renderWidth = opened.renderWidth();
        renderHeight = opened.renderHeight();
        this.outputWidth = opened.outputWidth();
        this.outputHeight = opened.outputHeight();
        slots[0] = first;
        slots[1] = second;
        lowResolutionFramebuffer = low;
        depthShader = newDepthShader;
        motionShader = newMotionShader;
        resourcesRetained = true;
        resourcesReleased = false;
        failureStage = "";
        resetTemporalHistory();
        state = LiveDlssPresentationState.CAPTURING;
        message = "Live DLSS Quality presentation active";
        publishSnapshot();
    }

    private TemporalCapture captureTemporalFrame(WorldRenderContext context) {
        if (context == null || session == null) {
            throw new FrameFailure("TEMPORAL", "World render context is unavailable");
        }
        Matrix4f projection = new Matrix4f(context.projectionMatrix());
        Matrix4f view = new Matrix4f(context.positionMatrix());
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        float[] viewProjectionValues = viewProjection.get(new float[16]);
        float[] projectionValues = projection.get(new float[16]);
        float[] viewValues = view.get(new float[16]);
        if (!allFinite(viewProjectionValues)
                || !allFinite(projectionValues) || !allFinite(viewValues)) {
            throw new FrameFailure("TEMPORAL",
                    "Non-finite temporal matrices: projection="
                            + Arrays.toString(projectionValues)
                            + " view=" + Arrays.toString(viewValues));
        }
        CameraTemporalFrame frame = new CameraTemporalFrame(
                viewProjectionValues, projectionValues, viewValues,
                session.renderWidth(), session.renderHeight(), ++temporalFrameIndex);
        CameraTemporalReprojection reprojection =
                CameraTemporalReprojection.between(previousTemporalFrame, frame);

        Matrix4f inverseProjection = new Matrix4f(projection).invert();
        Matrix4f inverseView = new Matrix4f(view).invert();
        double near = projection.m32() / (projection.m22() - 1.0f);
        double far = projection.m32() / (projection.m22() + 1.0f);
        float cameraNear = (float) Math.abs(near);
        float cameraFar = (float) Math.abs(far);
        float cameraFov = (float) (2.0 * Math.atan(1.0 / Math.abs(projection.m11())));
        var cameraPosition = context.camera().getPos();
        NativeTemporalConstants constants = MinecraftTemporalConstants.create(
                frame, reprojection, inverseProjection.get(new float[16]),
                new float[] {(float) cameraPosition.x, (float) cameraPosition.y,
                        (float) cameraPosition.z},
                normalized(inverseView.m10(), inverseView.m11(), inverseView.m12()),
                normalized(inverseView.m00(), inverseView.m01(), inverseView.m02()),
                normalized(-inverseView.m20(), -inverseView.m21(), -inverseView.m22()),
                cameraNear, cameraFar, cameraFov);
        if (Boolean.getBoolean("mcDlss.forceResetEveryFrame")) {
            constants = constants.withReset(true);
        }
        return new TemporalCapture(frame, reprojection, constants);
    }

    private void validateFrame(
            NativeLiveDlssFrameResult nativeFrame,
            Fp16ColorFingerprint glFrame,
            long signalValue) {
        if (nativeFrame == null || !nativeFrame.available()) {
            throw new FrameFailure(nativeFrame == null ? "INSPECT" : nativeFrame.stage(),
                    nativeFrame == null
                            ? "Native live DLSS frame is null" : nativeFrame.message());
        }
        boolean valid = "READY".equals(nativeFrame.stage())
                && nativeFrame.completedFenceValue() >= signalValue
                && nativeFrame.lastSubmittedSignal() == signalValue
                && nativeFrame.outputHash() == glFrame.hash()
                && nativeFrame.outputNonUniform() == glFrame.nonUniform()
                && nativeFrame.outputNonBlackPixelCount() == glFrame.nonBlackPixelCount()
                && glFrame.validForOutput();
        if (!valid) {
            throw new FrameFailure(
                    "VALIDATE", "OpenGL and D3D12 live DLSS outputs differ");
        }
    }

    private void recordSuccessfulFrame(
            int slotIndex,
            TemporalCapture temporal,
            NativeLiveDlssFrameResult nativeFrame,
            Fp16ColorFingerprint glFrame,
            NativeUpscalerExecutionMode executionMode,
            double elapsedMilliseconds) {
        if (executionMode == NativeUpscalerExecutionMode.VALIDATING) {
            if (hasPreviousOutputHash && previousOutputHash != glFrame.hash()) {
                outputHashChanges++;
            }
            previousOutputHash = glFrame.hash();
            hasPreviousOutputHash = true;
            openGlOutputHash = glFrame.hashHex();
            d3d12OutputHash = String.format(Locale.ROOT, "%016x", nativeFrame.outputHash());
            finiteChannelCount = glFrame.finiteChannelCount();
            nonBlackPixelCount = glFrame.nonBlackPixelCount();
            outputNonUniform = glFrame.nonUniform();
            modeTracker.recordValidatedFrame();
        } else {
            modeTracker.recordFastFrame();
        }
        if (temporal.reprojection().reset()) resetFrameCount++;
        previousTemporalFrame = temporal.frame();
        transientTemporalFailures = 0;
        slotUseCounts[slotIndex]++;
        evaluationFrameIndex++;
        successfulFrames = modeTracker.validatedFrames();
        retainedFrames = modeTracker.fastFrames();
        if (slotIndex == 0) slotAUses++; else slotBUses++;
        if (modeTracker.dlssReady()) {
            state = LiveDlssPresentationState.COMPLETE;
            message = "Live DLSS fast presentation verified";
        } else if (successfulFrames >= TARGET_FRAMES) {
            state = LiveDlssPresentationState.RETAINING;
            message = "Fast GPU frame " + retainedFrames + "/" + TARGET_RETAINED_FRAMES;
        } else {
            state = LiveDlssPresentationState.CAPTURING;
            message = "Verified live DLSS frame " + successfulFrames + "/" + TARGET_FRAMES;
        }
        totalMilliseconds += elapsedMilliseconds;
        maximumMilliseconds = Math.max(maximumMilliseconds, elapsedMilliseconds);
        applyAutomaticMotionTest();
        resourcesRetained = true;
        resourcesReleased = false;
        originalFramebufferRestored = true;
        publishSnapshot();
    }

    private void failPermanently(String stage, String failureMessage, boolean frameFallback) {
        failureStage = stage == null ? "UNKNOWN" : stage;
        if (message == null || !message.contains("fallback:")) {
            message = failureMessage == null ? "Live DLSS presentation failed" : failureMessage;
        }
        state = LiveDlssPresentationState.FAILED;
        previousTemporalFrame = null;
        releaseResources();
        originalFramebufferRestored = !redirector.active();
        if (!frameFallback) fallbackFrameCount = Math.max(0, fallbackFrameCount);
        publishSnapshot();
    }

    private Framebuffer restoreOriginalIfNeeded(MinecraftClient client) {
        if (!redirector.active()) return client == null ? null : client.getFramebuffer();
        Framebuffer original = (Framebuffer) redirector.restore();
        setClientFramebuffer(client, original);
        originalFramebufferRestored = true;
        return original;
    }

    private void releaseResources() {
        closeQuietly(slots[0]);
        closeQuietly(slots[1]);
        slots[0] = null;
        slots[1] = null;
        closeQuietly(motionShader);
        closeQuietly(depthShader);
        motionShader = null;
        depthShader = null;
        if (lowResolutionFramebuffer != null) {
            try {
                lowResolutionFramebuffer.delete();
            } catch (RuntimeException ignored) {
            }
            lowResolutionFramebuffer = null;
        }
        if (session != null) {
            entrypoint.closeLiveDlssSession(session.sessionId());
            session = null;
        }
        resourcesRetained = false;
        resourcesReleased = true;
    }

    private void resetValidationProgress() {
        successfulFrames = 0;
        retainedFrames = 0;
        slotAUses = 0;
        slotBUses = 0;
        outputHashChanges = 0;
        hasPreviousOutputHash = false;
        slotUseCounts[0] = 0;
        slotUseCounts[1] = 0;
        evaluationFrameIndex = 0;
        modeTracker.resetForResize();
        automaticMotionApplied = false;
        automaticMotionRestored = false;
        resetTemporalHistory();
    }

    private void applyAutomaticMotionTest() {
        if (!Boolean.getBoolean("mcDlss.autoMotionTest")) return;
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (!automaticMotionApplied && evaluationFrameIndex >= 30) {
            automaticMotionOriginalYaw = player.getYaw();
            player.setYaw(automaticMotionOriginalYaw + 5.0f);
            automaticMotionApplied = true;
        } else if (automaticMotionApplied && !automaticMotionRestored
                && evaluationFrameIndex >= 45) {
            player.setYaw(automaticMotionOriginalYaw);
            automaticMotionRestored = true;
        }
    }

    private void resetTemporalHistory() {
        previousTemporalFrame = null;
        temporalFrameIndex = 0;
    }

    private void publishSnapshot() {
        snapshot = createSnapshot();
    }

    private LiveDlssPresentationSnapshot createSnapshot() {
        long measuredFrames = evaluationFrameIndex;
        double average = measuredFrames == 0 ? 0.0 : totalMilliseconds / measuredFrames;
        return new LiveDlssPresentationSnapshot(
                state, renderWidth, renderHeight, outputWidth, outputHeight,
                successfulFrames, TARGET_FRAMES, retainedFrames, TARGET_RETAINED_FRAMES,
                slotAUses, slotBUses, resizeCount, outputHashChanges,
                openGlOutputHash, d3d12OutputHash, finiteChannelCount,
                nonBlackPixelCount, outputNonUniform, resetFrameCount,
                fallbackFrameCount, originalFramebufferRestored,
                resourcesRetained, resourcesReleased, failureStage,
                average, maximumMilliseconds, message);
    }

    private static Path firstNativeDirectory() {
        String value = System.getProperty("java.library.path", "");
        if (value.isBlank()) {
            throw new FrameFailure("PLUGIN_PATH", "java.library.path is empty");
        }
        String first = value.split(java.util.regex.Pattern.quote(File.pathSeparator), 2)[0];
        Path path = Path.of(first).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new FrameFailure("PLUGIN_PATH", "Native plugin directory is unavailable");
        }
        return path;
    }

    private static void linearFallback(Framebuffer source, Framebuffer target) {
        if (source == null || target == null || source.fbo <= 0 || target.fbo <= 0) {
            throw new IllegalArgumentException("Fallback framebuffers are invalid");
        }
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
            GL30.glBlitFramebuffer(
                    0, 0, source.textureWidth, source.textureHeight,
                    0, 0, target.textureWidth, target.textureHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException(String.format(
                        Locale.ROOT, "Fallback blit failed with OpenGL error 0x%04x", error));
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static float[] normalized(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (!Float.isFinite(length) || length <= 0.000001f) {
            throw new FrameFailure("TEMPORAL", "Camera basis is invalid");
        }
        return new float[] {x / length, y / length, z / length};
    }

    private static boolean allFinite(float[] values) {
        for (float value : values) {
            if (!Float.isFinite(value)) return false;
        }
        return true;
    }

    private static void setClientFramebuffer(MinecraftClient client, Framebuffer framebuffer) {
        if (client == null || framebuffer == null) return;
        ((MinecraftClientFramebufferAccessor) (Object) client)
                .mcDlss$setFramebuffer(framebuffer);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static String stageOf(RuntimeException error, String fallback) {
        return error instanceof FrameFailure failure ? failure.stage : fallback;
    }

    private static String messageOf(Throwable error) {
        if (error == null) return "Unknown live DLSS failure";
        String result = error.getMessage();
        return result == null || result.isBlank()
                ? error.getClass().getSimpleName() : result;
    }

    private record TemporalCapture(
            CameraTemporalFrame frame,
            CameraTemporalReprojection reprojection,
            NativeTemporalConstants constants) {
    }

    private static final class FrameFailure extends RuntimeException {
        private final String stage;

        private FrameFailure(String stage, String message) {
            super(message);
            this.stage = stage;
        }
    }
}
