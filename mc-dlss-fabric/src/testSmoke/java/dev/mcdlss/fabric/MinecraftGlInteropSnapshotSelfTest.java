package dev.mcdlss.fabric;

import java.util.List;

public final class MinecraftGlInteropSnapshotSelfTest {
    public static void main(String[] args) {
        reportsReadyForMatchingAdapters();
        rejectsMissingExtension();
        rejectsMismatchedOrMalformedLuids();
        formatsCompactOverlay();
        System.out.println("MinecraftGlInteropSnapshotSelfTest passed");
    }

    private static void reportsReadyForMatchingAdapters() {
        MinecraftGlInteropSnapshot snapshot = completeSnapshot();
        if (!snapshot.adapterLuidMatched() || !snapshot.interopPrerequisitesReady()) {
            throw new AssertionError("Expected matching live interop prerequisites");
        }
    }

    private static void rejectsMissingExtension() {
        MinecraftGlInteropSnapshot complete = completeSnapshot();
        MinecraftGlInteropSnapshot missingSemaphore = new MinecraftGlInteropSnapshot(
                true,
                complete.openGlVendor(),
                complete.openGlRenderer(),
                complete.openGlVersion(),
                true,
                true,
                false,
                true,
                complete.openGlLuid(),
                complete.d3d12Luid(),
                "Missing GL_EXT_semaphore");
        if (missingSemaphore.interopPrerequisitesReady()) {
            throw new AssertionError("Missing extension must block readiness");
        }
    }

    private static void rejectsMismatchedOrMalformedLuids() {
        MinecraftGlInteropSnapshot complete = completeSnapshot();
        MinecraftGlInteropSnapshot mismatch = new MinecraftGlInteropSnapshot(
                true,
                complete.openGlVendor(),
                complete.openGlRenderer(),
                complete.openGlVersion(),
                true,
                true,
                true,
                true,
                "0011223344556677",
                "8899aabbccddeeff",
                "Adapter mismatch");
        if (mismatch.adapterLuidMatched() || mismatch.interopPrerequisitesReady()) {
            throw new AssertionError("Mismatched adapters must block readiness");
        }

        MinecraftGlInteropSnapshot malformed = new MinecraftGlInteropSnapshot(
                true, "vendor", "renderer", "version",
                true, true, true, true,
                "1234", "1234", "Malformed LUID");
        if (malformed.adapterLuidMatched() || malformed.interopPrerequisitesReady()) {
            throw new AssertionError("Malformed LUID must block readiness");
        }
    }

    private static void formatsCompactOverlay() {
        List<String> lines = MinecraftGlInteropOverlayLines.fromSnapshot(completeSnapshot());
        String joined = String.join("\n", lines);
        if (!joined.contains("interop-ready: true")) {
            throw new AssertionError("Expected readiness line");
        }
        if (!joined.contains("NVIDIA GeForce RTX 4070")) {
            throw new AssertionError("Expected renderer line");
        }
        if (!joined.contains("e139010000000000")) {
            throw new AssertionError("Expected adapter LUID line");
        }
    }

    private static MinecraftGlInteropSnapshot completeSnapshot() {
        return new MinecraftGlInteropSnapshot(
                true,
                "NVIDIA Corporation",
                "NVIDIA GeForce RTX 4070 Laptop GPU/PCIe/SSE2",
                "4.6.0 NVIDIA 610.62",
                true,
                true,
                true,
                true,
                "E139010000000000",
                "e139010000000000",
                "Minecraft OpenGL and D3D12 adapters match");
    }

    private MinecraftGlInteropSnapshotSelfTest() {
    }
}
