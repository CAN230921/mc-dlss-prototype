package dev.mcdlss.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.Locale;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/** OpenGL ownership of one A/B live-DLSS shared-resource slot. */
public final class LiveDlssGlSlot implements AutoCloseable {
    private final int renderWidth;
    private final int renderHeight;
    private final int outputWidth;
    private final int outputHeight;
    private final int[] memories = new int[4];
    private final int[] textures = new int[4];
    private final int[] framebuffers = new int[4];
    private final int[] layouts = {
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT
    };
    private final ByteBuffer outputBuffer;
    private final byte[] outputBytes;
    private int semaphore;
    private boolean closed;

    public LiveDlssGlSlot(
            int renderWidth,
            int renderHeight,
            int outputWidth,
            int outputHeight,
            long colorHandle,
            long depthHandle,
            long motionHandle,
            long outputHandle,
            long fenceHandle) {
        requireRenderThread();
        if (renderWidth <= 0 || renderHeight <= 0
                || outputWidth <= renderWidth || outputHeight <= renderHeight
                || colorHandle <= 0 || depthHandle <= 0 || motionHandle <= 0
                || outputHandle <= 0 || fenceHandle <= 0) {
            throw new IllegalArgumentException("Live DLSS GL slot resources are invalid");
        }
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        int outputByteCount = Math.multiplyExact(
                Math.multiplyExact(outputWidth, outputHeight), 8);
        outputBuffer = MemoryUtil.memAlloc(outputByteCount);
        outputBytes = new byte[outputByteCount];

        GlState saved = GlState.capture();
        drainGlErrors();
        try {
            memories[0] = importMemory(colorHandle);
            memories[1] = importMemory(depthHandle);
            memories[2] = importMemory(motionHandle);
            memories[3] = importMemory(outputHandle);
            textures[0] = createTexture(
                    memories[0], GL30.GL_RGBA16F, renderWidth, renderHeight);
            textures[1] = createTexture(
                    memories[1], GL30.GL_R32F, renderWidth, renderHeight);
            textures[2] = createTexture(
                    memories[2], GL30.GL_RG16F, renderWidth, renderHeight);
            textures[3] = createTexture(
                    memories[3], GL30.GL_RGBA16F, outputWidth, outputHeight);
            for (int index = 0; index < framebuffers.length; index++) {
                framebuffers[index] = createFramebuffer(textures[index]);
            }
            semaphore = EXTSemaphore.glGenSemaphoresEXT();
            EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT,
                    fenceHandle);
            requireNoGlError("import live DLSS semaphore");
        } catch (RuntimeException error) {
            releaseGlResources();
            MemoryUtil.memFree(outputBuffer);
            closed = true;
            throw error;
        } finally {
            saved.restore();
        }
    }

    public void populateInputs(
            Framebuffer source,
            MinecraftDepthExtractionShader depthShader,
            MinecraftMotionVectorShader motionShader,
            CameraTemporalReprojection reprojection) {
        requireOpen();
        requireRenderThread();
        if (source == null || source.fbo <= 0 || source.getDepthAttachment() <= 0
                || source.textureWidth != renderWidth || source.textureHeight != renderHeight
                || depthShader == null || motionShader == null || reprojection == null) {
            throw new IllegalArgumentException("Live DLSS input resources are invalid");
        }
        GlState saved = GlState.capture();
        drainGlErrors();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[0]);
            GL30.glBlitFramebuffer(
                    0, 0, renderWidth, renderHeight,
                    0, 0, renderWidth, renderHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            requireNoGlError("copy live DLSS FP16 color");

            depthShader.render(
                    source.getDepthAttachment(), framebuffers[1], renderWidth, renderHeight);
            requireNoGlError("extract live DLSS depth");
            motionShader.render(
                    source.getDepthAttachment(), framebuffers[2], renderWidth, renderHeight,
                    reprojection.clipToPrevClip(), reprojection.reset());
            requireNoGlError("generate live DLSS motion");
        } finally {
            saved.restore();
        }
    }

    public void signalInputs(long fenceValue) {
        requireOpen();
        requireRenderThread();
        requireFenceValue(fenceValue);
        EXTSemaphore.glSemaphoreParameterui64EXT(
                semaphore, EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT, fenceValue);
        EXTSemaphore.glSignalSemaphoreEXT(semaphore, new int[0], textures, layouts);
        GL11.glFlush();
        requireNoGlError("signal live DLSS inputs");
    }

    public void waitForOutput(long fenceValue) {
        requireOpen();
        requireRenderThread();
        requireFenceValue(fenceValue);
        EXTSemaphore.glSemaphoreParameterui64EXT(
                semaphore, EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT, fenceValue);
        EXTSemaphore.glWaitSemaphoreEXT(semaphore, new int[0], textures, layouts);
        GL11.glFinish();
        requireNoGlError("wait for live DLSS output");
    }

    public void queueWaitForOutput(long fenceValue) {
        requireOpen();
        requireRenderThread();
        requireFenceValue(fenceValue);
        EXTSemaphore.glSemaphoreParameterui64EXT(
                semaphore, EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT, fenceValue);
        EXTSemaphore.glWaitSemaphoreEXT(semaphore, new int[0], textures, layouts);
        requireNoGlError("queue wait for live upscaler output");
    }

    public Fp16ColorFingerprint readOutputFingerprint() {
        requireOpen();
        requireRenderThread();
        GlState saved = GlState.capture();
        drainGlErrors();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffers[3]);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            outputBuffer.clear();
            GL11.glReadPixels(
                    0, 0, outputWidth, outputHeight,
                    GL11.GL_RGBA, GL30.GL_HALF_FLOAT, outputBuffer);
            requireNoGlError("read live DLSS output");
            outputBuffer.get(0, outputBytes);
            return Fp16ColorFingerprint.fromRgba16f(outputBytes);
        } finally {
            saved.restore();
        }
    }

    public void compositeTo(Framebuffer target) {
        requireOpen();
        requireRenderThread();
        if (target == null || target.fbo <= 0) {
            throw new IllegalArgumentException("Live DLSS composite target is invalid");
        }
        LiveDlssOutputComposite.validateExactOutput(
                outputWidth, outputHeight, target.textureWidth, target.textureHeight);
        GlState saved = GlState.capture();
        drainGlErrors();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffers[3]);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
            GL30.glBlitFramebuffer(
                    0, 0, outputWidth, outputHeight,
                    0, 0, outputWidth, outputHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            requireNoGlError("composite live DLSS output");
        } finally {
            saved.restore();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        requireRenderThread();
        GlState saved = GlState.capture();
        drainGlErrors();
        try {
            releaseGlResources();
            requireNoGlError("release live DLSS GL slot");
        } finally {
            saved.restore();
            MemoryUtil.memFree(outputBuffer);
            closed = true;
        }
    }

    private void releaseGlResources() {
        for (int framebuffer : framebuffers) {
            if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        }
        if (semaphore != 0) {
            EXTSemaphore.glDeleteSemaphoresEXT(semaphore);
            semaphore = 0;
        }
        for (int texture : textures) {
            if (texture != 0) GL11.glDeleteTextures(texture);
        }
        for (int memory : memories) {
            if (memory != 0) EXTMemoryObject.glDeleteMemoryObjectsEXT(memory);
        }
    }

    private static int importMemory(long handle) {
        int memory = EXTMemoryObject.glCreateMemoryObjectsEXT();
        EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                memory, 0L, EXTMemoryObjectWin32.GL_HANDLE_TYPE_D3D12_RESOURCE_EXT, handle);
        requireNoGlError("import live DLSS memory");
        return memory;
    }

    private static int createTexture(int memory, int format, int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        EXTMemoryObject.glTexStorageMem2DEXT(
                GL11.GL_TEXTURE_2D, 1, format, width, height, memory, 0L);
        requireNoGlError("allocate live DLSS texture");
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
            throw new IllegalStateException("Live DLSS framebuffer is incomplete");
        }
        requireNoGlError("attach live DLSS texture");
        return framebuffer;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Live DLSS GL slot is closed");
    }

    private static void requireFenceValue(long value) {
        if (value <= 0) throw new IllegalArgumentException("Fence value must be positive");
    }

    private static void requireRenderThread() {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Live DLSS GL work requires the render thread");
        }
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
}
