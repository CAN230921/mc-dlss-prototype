package dev.mcdlss.fabric;

public final class LiveDlssPresentationSnapshotSelfTest {
    public static void main(String[] args) {
        LiveDlssPresentationSnapshot gated = snapshot(
                LiveDlssPresentationState.CAPTURING, 30, 0, 15, 15,
                29, 0, false, false, "");
        if (!gated.initialGatePassed() || gated.dlssReady()) {
            throw new AssertionError("The 30-frame gate must precede final readiness");
        }

        LiveDlssPresentationSnapshot complete = snapshot(
                LiveDlssPresentationState.COMPLETE, 30, 300, 166, 165,
                119, 0, true, false, "");
        if (!complete.dlssReady()) {
            throw new AssertionError("Expected a ready live DLSS presentation");
        }
        if (!complete.outputHashesMatch()) {
            throw new AssertionError("Expected matching GL and D3D12 output hashes");
        }

        if (snapshot(LiveDlssPresentationState.COMPLETE, 30, 300, 164, 167,
                119, 0, true, false, "").dlssReady()) {
            throw new AssertionError("Unbalanced A/B usage was accepted");
        }
        if (snapshot(LiveDlssPresentationState.COMPLETE, 30, 299, 165, 165,
                119, 0, true, false, "").dlssReady()) {
            throw new AssertionError("Insufficient retained frames were accepted");
        }
        if (snapshot(LiveDlssPresentationState.COMPLETE, 30, 300, 165, 166,
                0, 0, true, false, "").dlssReady()) {
            throw new AssertionError("Static output hashes were accepted");
        }

        LiveDlssPresentationSnapshot failed = snapshot(
                LiveDlssPresentationState.FAILED, 17, 0, 9, 8,
                16, 1, false, true, "EVALUATE");
        if (!failed.fallbackRecovered() || failed.dlssReady()) {
            throw new AssertionError("Expected healthy vanilla fallback after failure");
        }

        String overlay = String.join("\n",
                LiveDlssPresentationOverlayLines.fromSnapshot(complete));
        if (!overlay.contains("live-dlss: true")
                || !overlay.contains("569x320 -> 854x480")
                || !overlay.contains("frames=30/30 retained=300/300")
                || !overlay.contains("slots=166/165")) {
            throw new AssertionError("Live DLSS overlay mismatch");
        }
        System.out.println("LiveDlssPresentationSnapshotSelfTest passed");
    }

    private static LiveDlssPresentationSnapshot snapshot(
            LiveDlssPresentationState state,
            int frames,
            int retained,
            int slotA,
            int slotB,
            int hashChanges,
            int fallbackFrames,
            boolean resourcesRetained,
            boolean resourcesReleased,
            String failureStage) {
        return new LiveDlssPresentationSnapshot(
                state, 569, 320, 854, 480,
                frames, 30, retained, 300, slotA, slotB,
                0, hashChanges, "6ebdde4d158cd056", "6ebdde4d158cd056",
                854 * 480 * 4, 303537, true, 1,
                fallbackFrames, true, resourcesRetained, resourcesReleased,
                failureStage, 14.5, 31.0, "test");
    }

    private LiveDlssPresentationSnapshotSelfTest() {
    }
}
