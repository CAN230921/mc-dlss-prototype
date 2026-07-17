package dev.mcdlss.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcdlss.core.NativeMotionFrameReadbackFingerprint;
import dev.mcdlss.core.NativePersistentMotionFrameSessionInfo;
import dev.mcdlss.core.NativeTemporalConstants;
import java.nio.ByteBuffer;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

public final class MinecraftPersistentFrameCaptureProbe {
    private static final int TARGET_FRAMES = 120;
    private static final int TARGET_RETAINED_FRAMES = 30;

    private final McDlssFabricEntrypoint entrypoint;
    private final Slot[] slots = new Slot[2];
    private MinecraftDepthExtractionShader depthShader;
    private MinecraftMotionVectorShader motionShader;
    private CameraTemporalFrame previousTemporalFrame;
    private long temporalFrameIndex;
    private PersistentFrameCaptureState state = PersistentFrameCaptureState.WAITING;
    private int width;
    private int height;
    private int successfulFrames;
    private int retainedFrames;
    private int slotAUses;
    private int slotBUses;
    private int resizeCount;
    private int colorHashChanges;
    private int depthHashChanges;
    private int motionHashChanges;
    private long previousColorHash;
    private long previousDepthHash;
    private long previousMotionHash;
    private boolean hasPreviousHashes;
    private String openGlColorHash = "";
    private String d3d12ColorHash = "";
    private String openGlDepthHash = "";
    private String d3d12DepthHash = "";
    private String openGlMotionHash = "";
    private String d3d12MotionHash = "";
    private int motionFinite;
    private int motionNonZero;
    private int motionOutOfBounds;
    private float minimumMotionX;
    private float maximumMotionX;
    private float minimumMotionY;
    private float maximumMotionY;
    private float maximumMotionMagnitude;
    private boolean stationaryPhaseObserved;
    private boolean cameraMotionPhaseObserved;
    private boolean temporalConstantsValid;
    private int resetFrameCount;
    private boolean automaticMotionApplied;
    private boolean automaticMotionRestored;
    private float automaticMotionOriginalYaw;
    private int depthFinite;
    private int depthInRange;
    private int depthScene;
    private int depthFar;
    private float minimumDepth;
    private float maximumDepth;
    private long totalNanos;
    private long maximumNanos;
    private boolean resourcesReleased;
    private String message = "Waiting for persistent color/depth capture";

    public MinecraftPersistentFrameCaptureProbe(McDlssFabricEntrypoint entrypoint) {
        if (entrypoint == null) {
            throw new IllegalArgumentException("Fabric entrypoint is required");
        }
        this.entrypoint = entrypoint;
    }

    public MinecraftPersistentMotionFrameCaptureSnapshot onWorldFrame(
            WorldRenderContext context) {
        if (!RenderSystem.isOnRenderThread()) {
            fail("Persistent frame capture must run on the render thread");
            return snapshot();
        }
        if (state == PersistentFrameCaptureState.COMPLETE
                || state == PersistentFrameCaptureState.FAILED) {
            return snapshot();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            fail("World unloaded during persistent frame capture");
            return snapshot();
        }
        Framebuffer source = client.getFramebuffer();
        if (source.fbo <= 0 || source.getDepthAttachment() <= 0
                || source.textureWidth <= 0 || source.textureHeight <= 0) {
            fail("Minecraft color/depth framebuffer is unavailable");
            return snapshot();
        }
        try {
            if (state == PersistentFrameCaptureState.WAITING) {
                createPair(source.textureWidth, source.textureHeight);
                state = PersistentFrameCaptureState.CAPTURING;
                message = "Capturing full-resolution color and depth";
            } else if (state == PersistentFrameCaptureState.CAPTURING
                    && (source.textureWidth != width || source.textureHeight != height)) {
                releasePair();
                createPair(source.textureWidth, source.textureHeight);
                resizeCount++;
            }
            if (state == PersistentFrameCaptureState.CAPTURING) {
                captureFrame(source, captureTemporalFrame(context));
                if (successfulFrames == TARGET_FRAMES) {
                    if (colorHashChanges == 0 || depthHashChanges == 0
                            || motionHashChanges == 0 || !cameraMotionPhaseObserved) {
                        fail("Color/depth/motion hashes did not change during capture");
                    } else {
                        state = PersistentFrameCaptureState.RETAINING;
                        message = "Frame capture complete; retaining resources";
                    }
                }
            } else if (state == PersistentFrameCaptureState.RETAINING) {
                retainedFrames++;
                if (retainedFrames == TARGET_RETAINED_FRAMES) {
                    releasePair();
                    closeShader();
                    resourcesReleased = true;
                    state = PersistentFrameCaptureState.COMPLETE;
                    message = "Persistent color/depth/motion double-buffer capture completed";
                }
            }
        } catch (RuntimeException error) {
            fail(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
        return snapshot();
    }

    public MinecraftPersistentMotionFrameCaptureSnapshot onClientTick() {
        if (state != PersistentFrameCaptureState.WAITING
                && state != PersistentFrameCaptureState.COMPLETE
                && state != PersistentFrameCaptureState.FAILED
                && MinecraftClient.getInstance().world == null) {
            fail("World unloaded during persistent frame capture");
        }
        return snapshot();
    }

    public MinecraftPersistentMotionFrameCaptureSnapshot snapshot() {
        double average = successfulFrames == 0
                ? 0.0 : totalNanos / 1_000_000.0 / successfulFrames;
        MinecraftPersistentFrameCaptureSnapshot frame = new MinecraftPersistentFrameCaptureSnapshot(
                state, width, height, TARGET_FRAMES, successfulFrames,
                TARGET_RETAINED_FRAMES, retainedFrames, slotAUses, slotBUses,
                resizeCount, colorHashChanges, depthHashChanges,
                openGlColorHash, d3d12ColorHash, openGlDepthHash, d3d12DepthHash,
                depthFinite, depthInRange, depthScene, depthFar,
                minimumDepth, maximumDepth, average, maximumNanos / 1_000_000.0,
                resourcesReleased, message);
        return new MinecraftPersistentMotionFrameCaptureSnapshot(
                frame, motionHashChanges, openGlMotionHash, d3d12MotionHash,
                motionFinite, motionNonZero, motionOutOfBounds,
                minimumMotionX, maximumMotionX, minimumMotionY, maximumMotionY,
                maximumMotionMagnitude, stationaryPhaseObserved,
                cameraMotionPhaseObserved, temporalConstantsValid,
                resetFrameCount, message);
    }

    private void createPair(int newWidth, int newHeight) {
        GlState saved = GlState.capture();
        Slot first = null;
        Slot second = null;
        try {
            if (depthShader == null) {
                depthShader = new MinecraftDepthExtractionShader();
            }
            if (motionShader == null) {
                motionShader = new MinecraftMotionVectorShader();
            }
            first = createSlot(newWidth, newHeight);
            second = createSlot(newWidth, newHeight);
            slots[0] = first;
            slots[1] = second;
            width = newWidth;
            height = newHeight;
            resourcesReleased = false;
            previousTemporalFrame = null;
        } catch (RuntimeException error) {
            releaseSlot(first);
            releaseSlot(second);
            throw error;
        } finally {
            saved.restore();
        }
    }

    private Slot createSlot(int slotWidth, int slotHeight) {
        NativePersistentMotionFrameSessionInfo session =
                entrypoint.openD3D12PersistentMotionFrameSession(slotWidth, slotHeight);
        if (session == null || !session.available()) {
            throw new IllegalStateException(session == null
                    ? "Native frame session is null" : session.message());
        }
        Slot slot = new Slot(session.sessionId());
        try {
            slot.colorMemory = importMemory(session.colorTextureHandle());
            slot.colorTexture = createTexture(
                    slot.colorMemory, GL11.GL_RGBA8, slotWidth, slotHeight);
            slot.depthMemory = importMemory(session.depthTextureHandle());
            slot.depthTexture = createTexture(
                    slot.depthMemory, GL30.GL_R32F, slotWidth, slotHeight);
            slot.motionMemory = importMemory(session.motionTextureHandle());
            slot.motionTexture = createTexture(
                    slot.motionMemory, GL30.GL_RG16F, slotWidth, slotHeight);
            slot.semaphore = EXTSemaphore.glGenSemaphoresEXT();
            EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                    slot.semaphore,
                    EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT,
                    session.fenceHandle());
            requireNoGlError("import frame semaphore");
            slot.colorFramebuffer = createFramebuffer(slot.colorTexture);
            slot.depthFramebuffer = createFramebuffer(slot.depthTexture);
            slot.motionFramebuffer = createFramebuffer(slot.motionTexture);
            int byteCount = Math.multiplyExact(Math.multiplyExact(slotWidth, slotHeight), 4);
            slot.colorBuffer = MemoryUtil.memAlloc(byteCount);
            slot.depthBuffer = MemoryUtil.memAlloc(byteCount);
            slot.motionBuffer = MemoryUtil.memAlloc(byteCount);
            slot.colorBytes = new byte[byteCount];
            slot.depthBytes = new byte[byteCount];
            slot.motionBytes = new byte[byteCount];
            return slot;
        } catch (RuntimeException error) {
            releaseSlot(slot);
            throw error;
        }
    }

    private TemporalCapture captureTemporalFrame(WorldRenderContext context) {
        if (context == null) {
            throw new IllegalArgumentException("World render context is required");
        }
        Matrix4f projection = new Matrix4f(context.projectionMatrix());
        Matrix4f view = new Matrix4f(context.positionMatrix());
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        float[] projectionValues = projection.get(new float[16]);
        float[] viewValues = view.get(new float[16]);
        CameraTemporalFrame frame = new CameraTemporalFrame(
                viewProjection.get(new float[16]), projectionValues, viewValues,
                width, height, ++temporalFrameIndex);
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
        return new TemporalCapture(frame, reprojection, constants);
    }

    private static float[] normalized(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (!Float.isFinite(length) || length <= 0.000001f) {
            throw new IllegalArgumentException("Camera basis is invalid");
        }
        return new float[] {x / length, y / length, z / length};
    }

    private void captureFrame(Framebuffer source, TemporalCapture temporal) {
        Slot slot = slots[successfulFrames & 1];
        if (slot == null) {
            throw new IllegalStateException("Persistent frame slot is unavailable");
        }
        long started = System.nanoTime();
        GlState saved = GlState.capture();
        drainGlErrors();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, slot.colorFramebuffer);
            GL30.glBlitFramebuffer(
                    0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            requireNoGlError("blit persistent frame color");

            depthShader.render(
                    source.getDepthAttachment(), slot.depthFramebuffer, width, height);
            requireNoGlError("extract persistent frame depth");

            motionShader.render(
                    source.getDepthAttachment(), slot.motionFramebuffer, width, height,
                    temporal.reprojection().clipToPrevClip(), temporal.reprojection().reset());
            requireNoGlError("generate persistent frame motion");

            RgbaFrameFingerprint glColor = readColor(slot);
            DepthFrameFingerprint glDepth = readDepth(slot);
            MotionFrameFingerprint glMotion = readMotion(slot);
            if (!glColor.nonUniform() || glColor.nonBlackPixelCount() == 0) {
                throw new IllegalStateException("Persistent frame color is uniform or black");
            }
            if (!glDepth.validForFrame()) {
                throw new IllegalStateException(
                        "Persistent frame depth invalid: finite=" + glDepth.finiteSampleCount()
                                + " inRange=" + glDepth.inRangeSampleCount()
                                + " scene=" + glDepth.sceneSampleCount()
                                + " far=" + glDepth.farSampleCount()
                                + " min=" + glDepth.minimum()
                                + " max=" + glDepth.maximum());
            }
            if (!glMotion.validForFrame()) {
                throw new IllegalStateException("Persistent frame motion is invalid");
            }

            PersistentFenceValues fence = PersistentFenceValues.forUse(slot.useCount);
            int[] textures = {slot.colorTexture, slot.depthTexture, slot.motionTexture};
            int[] layouts = {
                EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
                EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
                EXTSemaphore.GL_LAYOUT_GENERAL_EXT};
            EXTSemaphore.glSemaphoreParameterui64EXT(
                    slot.semaphore, EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    fence.waitValue());
            EXTSemaphore.glSignalSemaphoreEXT(
                    slot.semaphore, new int[0], textures, layouts);
            GL11.glFlush();
            requireNoGlError("signal persistent frame fence");
            if (!entrypoint.submitD3D12PersistentMotionFrameReadback(
                    slot.sessionId, fence.waitValue(), fence.signalValue())) {
                throw new IllegalStateException("Persistent frame D3D12 submission failed");
            }
            EXTSemaphore.glSemaphoreParameterui64EXT(
                    slot.semaphore, EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    fence.signalValue());
            EXTSemaphore.glWaitSemaphoreEXT(
                    slot.semaphore, new int[0], textures, layouts);
            GL11.glFinish();
            requireNoGlError("wait persistent frame fence");

            int pixelCount = Math.multiplyExact(width, height);
            NativeMotionFrameReadbackFingerprint nativeFrame =
                    entrypoint.inspectD3D12PersistentMotionFrameReadback(
                            slot.sessionId, fence.signalValue(), pixelCount);
            validateNative(glColor, glDepth, glMotion, nativeFrame, pixelCount);
            publish(glColor, glDepth, glMotion, nativeFrame);

            if (hasPreviousHashes) {
                colorHashChanges += previousColorHash == glColor.hash() ? 0 : 1;
                depthHashChanges += previousDepthHash == glDepth.hash() ? 0 : 1;
                motionHashChanges += previousMotionHash == glMotion.hash() ? 0 : 1;
            }
            previousColorHash = glColor.hash();
            previousDepthHash = glDepth.hash();
            previousMotionHash = glMotion.hash();
            hasPreviousHashes = true;
            previousTemporalFrame = temporal.frame();
            temporalConstantsValid = true;
            if (temporal.reprojection().reset()) resetFrameCount++;
            stationaryPhaseObserved |= glMotion.nonZeroVectorCount() == 0;
            cameraMotionPhaseObserved |= glMotion.nonZeroVectorCount() > 0;
            slot.useCount++;
            if ((successfulFrames & 1) == 0) {
                slotAUses++;
            } else {
                slotBUses++;
            }
            successfulFrames++;
            applyAutomaticMotionTest();
            long elapsed = System.nanoTime() - started;
            totalNanos += elapsed;
            maximumNanos = Math.max(maximumNanos, elapsed);
            message = "Verified persistent color/depth frame "
                    + successfulFrames + "/" + TARGET_FRAMES;
        } finally {
            saved.restore();
        }
    }

    private void applyAutomaticMotionTest() {
        if (!Boolean.getBoolean("mcDlss.autoMotionTest")) {
            return;
        }
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        if (!automaticMotionApplied && successfulFrames >= 30) {
            automaticMotionOriginalYaw = player.getYaw();
            player.setYaw(automaticMotionOriginalYaw + 5.0f);
            automaticMotionApplied = true;
        } else if (automaticMotionApplied && !automaticMotionRestored
                && successfulFrames >= 45) {
            player.setYaw(automaticMotionOriginalYaw);
            automaticMotionRestored = true;
        }
    }

    private RgbaFrameFingerprint readColor(Slot slot) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, slot.colorFramebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        slot.colorBuffer.clear();
        GL11.glReadPixels(0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, slot.colorBuffer);
        requireNoGlError("read persistent frame color");
        slot.colorBuffer.get(0, slot.colorBytes);
        return RgbaFrameFingerprint.fromRgba8(slot.colorBytes);
    }

    private DepthFrameFingerprint readDepth(Slot slot) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, slot.depthFramebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        slot.depthBuffer.clear();
        GL11.glReadPixels(0, 0, width, height,
                GL11.GL_RED, GL11.GL_FLOAT, slot.depthBuffer);
        requireNoGlError("read persistent frame depth");
        slot.depthBuffer.get(0, slot.depthBytes);
        return DepthFrameFingerprint.fromR32fBytes(slot.depthBytes);
    }

    private MotionFrameFingerprint readMotion(Slot slot) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, slot.motionFramebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        slot.motionBuffer.clear();
        GL11.glReadPixels(0, 0, width, height,
                GL30.GL_RG, GL30.GL_HALF_FLOAT, slot.motionBuffer);
        requireNoGlError("read persistent frame motion");
        slot.motionBuffer.get(0, slot.motionBytes);
        return MotionFrameFingerprint.fromRg16f(slot.motionBytes, width, height);
    }

    private static void validateNative(
            RgbaFrameFingerprint glColor,
            DepthFrameFingerprint glDepth,
            MotionFrameFingerprint glMotion,
            NativeMotionFrameReadbackFingerprint nativeFrame,
            int pixelCount) {
        if (nativeFrame == null || !nativeFrame.available()) {
            throw new IllegalStateException(nativeFrame == null
                    ? "Native persistent frame fingerprint is null"
                    : nativeFrame.message());
        }
        var nativeColorDepth = nativeFrame.frame();
        boolean matches = nativeColorDepth.colorHash() == glColor.hash()
                && nativeColorDepth.depthHash() == glDepth.hash()
                && nativeColorDepth.colorNonUniform()
                && nativeColorDepth.colorNonBlackPixelCount() > 0
                && nativeColorDepth.depthNonUniform()
                && nativeColorDepth.depthFiniteSampleCount() == pixelCount
                && nativeColorDepth.depthInRangeSampleCount() == pixelCount
                && nativeColorDepth.depthSceneSampleCount() == glDepth.sceneSampleCount()
                && nativeColorDepth.depthFarSampleCount() == glDepth.farSampleCount()
                && Float.floatToRawIntBits(nativeColorDepth.minimumDepth())
                        == Float.floatToRawIntBits(glDepth.minimum())
                && Float.floatToRawIntBits(nativeColorDepth.maximumDepth())
                        == Float.floatToRawIntBits(glDepth.maximum())
                && nativeFrame.motionHash() == glMotion.hash()
                && nativeFrame.finiteVectorCount() == glMotion.finiteVectorCount()
                && nativeFrame.nonZeroVectorCount() == glMotion.nonZeroVectorCount()
                && nativeFrame.outOfBoundsVectorCount() == glMotion.outOfBoundsVectorCount();
        if (!matches) {
            throw new IllegalStateException(
                    "Persistent frame fingerprints differ: color=" + glColor.hashHex()
                            + "/" + nativeColorDepth.colorHashHex()
                            + " depth=" + glDepth.hashHex()
                            + "/" + nativeColorDepth.depthHashHex());
        }
    }

    private void publish(
            RgbaFrameFingerprint glColor,
            DepthFrameFingerprint glDepth,
            MotionFrameFingerprint glMotion,
            NativeMotionFrameReadbackFingerprint nativeFrame) {
        var nativeColorDepth = nativeFrame.frame();
        openGlColorHash = glColor.hashHex();
        d3d12ColorHash = nativeColorDepth.colorHashHex();
        openGlDepthHash = glDepth.hashHex();
        d3d12DepthHash = nativeColorDepth.depthHashHex();
        openGlMotionHash = glMotion.hashHex();
        d3d12MotionHash = String.format(Locale.ROOT, "%016x", nativeFrame.motionHash());
        depthFinite = glDepth.finiteSampleCount();
        depthInRange = glDepth.inRangeSampleCount();
        depthScene = glDepth.sceneSampleCount();
        depthFar = glDepth.farSampleCount();
        minimumDepth = glDepth.minimum();
        maximumDepth = glDepth.maximum();
        motionFinite = glMotion.finiteVectorCount();
        motionNonZero = glMotion.nonZeroVectorCount();
        motionOutOfBounds = glMotion.outOfBoundsVectorCount();
        minimumMotionX = glMotion.minimumX();
        maximumMotionX = glMotion.maximumX();
        minimumMotionY = glMotion.minimumY();
        maximumMotionY = glMotion.maximumY();
        maximumMotionMagnitude = glMotion.maximumMagnitude();
    }

    private void fail(String failureMessage) {
        if (state == PersistentFrameCaptureState.COMPLETE
                || state == PersistentFrameCaptureState.FAILED) {
            return;
        }
        try {
            releasePair();
            closeShader();
            resourcesReleased = true;
        } catch (RuntimeException cleanupError) {
            failureMessage += "; cleanup: " + cleanupError.getMessage();
        }
        state = PersistentFrameCaptureState.FAILED;
        message = failureMessage;
    }

    private void releasePair() {
        RuntimeException failure = null;
        for (int index = 0; index < slots.length; index++) {
            try {
                releaseSlot(slots[index]);
            } catch (RuntimeException error) {
                failure = error;
            } finally {
                slots[index] = null;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void releaseSlot(Slot slot) {
        if (slot == null) {
            return;
        }
        if (slot.colorFramebuffer != 0) GL30.glDeleteFramebuffers(slot.colorFramebuffer);
        if (slot.depthFramebuffer != 0) GL30.glDeleteFramebuffers(slot.depthFramebuffer);
        if (slot.motionFramebuffer != 0) GL30.glDeleteFramebuffers(slot.motionFramebuffer);
        if (slot.semaphore != 0) EXTSemaphore.glDeleteSemaphoresEXT(slot.semaphore);
        if (slot.colorTexture != 0) GL11.glDeleteTextures(slot.colorTexture);
        if (slot.depthTexture != 0) GL11.glDeleteTextures(slot.depthTexture);
        if (slot.motionTexture != 0) GL11.glDeleteTextures(slot.motionTexture);
        if (slot.colorMemory != 0) EXTMemoryObject.glDeleteMemoryObjectsEXT(slot.colorMemory);
        if (slot.depthMemory != 0) EXTMemoryObject.glDeleteMemoryObjectsEXT(slot.depthMemory);
        if (slot.motionMemory != 0) EXTMemoryObject.glDeleteMemoryObjectsEXT(slot.motionMemory);
        if (slot.colorBuffer != null) MemoryUtil.memFree(slot.colorBuffer);
        if (slot.depthBuffer != null) MemoryUtil.memFree(slot.depthBuffer);
        if (slot.motionBuffer != null) MemoryUtil.memFree(slot.motionBuffer);
        entrypoint.closeD3D12PersistentMotionFrameSession(slot.sessionId);
        requireNoGlError("release persistent frame slot");
    }

    private void closeShader() {
        if (depthShader != null) {
            depthShader.close();
            depthShader = null;
        }
        if (motionShader != null) {
            motionShader.close();
            motionShader = null;
        }
    }

    private static int importMemory(long handle) {
        int memory = EXTMemoryObject.glCreateMemoryObjectsEXT();
        EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                memory, 0L, EXTMemoryObjectWin32.GL_HANDLE_TYPE_D3D12_RESOURCE_EXT,
                handle);
        requireNoGlError("import persistent frame memory");
        return memory;
    }

    private static int createTexture(int memory, int format, int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        EXTMemoryObject.glTexStorageMem2DEXT(
                GL11.GL_TEXTURE_2D, 1, format, width, height, memory, 0L);
        requireNoGlError("allocate persistent frame texture");
        return texture;
    }

    private static int createFramebuffer(int texture) {
        int framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(
                GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Persistent frame framebuffer is incomplete");
        }
        requireNoGlError("attach persistent frame texture");
        return framebuffer;
    }

    private static void drainGlErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
    }

    private static void requireNoGlError(String operation) {
        int error = GL11.glGetError();
        if (error == GL11.GL_NO_ERROR) return;
        drainGlErrors();
        throw new IllegalStateException(String.format(
                Locale.ROOT, "%s failed with OpenGL error 0x%04x", operation, error));
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
    }

    private record GlState(
            int readFramebuffer,
            int drawFramebuffer,
            int program,
            int vertexArray,
            int activeTexture,
            int texture0,
            int[] viewport,
            boolean depthTest,
            boolean blend,
            boolean scissor,
            boolean cull,
            boolean[] colorMask) {
        static GlState capture() {
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(active);
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            ByteBuffer colorMaskBytes = MemoryUtil.memAlloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMaskBytes);
            boolean[] colorMask = {
                colorMaskBytes.get(0) != 0,
                colorMaskBytes.get(1) != 0,
                colorMaskBytes.get(2) != 0,
                colorMaskBytes.get(3) != 0
            };
            MemoryUtil.memFree(colorMaskBytes);
            return new GlState(
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    active, texture0, viewport,
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE), colorMask);
        }

        void restore() {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            setEnabled(GL11.GL_DEPTH_TEST, depthTest);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_SCISSOR_TEST, scissor);
            setEnabled(GL11.GL_CULL_FACE, cull);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        }
    }

    private record TemporalCapture(
            CameraTemporalFrame frame,
            CameraTemporalReprojection reprojection,
            NativeTemporalConstants constants) {
    }

    private static final class Slot {
        private final long sessionId;
        private int colorMemory;
        private int depthMemory;
        private int motionMemory;
        private int colorTexture;
        private int depthTexture;
        private int motionTexture;
        private int semaphore;
        private int colorFramebuffer;
        private int depthFramebuffer;
        private int motionFramebuffer;
        private ByteBuffer colorBuffer;
        private ByteBuffer depthBuffer;
        private ByteBuffer motionBuffer;
        private byte[] colorBytes;
        private byte[] depthBytes;
        private byte[] motionBytes;
        private int useCount;

        private Slot(long sessionId) {
            this.sessionId = sessionId;
        }
    }
}
