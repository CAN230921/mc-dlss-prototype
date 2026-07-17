package dev.mcdlss.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcdlss.core.NativePersistentInteropSessionInfo;
import dev.mcdlss.core.NativeReadbackFingerprint;
import java.nio.ByteBuffer;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

public final class MinecraftPersistentColorCaptureProbe {
    private static final int TARGET_FRAMES = 120;
    private static final int TARGET_RETAINED_FRAMES = 30;

    private final McDlssFabricEntrypoint entrypoint;
    private final Slot[] slots = new Slot[2];

    private PersistentColorCaptureState state = PersistentColorCaptureState.WAITING;
    private int width;
    private int height;
    private int successfulFrames;
    private int retainedFrames;
    private int slotAUses;
    private int slotBUses;
    private int resizeCount;
    private int hashChanges;
    private long previousHash;
    private boolean hasPreviousHash;
    private String openGlHash = "";
    private String d3d12Hash = "";
    private long totalNanos;
    private long maximumNanos;
    private boolean resourcesReleased;
    private String message = "Waiting for persistent full-resolution capture";

    public MinecraftPersistentColorCaptureProbe(McDlssFabricEntrypoint entrypoint) {
        if (entrypoint == null) {
            throw new IllegalArgumentException("Fabric entrypoint is required");
        }
        this.entrypoint = entrypoint;
    }

    public MinecraftPersistentColorCaptureSnapshot onWorldFrame() {
        if (!RenderSystem.isOnRenderThread()) {
            fail("Persistent capture must run on Minecraft's render thread");
            return snapshot();
        }
        if (state == PersistentColorCaptureState.COMPLETE
                || state == PersistentColorCaptureState.FAILED) {
            return snapshot();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            fail("World unloaded during persistent capture");
            return snapshot();
        }
        Framebuffer source = client.getFramebuffer();
        if (source.fbo <= 0 || source.textureWidth <= 0 || source.textureHeight <= 0) {
            fail("Minecraft main framebuffer is unavailable");
            return snapshot();
        }

        try {
            if (state == PersistentColorCaptureState.WAITING) {
                createPair(source.textureWidth, source.textureHeight);
                state = PersistentColorCaptureState.CAPTURING;
                message = "Capturing full-resolution frames";
            } else if (state == PersistentColorCaptureState.CAPTURING
                    && (source.textureWidth != width || source.textureHeight != height)) {
                releasePair();
                createPair(source.textureWidth, source.textureHeight);
                resizeCount++;
                message = "Rebuilt persistent slots after resize";
            }

            if (state == PersistentColorCaptureState.CAPTURING) {
                captureFrame(source);
                if (successfulFrames == TARGET_FRAMES) {
                    if (hashChanges == 0) {
                        fail("No world-frame hash changes observed; move the camera during capture");
                    } else {
                        state = PersistentColorCaptureState.RETAINING;
                        message = "Capture complete; retaining both slots for 30 frames";
                    }
                }
            } else if (state == PersistentColorCaptureState.RETAINING) {
                retainedFrames++;
                if (retainedFrames == TARGET_RETAINED_FRAMES) {
                    releasePair();
                    resourcesReleased = true;
                    state = PersistentColorCaptureState.COMPLETE;
                    message = "Persistent full-resolution double-buffer capture completed";
                }
            }
        } catch (RuntimeException error) {
            fail(error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage());
        }
        return snapshot();
    }

    public MinecraftPersistentColorCaptureSnapshot onClientTick() {
        if (state != PersistentColorCaptureState.WAITING
                && state != PersistentColorCaptureState.COMPLETE
                && state != PersistentColorCaptureState.FAILED
                && MinecraftClient.getInstance().world == null) {
            fail("World unloaded during persistent capture");
        }
        return snapshot();
    }

    public MinecraftPersistentColorCaptureSnapshot snapshot() {
        double averageMilliseconds = successfulFrames == 0
                ? 0.0
                : totalNanos / 1_000_000.0 / successfulFrames;
        return new MinecraftPersistentColorCaptureSnapshot(
                state,
                width,
                height,
                TARGET_FRAMES,
                successfulFrames,
                TARGET_RETAINED_FRAMES,
                retainedFrames,
                slotAUses,
                slotBUses,
                resizeCount,
                hashChanges,
                openGlHash,
                d3d12Hash,
                averageMilliseconds,
                maximumNanos / 1_000_000.0,
                resourcesReleased,
                message);
    }

    private void createPair(int newWidth, int newHeight) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Slot first = null;
        Slot second = null;
        try {
            first = createSlot(newWidth, newHeight);
            second = createSlot(newWidth, newHeight);
            slots[0] = first;
            slots[1] = second;
            width = newWidth;
            height = newHeight;
            resourcesReleased = false;
        } catch (RuntimeException error) {
            releaseSlot(first);
            releaseSlot(second);
            throw error;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);
        }
    }

    private Slot createSlot(int slotWidth, int slotHeight) {
        NativePersistentInteropSessionInfo session =
                entrypoint.openD3D12PersistentInteropSession(slotWidth, slotHeight);
        if (session == null || !session.available()) {
            throw new IllegalStateException(session == null
                    ? "Native persistent session is null"
                    : session.message());
        }

        Slot slot = new Slot(session.sessionId(), slotWidth, slotHeight);
        try {
            slot.memoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();
            requireNoGlError("glCreateMemoryObjectsEXT(persistent)");
            EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                    slot.memoryObject,
                    0L,
                    EXTMemoryObjectWin32.GL_HANDLE_TYPE_D3D12_RESOURCE_EXT,
                    session.textureHandle());
            requireNoGlError("glImportMemoryWin32HandleEXT(persistent)");

            slot.texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, slot.texture);
            EXTMemoryObject.glTexStorageMem2DEXT(
                    GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8,
                    slotWidth, slotHeight, slot.memoryObject, 0L);
            requireNoGlError("glTexStorageMem2DEXT(persistent)");

            slot.semaphore = EXTSemaphore.glGenSemaphoresEXT();
            EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                    slot.semaphore,
                    EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT,
                    session.fenceHandle());
            requireNoGlError("glImportSemaphoreWin32HandleEXT(persistent)");

            slot.framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, slot.framebuffer);
            GL30.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    slot.texture,
                    0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                    != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Persistent slot framebuffer is incomplete");
            }
            requireNoGlError("attach persistent framebuffer");

            int byteCount = Math.multiplyExact(Math.multiplyExact(slotWidth, slotHeight), 4);
            slot.pixelBuffer = MemoryUtil.memAlloc(byteCount);
            slot.pixelBytes = new byte[byteCount];
            return slot;
        } catch (RuntimeException error) {
            releaseSlot(slot);
            throw error;
        }
    }

    private void captureFrame(Framebuffer source) {
        int slotIndex = successfulFrames & 1;
        Slot slot = slots[slotIndex];
        if (slot == null) {
            throw new IllegalStateException("Persistent slot is unavailable");
        }
        long started = System.nanoTime();
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        drainGlErrors();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, slot.framebuffer);
            GL30.glBlitFramebuffer(
                    0, 0, width, height,
                    0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST);
            requireNoGlError("glBlitFramebuffer(persistent)");

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, slot.framebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            slot.pixelBuffer.clear();
            GL11.glReadPixels(
                    0, 0, width, height,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, slot.pixelBuffer);
            requireNoGlError("glReadPixels(persistent)");
            slot.pixelBuffer.get(0, slot.pixelBytes);
            RgbaFrameFingerprint glFingerprint =
                    RgbaFrameFingerprint.fromRgba8(slot.pixelBytes);
            if (!glFingerprint.nonUniform() || glFingerprint.nonBlackPixelCount() == 0) {
                throw new IllegalStateException("Persistent world frame is uniform or black");
            }

            PersistentFenceValues fenceValues = PersistentFenceValues.forUse(slot.useCount);
            MinecraftInteropSemaphoreBarriers barriers =
                    MinecraftInteropSemaphoreBarriers.forTexture(
                            slot.texture, EXTSemaphore.GL_LAYOUT_GENERAL_EXT);
            EXTSemaphore.glSemaphoreParameterui64EXT(
                    slot.semaphore,
                    EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    fenceValues.waitValue());
            EXTSemaphore.glSignalSemaphoreEXT(
                    slot.semaphore,
                    barriers.buffers(), barriers.textures(), barriers.layouts());
            GL11.glFlush();
            requireNoGlError("signal persistent fence");

            if (!entrypoint.submitD3D12PersistentReadback(
                    slot.sessionId,
                    fenceValues.waitValue(),
                    fenceValues.signalValue())) {
                throw new IllegalStateException("Persistent D3D12 submission failed");
            }

            EXTSemaphore.glSemaphoreParameterui64EXT(
                    slot.semaphore,
                    EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    fenceValues.signalValue());
            EXTSemaphore.glWaitSemaphoreEXT(
                    slot.semaphore,
                    barriers.buffers(), barriers.textures(), barriers.layouts());
            GL11.glFinish();
            requireNoGlError("wait persistent fence");

            NativeReadbackFingerprint d3dFingerprint =
                    entrypoint.inspectD3D12PersistentReadback(
                            slot.sessionId, fenceValues.signalValue());
            openGlHash = glFingerprint.hashHex();
            d3d12Hash = d3dFingerprint == null ? "" : d3dFingerprint.hashHex();
            if (d3dFingerprint == null || !d3dFingerprint.available()
                    || !d3dFingerprint.nonUniform()
                    || d3dFingerprint.nonBlackPixelCount() == 0
                    || d3dFingerprint.hash() != glFingerprint.hash()) {
                int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
                int packRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
                throw new IllegalStateException(
                        "Persistent fingerprints differ: gl=" + glFingerprint.hashHex()
                                + " d3d=" + d3d12Hash
                                + " glNonUniform=" + glFingerprint.nonUniform()
                                + " d3dNonUniform=" + (d3dFingerprint != null
                                        && d3dFingerprint.nonUniform())
                                + " glNonBlack=" + glFingerprint.nonBlackPixelCount()
                                + " d3dNonBlack=" + (d3dFingerprint == null
                                        ? -1 : d3dFingerprint.nonBlackPixelCount())
                                + " d3dStatus=" + (d3dFingerprint == null
                                        ? "null" : d3dFingerprint.message())
                                + " packAlignment=" + packAlignment
                                + " packRowLength=" + packRowLength);
            }

            if (hasPreviousHash && previousHash != glFingerprint.hash()) {
                hashChanges++;
            }
            previousHash = glFingerprint.hash();
            hasPreviousHash = true;
            slot.useCount++;
            if (slotIndex == 0) {
                slotAUses++;
            } else {
                slotBUses++;
            }
            successfulFrames++;
            long elapsed = System.nanoTime() - started;
            totalNanos += elapsed;
            maximumNanos = Math.max(maximumNanos, elapsed);
            message = "Verified persistent frame " + successfulFrames + "/" + TARGET_FRAMES;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);
        }
    }

    private void fail(String failureMessage) {
        if (state == PersistentColorCaptureState.COMPLETE
                || state == PersistentColorCaptureState.FAILED) {
            return;
        }
        try {
            releasePair();
            resourcesReleased = true;
        } catch (RuntimeException cleanupError) {
            failureMessage = failureMessage + "; cleanup: " + cleanupError.getMessage();
        }
        state = PersistentColorCaptureState.FAILED;
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
        RuntimeException failure = null;
        try {
            if (slot.framebuffer != 0) {
                GL30.glDeleteFramebuffers(slot.framebuffer);
            }
            if (slot.semaphore != 0) {
                EXTSemaphore.glDeleteSemaphoresEXT(slot.semaphore);
            }
            if (slot.texture != 0) {
                GL11.glDeleteTextures(slot.texture);
            }
            if (slot.memoryObject != 0) {
                EXTMemoryObject.glDeleteMemoryObjectsEXT(slot.memoryObject);
            }
            requireNoGlError("release persistent OpenGL slot");
        } catch (RuntimeException error) {
            failure = error;
        }
        if (slot.pixelBuffer != null) {
            MemoryUtil.memFree(slot.pixelBuffer);
            slot.pixelBuffer = null;
        }
        entrypoint.closeD3D12PersistentInteropSession(slot.sessionId);
        if (failure != null) {
            throw failure;
        }
    }

    private static void drainGlErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
    }

    private static void requireNoGlError(String operation) {
        int error = GL11.glGetError();
        if (error == GL11.GL_NO_ERROR) {
            return;
        }
        drainGlErrors();
        throw new IllegalStateException(String.format(
                Locale.ROOT, "%s failed with OpenGL error 0x%04x", operation, error));
    }

    private static final class Slot {
        private final long sessionId;
        private final int width;
        private final int height;
        private int memoryObject;
        private int texture;
        private int semaphore;
        private int framebuffer;
        private ByteBuffer pixelBuffer;
        private byte[] pixelBytes;
        private int useCount;

        private Slot(long sessionId, int width, int height) {
            this.sessionId = sessionId;
            this.width = width;
            this.height = height;
        }
    }
}
