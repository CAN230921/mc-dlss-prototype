package dev.mcdlss.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcdlss.core.NativeAdapterIdentity;
import java.nio.ByteBuffer;
import java.util.Locale;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

public final class MinecraftGlInteropProbe {
    private static final int LUID_SIZE = 8;

    public static MinecraftGlInteropSnapshot probe(NativeAdapterIdentity d3d12Identity) {
        if (!RenderSystem.isOnRenderThread()) {
            return MinecraftGlInteropSnapshot.failure(
                    "Live OpenGL probe must run on Minecraft's render thread");
        }

        try {
            GLCapabilities capabilities = GL.getCapabilities();
            String vendor = glString(GL11.GL_VENDOR);
            String renderer = glString(GL11.GL_RENDERER);
            String version = glString(GL11.GL_VERSION);

            boolean memoryObject = capabilities.GL_EXT_memory_object;
            boolean memoryObjectWin32 = capabilities.GL_EXT_memory_object_win32;
            boolean semaphore = capabilities.GL_EXT_semaphore;
            boolean semaphoreWin32 = capabilities.GL_EXT_semaphore_win32;

            String openGlLuid = "";
            String message;
            if (!memoryObject || !memoryObjectWin32 || !semaphore || !semaphoreWin32) {
                message = "Minecraft OpenGL context is missing a required interop extension";
            } else {
                openGlLuid = queryOpenGlLuid();
                message = adapterMessage(openGlLuid, d3d12Identity);
            }

            String d3d12Luid = d3d12Identity != null && d3d12Identity.available()
                    ? d3d12Identity.luid()
                    : "";
            return new MinecraftGlInteropSnapshot(
                    true,
                    vendor,
                    renderer,
                    version,
                    memoryObject,
                    memoryObjectWin32,
                    semaphore,
                    semaphoreWin32,
                    openGlLuid,
                    d3d12Luid,
                    message);
        } catch (RuntimeException error) {
            return MinecraftGlInteropSnapshot.failure(
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    public static String toLogLine(MinecraftGlInteropSnapshot snapshot) {
        MinecraftGlInteropSnapshot safe = snapshot == null
                ? MinecraftGlInteropSnapshot.failure("Live OpenGL snapshot is null")
                : snapshot;
        return "vendor=" + safe.openGlVendor()
                + " renderer=" + safe.openGlRenderer()
                + " version=" + safe.openGlVersion()
                + " memoryObject=" + safe.memoryObjectExtension()
                + " memoryObjectWin32=" + safe.memoryObjectWin32Extension()
                + " semaphore=" + safe.semaphoreExtension()
                + " semaphoreWin32=" + safe.semaphoreWin32Extension()
                + " glLuid=" + safe.openGlLuid()
                + " d3d12Luid=" + safe.d3d12Luid()
                + " adapterLuidMatched=" + safe.adapterLuidMatched()
                + " interopPrerequisitesReady=" + safe.interopPrerequisitesReady()
                + " message=" + safe.message();
    }

    private static String queryOpenGlLuid() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer bytes = stack.malloc(LUID_SIZE);
            EXTMemoryObject.glGetUnsignedBytevEXT(
                    EXTMemoryObjectWin32.GL_DEVICE_LUID_EXT,
                    bytes);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException(String.format(
                        Locale.ROOT,
                        "GL_DEVICE_LUID_EXT query failed with OpenGL error 0x%04x",
                        error));
            }

            StringBuilder luid = new StringBuilder(LUID_SIZE * 2);
            for (int index = 0; index < LUID_SIZE; index++) {
                luid.append(String.format(
                        Locale.ROOT,
                        "%02x",
                        Byte.toUnsignedInt(bytes.get(index))));
            }
            return luid.toString();
        }
    }

    private static String adapterMessage(
            String openGlLuid,
            NativeAdapterIdentity d3d12Identity) {
        if (d3d12Identity == null || !d3d12Identity.available()) {
            String reason = d3d12Identity == null ? "identity is null" : d3d12Identity.message();
            return "D3D12 adapter identity unavailable: " + reason;
        }
        if (openGlLuid.equalsIgnoreCase(d3d12Identity.luid())) {
            return "Minecraft OpenGL and D3D12 adapters match";
        }
        return "Minecraft OpenGL and D3D12 adapter LUID values differ";
    }

    private static String glString(int name) {
        String value = GL11.glGetString(name);
        return value == null ? "" : value;
    }

    private MinecraftGlInteropProbe() {
    }
}
