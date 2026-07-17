package dev.mcdlss.fabric;

public final class LiveDlssFrameBoundarySelfTest {
    public static void main(String[] args) {
        LiveDlssFrameBoundary<String> boundary = new LiveDlssFrameBoundary<>();
        boundary.begin();
        boundary.captureAtWorldEnd("temporal-frame");
        if (!boundary.active() || boundary.peek() == null) {
            throw new AssertionError("World END must retain data without presenting");
        }
        if (!"temporal-frame".equals(boundary.consumeAfterRenderWorld())) {
            throw new AssertionError("renderWorld return must consume retained data");
        }
        if (boundary.active() || boundary.consumeAfterRenderWorld() != null) {
            throw new AssertionError("Frame boundary must consume exactly once");
        }

        boundary.begin();
        if (boundary.consumeAfterRenderWorld() != null || boundary.active()) {
            throw new AssertionError("Missing World END must close without stale data");
        }
        System.out.println("LiveDlssFrameBoundarySelfTest passed");
    }

    private LiveDlssFrameBoundarySelfTest() {
    }
}
