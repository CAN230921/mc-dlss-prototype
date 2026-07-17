package dev.mcdlss.neoforge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreWin32;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

public final class IrisDlssGlInputSlot implements AutoCloseable {
    private final int renderWidth;
    private final int renderHeight;
    private final int outputWidth;
    private final int outputHeight;
    private final int[] memories = new int[5];
    private final int[] textures = new int[5];
    private final int[] framebuffers = new int[5];
    private final int[] layouts = {
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT
    };
    private final int sharedTextureCount;
    private int sourceFramebuffer;
    private int semaphore;
    private boolean closed;

    public IrisDlssGlInputSlot(
            int renderWidth,
            int renderHeight,
            int outputWidth,
            int outputHeight,
            long colorHandle,
            long depthHandle,
            long motionHandle,
            long outputHandle,
            long fenceHandle) {
        this(renderWidth, renderHeight, outputWidth, outputHeight,
                colorHandle, depthHandle, motionHandle, outputHandle,
                fenceHandle, 0L);
    }

    public IrisDlssGlInputSlot(
            int renderWidth,
            int renderHeight,
            int outputWidth,
            int outputHeight,
            long colorHandle,
            long depthHandle,
            long motionHandle,
            long outputHandle,
            long fenceHandle,
            long generatedHandle) {
        requireRenderThread();
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.sharedTextureCount = generatedHandle == 0L ? 4 : 5;
        GlState saved = GlState.capture();
        try {
            memories[0] = importMemory(colorHandle);
            memories[1] = importMemory(depthHandle);
            memories[2] = importMemory(motionHandle);
            memories[3] = importMemory(outputHandle);
            if (generatedHandle != 0L) memories[4] = importMemory(generatedHandle);
            textures[0] = createTexture(memories[0], GL30.GL_RGBA16F, renderWidth, renderHeight);
            textures[1] = createTexture(memories[1], GL30.GL_R32F, renderWidth, renderHeight);
            textures[2] = createTexture(memories[2], GL30.GL_RG16F, renderWidth, renderHeight);
            textures[3] = createTexture(memories[3], GL30.GL_RGBA16F, outputWidth, outputHeight);
            if (generatedHandle != 0L) {
                textures[4] = createTexture(memories[4], GL30.GL_RGBA16F,
                        outputWidth, outputHeight);
            }
            for (int index = 0; index < sharedTextureCount; index++) {
                framebuffers[index] = createFramebuffer(textures[index]);
            }
            sourceFramebuffer = GL30.glGenFramebuffers();
            semaphore = EXTSemaphore.glGenSemaphoresEXT();
            EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT(
                    semaphore,
                    EXTSemaphoreWin32.GL_HANDLE_TYPE_D3D12_FENCE_EXT,
                    fenceHandle);
            requireNoGlError("create Iris DLSS input slot");
        } finally {
            saved.restore();
        }
    }

    public void signalInputs(long fenceValue) {
        requireRenderThread();
        EXTSemaphore.glSemaphoreParameterui64EXT(
                semaphore, EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT, fenceValue);
        EXTSemaphore.glSignalSemaphoreEXT(semaphore, new int[0],
                Arrays.copyOf(textures, sharedTextureCount),
                Arrays.copyOf(layouts, sharedTextureCount));
        GL11.glFlush();
        requireNoGlError("signal Iris DLSS inputs");
    }

    public void waitForOutput(long fenceValue) {
        requireRenderThread();
        EXTSemaphore.glSemaphoreParameterui64EXT(
                semaphore, EXTSemaphoreWin32.GL_D3D12_FENCE_VALUE_EXT, fenceValue);
        EXTSemaphore.glWaitSemaphoreEXT(semaphore, new int[0],
                Arrays.copyOf(textures, sharedTextureCount),
                Arrays.copyOf(layouts, sharedTextureCount));
        GlState saved = GlState.capture();
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[3]);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColorMask(false, false, false, true);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        } finally {
            saved.restore();
        }
        requireNoGlError("wait for Iris DLSS output");
    }

    public void compositeTo(RenderTarget target) {
        requireRenderThread();
        if (closed || target == null || target.frameBufferId <= 0) {
            throw new IllegalArgumentException("Iris DLSS composite target is invalid");
        }
        GlState saved = GlState.capture();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffers[3]);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(
                    0, 0, target.width, target.height,
                    0, 0, target.width, target.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            requireNoGlError("composite Iris DLSS output");
        } finally {
            saved.restore();
        }
    }

    public void compositeGeneratedTo(RenderTarget target) {
        requireRenderThread();
        if (sharedTextureCount != 5 || closed || target == null
                || target.frameBufferId <= 0) {
            throw new IllegalStateException("FSR3 generated output is unavailable");
        }
        GlState saved = GlState.capture();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffers[4]);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(
                    0, 0, outputWidth, outputHeight,
                    0, 0, target.width, target.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            requireNoGlError("composite FSR3 generated output");
        } finally {
            saved.restore();
        }
    }

    public int outputTexture() {
        return textures[3];
    }

    public int generatedTexture() {
        return sharedTextureCount == 5 ? textures[4] : 0;
    }

    public void populate(
            int sourceColorTexture,
            int sourceDepthTexture,
            int sourceWidth,
            int sourceHeight,
            IrisDepthDownsampleShader depthShader,
            IrisMotionVectorShader motionShader,
            IrisTemporalCapture.Frame temporalFrame) {
        requireRenderThread();
        if (closed || sourceColorTexture <= 0 || sourceDepthTexture <= 0
                || sourceWidth <= 0 || sourceHeight <= 0 || depthShader == null
                || motionShader == null || temporalFrame == null) {
            throw new IllegalArgumentException("Iris DLSS source resources are invalid");
        }
        GlState saved = GlState.capture();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glFramebufferTexture2D(
                    GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, sourceColorTexture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)
                    != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Iris color framebuffer is incomplete");
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffers[0]);
            GL30.glBlitFramebuffer(
                    0, 0, sourceWidth, sourceHeight,
                    0, 0, renderWidth, renderHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            requireNoGlError("downsample Iris color");

            depthShader.render(
                    sourceDepthTexture, framebuffers[1],
                    sourceWidth, sourceHeight, renderWidth, renderHeight);
            requireNoGlError("downsample Iris depth");

            motionShader.render(
                    sourceDepthTexture, framebuffers[2], renderWidth, renderHeight,
                    temporalFrame.clipToPrevClip(), temporalFrame.constants().reset());
            requireNoGlError("generate Iris camera motion");
        } finally {
            saved.restore();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        requireRenderThread();
        if (sourceFramebuffer != 0) GL30.glDeleteFramebuffers(sourceFramebuffer);
        for (int framebuffer : framebuffers) if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        if (semaphore != 0) EXTSemaphore.glDeleteSemaphoresEXT(semaphore);
        for (int texture : textures) if (texture != 0) GL11.glDeleteTextures(texture);
        for (int memory : memories) if (memory != 0) EXTMemoryObject.glDeleteMemoryObjectsEXT(memory);
        closed = true;
    }

    private static int importMemory(long handle) {
        int memory = EXTMemoryObject.glCreateMemoryObjectsEXT();
        EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(
                memory, 0L, EXTMemoryObjectWin32.GL_HANDLE_TYPE_D3D12_RESOURCE_EXT, handle);
        requireNoGlError("import Iris DLSS memory");
        return memory;
    }

    private static int createTexture(int memory, int format, int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        EXTMemoryObject.glTexStorageMem2DEXT(
                GL11.GL_TEXTURE_2D, 1, format, width, height, memory, 0L);
        requireNoGlError("allocate Iris DLSS texture");
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
            throw new IllegalStateException("Iris DLSS shared framebuffer is incomplete");
        }
        return framebuffer;
    }

    private static void requireRenderThread() {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Iris DLSS GL work requires the render thread");
        }
    }

    private static void requireNoGlError(String operation) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT, "%s failed with OpenGL error 0x%04x", operation, error));
        }
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
            boolean[] colorMask,
            float[] clearColor) {
        static GlState capture() {
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(active);
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            ByteBuffer mask = MemoryUtil.memAlloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            boolean[] colorMask = {
                mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0
            };
            MemoryUtil.memFree(mask);
            float[] clearColor = new float[4];
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            return new GlState(
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    active, texture0, viewport,
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE), colorMask, clearColor);
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
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        }
    }
}
