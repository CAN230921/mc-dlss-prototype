package dev.mcdlss.core;

public final class StableCandidateGateSelfTest {
    public static void runAll() {
        StableCandidateGate<String> gate = new StableCandidateGate<>(2);
        require(!gate.observe("generation-1"), "First observation must remain transitional");
        require(gate.observe("generation-1"), "Repeated candidate must become stable");
        require(!gate.observe("generation-2"), "Changed candidate must restart stabilization");
        gate.clear();
        require(!gate.observe("generation-2"), "Clear must discard the previous observation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private StableCandidateGateSelfTest() {
    }
}
