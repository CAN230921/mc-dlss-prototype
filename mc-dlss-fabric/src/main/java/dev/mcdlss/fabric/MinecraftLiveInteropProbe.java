package dev.mcdlss.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcdlss.core.NativeInteropSessionInfo;
import java.nio.ByteBuffer;
import java.util.Locale;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

public final class MinecraftLiveInteropProbe {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 64;

    public static MinecraftLiveInteropSnapshot probe(McDlssFabricEntrypoint entrypoint) {
        if (!RenderSystem.isOnRenderThread()) {
            return MinecraftLiveInteropSnapshot.failure(
                    "Live shared-resource probe must run on Minecraft's render thread");
        }
        if (entrypoint == null) {
            return MinecraftLiveInteropSnapshot.failure("Fabric entrypoint is null");
        }

        boolean sessionCreated = false;
        boolean memoryImported = false;
        boolean semaphoreImported = false;
        boolean openGlWriteSubmitted = false;
        boolean d3d12ReadbackSubmitted = false;
        boolean openGlWaitCompleted = false;
        boolean readbackMatched = false;
        boolean resourcesReleased = false;
        String message = "Live shared-resource probe did not complete";

        long sessionId = 0L;
        int memoryObject = 0;
        int texture = 0;
        int semaphore = 0;
        int previousTextureBinding = 0;
        ByteBuffer pixels = null;

        try {
            drainGlErrors();
            previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            requireNoGlError("glGetIntegerv(GL_TEXTURE_BINDING_2D)");

            NativeInteropSessionInfo session =
                    entrypoint.openD3D12InteropSession(WIDTH, HEIGHT);
            if (session == null || !session.available()) {
                throw new IllegalStateException(session == null
                        ? "Native interop session is null"
                        : session.message());
            }
            sessionId = session.sessionId();
            sessionCreated = true;

            memoryObject = EXTMemoryObject.glCreateMemoryObjectsEXT();
            requireNoGlError("glCreateMemoryObjectsEXT");
            EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                    memoryObject,
                    0L,
                    EXTMemoryObjectWin32.GL_HANDLE_TYPE_D3D12_RESOURCE_EXT,
                    session.textureHandle());
            requireNoGlError("glImportMemoryWin32HandleEXT");
            memoryImported = true;

            texture = GL11.glGenTextures();
            requireNoGlError("glGenTextures");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            EXTMemoryObject.glTexStorageMem2DEXT(
                    GL11.GL_TEXTURE_2D,
                    1,
                    GL11.GL_RGBA8,
                    WIDTH,
                    HEIGHT,
                    memoryObject,
                    0L);
            requireNoGlError("glTexStorageMem2DEXT");

            semaphore = EXTSemaphore.glGenSemaphoresEXT();
            requireNoGlError("glGenSemaphoresEXT");
            EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT,
                    session.fenceHandle());
            requireNoGlError("glImportSemaphoreWin32HandleEXT");
            semaphoreImported = true;

            byte[] pattern = MinecraftInteropTestPattern.createRgba8(WIDTH, HEIGHT);
            pixels = MemoryUtil.memAlloc(pattern.length);
            pixels.put(pattern).flip();
            GL11.glTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    WIDTH,
                    HEIGHT,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    pixels);
            requireNoGlError("glTexSubImage2D");

            MinecraftInteropSemaphoreBarriers barriers =
                    MinecraftInteropSemaphoreBarriers.forTexture(
                            texture, EXTSemaphore.GL_LAYOUT_GENERAL_EXT);
            EXTSemaphore.glSemaphoreParameterui64EXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    1L);
            requireNoGlError("glSemaphoreParameterui64EXT(value 1)");
            EXTSemaphore.glSignalSemaphoreEXT(
                    semaphore,
                    barriers.buffers(),
                    barriers.textures(),
                    barriers.layouts());
            requireNoGlError("glSignalSemaphoreEXT(value 1)");
            GL11.glFlush();
            requireNoGlError("glFlush(value 1)");
            openGlWriteSubmitted = true;

            if (!entrypoint.submitD3D12InteropReadback(sessionId)) {
                throw new IllegalStateException("Native D3D12 readback submission failed");
            }
            d3d12ReadbackSubmitted = true;

            EXTSemaphore.glSemaphoreParameterui64EXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT,
                    2L);
            requireNoGlError("glSemaphoreParameterui64EXT(value 2)");
            EXTSemaphore.glWaitSemaphoreEXT(
                    semaphore,
                    barriers.buffers(),
                    barriers.textures(),
                    barriers.layouts());
            requireNoGlError("glWaitSemaphoreEXT(value 2)");
            GL11.glFinish();
            requireNoGlError("glFinish(value 2)");
            openGlWaitCompleted = true;

            readbackMatched = entrypoint.verifyD3D12InteropReadback(sessionId);
            if (!readbackMatched) {
                throw new IllegalStateException(
                        "Native D3D12 readback did not match the OpenGL pattern");
            }
            message = "Live OpenGL-D3D12 shared-resource round trip completed";
        } catch (RuntimeException error) {
            message = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
        } finally {
            boolean cleanupSucceeded = true;
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);
                if (semaphore != 0) {
                    EXTSemaphore.glDeleteSemaphoresEXT(semaphore);
                }
                if (texture != 0) {
                    GL11.glDeleteTextures(texture);
                }
                if (memoryObject != 0) {
                    EXTMemoryObject.glDeleteMemoryObjectsEXT(memoryObject);
                }
                requireNoGlError("live interop OpenGL cleanup");
            } catch (RuntimeException cleanupError) {
                cleanupSucceeded = false;
                message = message + "; cleanup: "
                        + (cleanupError.getMessage() == null
                                ? cleanupError.getClass().getSimpleName()
                                : cleanupError.getMessage());
            }
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }
            if (sessionId > 0L) {
                entrypoint.closeD3D12InteropSession(sessionId);
            }
            resourcesReleased = sessionCreated && cleanupSucceeded;
        }

        return new MinecraftLiveInteropSnapshot(
                true,
                sessionCreated,
                memoryImported,
                semaphoreImported,
                openGlWriteSubmitted,
                d3d12ReadbackSubmitted,
                openGlWaitCompleted,
                readbackMatched,
                resourcesReleased,
                message);
    }

    public static String toLogLine(MinecraftLiveInteropSnapshot snapshot) {
        MinecraftLiveInteropSnapshot safe = snapshot == null
                ? MinecraftLiveInteropSnapshot.failure("Live interop snapshot is null")
                : snapshot;
        return "attempted=" + safe.attempted()
                + " sessionCreated=" + safe.sessionCreated()
                + " memoryImported=" + safe.memoryImported()
                + " semaphoreImported=" + safe.semaphoreImported()
                + " openGlWriteSubmitted=" + safe.openGlWriteSubmitted()
                + " d3d12ReadbackSubmitted=" + safe.d3d12ReadbackSubmitted()
                + " openGlWaitCompleted=" + safe.openGlWaitCompleted()
                + " readbackMatched=" + safe.readbackMatched()
                + " resourcesReleased=" + safe.resourcesReleased()
                + " success=" + safe.success()
                + " message=" + safe.message();
    }

    private static void drainGlErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // Isolate the probe from earlier renderer errors.
        }
    }

    private static void requireNoGlError(String operation) {
        int firstError = GL11.glGetError();
        if (firstError == GL11.GL_NO_ERROR) {
            return;
        }
        drainGlErrors();
        throw new IllegalStateException(String.format(
                Locale.ROOT,
                "%s failed with OpenGL error 0x%04x",
                operation,
                firstError));
    }

    private MinecraftLiveInteropProbe() {
    }
}
