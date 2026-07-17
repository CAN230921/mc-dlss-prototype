package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftLiveInteropSnapshotSelfTest {
    public static void main(String[] args) {
        requiresEveryStageForSuccess();
        formatsAllCriticalStages();
        usesTheNativeInteropPattern();
        suppliesEmptyBufferBarriersWithoutNull();
        System.out.println("MinecraftLiveInteropSnapshotSelfTest passed");
    }

    private static void suppliesEmptyBufferBarriersWithoutNull() {
        MinecraftInteropSemaphoreBarriers barriers =
                MinecraftInteropSemaphoreBarriers.forTexture(17, 23);
        if (barriers.buffers() == null || barriers.buffers().length != 0) {
            throw new AssertionError("Buffer barriers must be a non-null empty array");
        }
        if (barriers.textures().length != 1 || barriers.textures()[0] != 17) {
            throw new AssertionError("Expected one texture barrier");
        }
        if (barriers.layouts().length != 1 || barriers.layouts()[0] != 23) {
            throw new AssertionError("Expected one matching texture layout");
        }
    }

    private static void usesTheNativeInteropPattern() {
        byte[] pixels = MinecraftInteropTestPattern.createRgba8(2, 2);
        int[] required = {
                0, 0, 0, 255,
                3, 1, 1, 255,
                1, 5, 1, 255,
                4, 6, 0, 255
        };
        if (pixels.length != required.length) {
            throw new AssertionError("Unexpected pattern length");
        }
        for (int index = 0; index < required.length; index++) {
            if (Byte.toUnsignedInt(pixels[index]) != required[index]) {
                throw new AssertionError("Pattern mismatch at byte " + index);
            }
        }
    }

    private static void requiresEveryStageForSuccess() {
        MinecraftLiveInteropSnapshot complete = completeSnapshot();
        if (!complete.success()) {
            throw new AssertionError("Every completed stage should report success");
        }

        MinecraftLiveInteropSnapshot missingWait = new MinecraftLiveInteropSnapshot(
                true, true, true, true, true, true,
                false, true, true, "OpenGL wait missing");
        if (missingWait.success()) {
            throw new AssertionError("Missing OpenGL wait must block success");
        }
    }

    private static void formatsAllCriticalStages() {
        List<String> lines = MinecraftLiveInteropOverlayLines.fromSnapshot(completeSnapshot());
        String joined = String.join("\n", lines);
        if (!joined.contains("live-share: true")) {
            throw new AssertionError("Expected overall live-share state");
        }
        if (!joined.contains("gl-write=true")
                || !joined.contains("d3d-submit=true")
                || !joined.contains("gl-wait=true")) {
            throw new AssertionError("Expected synchronization stages");
        }
        if (!joined.contains("readback=true") || !joined.contains("released=true")) {
            throw new AssertionError("Expected verification and release stages");
        }
    }

    private static MinecraftLiveInteropSnapshot completeSnapshot() {
        return new MinecraftLiveInteropSnapshot(
                true, true, true, true, true, true,
                true, true, true, "Live shared resource round trip completed");
    }

    private MinecraftLiveInteropSnapshotSelfTest() {
    }
}
