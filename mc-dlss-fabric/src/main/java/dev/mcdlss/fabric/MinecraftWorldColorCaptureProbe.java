package dev.mcdlss.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcdlss.core.NativeInteropSessionInfo;
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

public final class MinecraftWorldColorCaptureProbe {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 64;

    public static MinecraftWorldColorCaptureSnapshot probe(McDlssFabricEntrypoint entrypoint) {
        if (!RenderSystem.isOnRenderThread()) {
            return MinecraftWorldColorCaptureSnapshot.failure(
                    "World-color capture must run on Minecraft's render thread");
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (entrypoint == null || client.world == null) {
            return MinecraftWorldColorCaptureSnapshot.failure(
                    "World-color capture requires an entrypoint and loaded world");
        }

        boolean framebufferComplete = false;
        boolean blitCompleted = false;
        boolean openGlContentValid = false;
        boolean d3d12ReadbackSubmitted = false;
        boolean openGlWaitCompleted = false;
        boolean fingerprintMatched = false;
        boolean resourcesReleased = false;
        String openGlHash = "";
        String d3d12Hash = "";
        String message = "World-color capture did not complete";

        Framebuffer source = client.getFramebuffer();
        int sourceWidth = source.textureWidth;
        int sourceHeight = source.textureHeight;
        long sessionId = 0L;
        boolean sessionCreated = false;
        int memoryObject = 0;
        int texture = 0;
        int semaphore = 0;
        int captureFramebuffer = 0;
        int previousReadFramebuffer = 0;
        int previousDrawFramebuffer = 0;
        int previousTextureBinding = 0;
        ByteBuffer pixels = null;

        try {
            if (sourceWidth <= 0 || sourceHeight <= 0 || source.fbo <= 0) {
                throw new IllegalStateException("Minecraft main framebuffer is unavailable");
            }
            drainGlErrors();
            previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            requireNoGlError("save Minecraft framebuffer bindings");

            NativeInteropSessionInfo session =
                    entrypoint.openD3D12InteropSession(WIDTH, HEIGHT);
            if (session == null || !session.available()) {
                throw new IllegalStateException(session == null
                        ? "Native capture session is null"
                        : session.message());
            }
            sessionId = session.sessionId();
            sessionCreated = true;

            memoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();
            requireNoGlError("glCreateMemoryObjectsEXT(world color)");
            EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                    memoryObject,
                    0L,
                    EXTMemoryObjectWin32.GL_HANDLE_TYPE_D3D12_RESOURCE_EXT,
                    session.textureHandle());
            requireNoGlError("glImportMemoryWin32HandleEXT(world color)");

            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            EXTMemoryObject.glTexStorageMem2DEXT(
                    GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8,
                    WIDTH, HEIGHT, memoryObject, 0L);
            requireNoGlError("glTexStorageMem2DEXT(world color)");

            semaphore = EXTSemaphore.glGenSemaphoresEXT();
            EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT,
                    session.fenceHandle());
            requireNoGlError("glImportSemaphoreWin32HandleEXT(world color)");

            captureFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureFramebuffer);
            GL30.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    texture,
                    0);
            framebufferComplete = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                    == GL30.GL_FRAMEBUFFER_COMPLETE;
            requireNoGlError("attach shared world-color framebuffer");
            if (!framebufferComplete) {
                throw new IllegalStateException("Shared world-color framebuffer is incomplete");
            }

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureFramebuffer);
            GL30.glBlitFramebuffer(
                    0, 0, sourceWidth, sourceHeight,
                    0, 0, WIDTH, HEIGHT,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_LINEAR);
            requireNoGlError("glBlitFramebuffer(world color)");
            blitCompleted = true;

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, captureFramebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
            GL11.glReadPixels(
                    0, 0, WIDTH, HEIGHT,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            requireNoGlError("glReadPixels(world color)");
            byte[] pixelBytes = new byte[pixels.remaining()];
            pixels.get(0, pixelBytes);
            RgbaFrameFingerprint openGlFingerprint =
                    RgbaFrameFingerprint.fromRgba8(pixelBytes);
            openGlHash = openGlFingerprint.hashHex();
            openGlContentValid = openGlFingerprint.nonUniform()
                    && openGlFingerprint.nonBlackPixelCount() > 0;

            MinecraftInteropSemaphoreBarriers barriers =
                    MinecraftInteropSemaphoreBarriers.forTexture(
                            texture, EXTSemaphore.GL_LAYOUT_GENERAL_EXT);
            EXTSemaphore.glSemaphoreParameterui64EXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    1L);
            EXTSemaphore.glSignalSemaphoreEXT(
                    semaphore,
                    barriers.buffers(),
                    barriers.textures(),
                    barriers.layouts());
            GL11.glFlush();
            requireNoGlError("signal world-color fence value 1");

            if (!entrypoint.submitD3D12InteropReadback(sessionId)) {
                throw new IllegalStateException("Native world-color readback submission failed");
            }
            d3d12ReadbackSubmitted = true;

            EXTSemaphore.glSemaphoreParameterui64EXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    2L);
            EXTSemaphore.glWaitSemaphoreEXT(
                    semaphore,
                    barriers.buffers(),
                    barriers.textures(),
                    barriers.layouts());
            GL11.glFinish();
            requireNoGlError("wait for world-color fence value 2");
            openGlWaitCompleted = true;

            NativeReadbackFingerprint nativeFingerprint =
                    entrypoint.inspectD3D12InteropReadback(sessionId);
            if (nativeFingerprint == null || !nativeFingerprint.available()) {
                throw new IllegalStateException(nativeFingerprint == null
                        ? "Native world-color fingerprint is null"
                        : nativeFingerprint.message());
            }
            d3d12Hash = nativeFingerprint.hashHex();
            fingerprintMatched = openGlFingerprint.hash() == nativeFingerprint.hash()
                    && nativeFingerprint.nonUniform()
                    && nativeFingerprint.nonBlackPixelCount() > 0;
            if (!openGlContentValid) {
                message = "Captured world color is uniform or black";
            } else if (!fingerprintMatched) {
                message = "OpenGL and D3D12 world-color fingerprints differ";
            } else {
                message = "Pre-HUD Minecraft world color reached D3D12";
            }
        } catch (RuntimeException error) {
            message = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
        } finally {
            boolean cleanupSucceeded = true;
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);
                if (captureFramebuffer != 0) {
                    GL30.glDeleteFramebuffers(captureFramebuffer);
                }
                if (semaphore != 0) {
                    EXTSemaphore.glDeleteSemaphoresEXT(semaphore);
                }
                if (texture != 0) {
                    GL11.glDeleteTextures(texture);
                }
                if (memoryObject != 0) {
                    EXTMemoryObject.glDeleteMemoryObjectsEXT(memoryObject);
                }
                requireNoGlError("world-color OpenGL cleanup");
            } catch (RuntimeException cleanupError) {
                cleanupSucceeded = false;
                message = message + "; cleanup: " + cleanupError.getMessage();
            }
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }
            if (sessionId > 0L) {
                entrypoint.closeD3D12InteropSession(sessionId);
            }
            resourcesReleased = sessionCreated && cleanupSucceeded;
        }

        return new MinecraftWorldColorCaptureSnapshot(
                true,
                framebufferComplete,
                blitCompleted,
                openGlContentValid,
                d3d12ReadbackSubmitted,
                openGlWaitCompleted,
                fingerprintMatched,
                resourcesReleased,
                sourceWidth,
                sourceHeight,
                openGlHash,
                d3d12Hash,
                message);
    }

    public static String toLogLine(MinecraftWorldColorCaptureSnapshot snapshot) {
        MinecraftWorldColorCaptureSnapshot safe = snapshot == null
                ? MinecraftWorldColorCaptureSnapshot.failure("World-color snapshot is null")
                : snapshot;
        return "attempted=" + safe.attempted()
                + " framebufferComplete=" + safe.framebufferComplete()
                + " blitCompleted=" + safe.blitCompleted()
                + " openGlContentValid=" + safe.openGlContentValid()
                + " d3d12ReadbackSubmitted=" + safe.d3d12ReadbackSubmitted()
                + " openGlWaitCompleted=" + safe.openGlWaitCompleted()
                + " fingerprintMatched=" + safe.fingerprintMatched()
                + " resourcesReleased=" + safe.resourcesReleased()
                + " source=" + safe.sourceWidth() + "x" + safe.sourceHeight()
                + " glHash=" + safe.openGlHash()
                + " d3d12Hash=" + safe.d3d12Hash()
                + " success=" + safe.success()
                + " message=" + safe.message();
    }

    private static void drainGlErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
    }

    private static void requireNoGlError(String operation) {
        int firstError = GL11.glGetError();
        if (firstError == GL11.GL_NO_ERROR) {
            return;
        }
        drainGlErrors();
        throw new IllegalStateException(String.format(
                Locale.ROOT, "%s failed with OpenGL error 0x%04x",
                operation, firstError));
    }

    private MinecraftWorldColorCaptureProbe() {
    }
}
