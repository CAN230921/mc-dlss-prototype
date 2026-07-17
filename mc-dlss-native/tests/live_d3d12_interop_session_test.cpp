#include "live_d3d12_interop_registry.h"
#include "live_d3d12_interop_session.h"

#include <iostream>

int main() {
    if (!mc_dlss::validPersistentFencePair(0, 1, 2) ||
        !mc_dlss::validPersistentFencePair(2, 3, 4) ||
        mc_dlss::validPersistentFencePair(2, 5, 6) ||
        mc_dlss::validPersistentFencePair(2, 2, 3)) {
        std::cerr << "persistent fence validation mismatch\n";
        return 1;
    }

    const auto invalid = mc_dlss::openLiveD3D12InteropSession(32, 64);
    if (invalid.available()) {
        std::cerr << "unsupported dimensions were accepted\n";
        return 1;
    }
    if (mc_dlss::submitLiveD3D12InteropReadback(999999) ||
        mc_dlss::verifyLiveD3D12InteropReadback(999999)) {
        std::cerr << "unknown session ID was accepted\n";
        return 1;
    }
    const auto missingInspection =
        mc_dlss::inspectPersistentD3D12InteropReadback(999999, 2);
    if (missingInspection.status !=
        mc_dlss::InteropReadbackStatus::missingSession) {
        std::cerr << "missing persistent session diagnostic mismatch\n";
        return 1;
    }

    const auto opened = mc_dlss::openLiveD3D12InteropSession(64, 64);
    if (!opened.available() || opened.sessionId == 0 ||
        opened.textureHandle == 0 || opened.fenceHandle == 0) {
        std::cerr << "64 x 64 live session did not expose valid handles\n";
        return 1;
    }
    if (!mc_dlss::hasLiveD3D12InteropSession(opened.sessionId)) {
        std::cerr << "opened session was not registered\n";
        return 1;
    }

    mc_dlss::closeLiveD3D12InteropSession(opened.sessionId);
    mc_dlss::closeLiveD3D12InteropSession(opened.sessionId);
    if (mc_dlss::hasLiveD3D12InteropSession(opened.sessionId)) {
        std::cerr << "closed session remained registered\n";
        return 1;
    }

    const auto persistent = mc_dlss::openPersistentD3D12InteropSession(1920, 1080);
    if (!persistent.available()) {
        std::cerr << "full-resolution persistent session did not open\n";
        return 1;
    }
    const auto rejectedInspection =
        mc_dlss::inspectPersistentD3D12InteropReadback(persistent.sessionId, 2);
    if (rejectedInspection.status !=
            mc_dlss::InteropReadbackStatus::invalidRequest ||
        rejectedInspection.lastSubmittedSignal != 0) {
        std::cerr << "unsubmitted persistent inspection diagnostic mismatch\n";
        return 1;
    }
    mc_dlss::closeLiveD3D12InteropSession(persistent.sessionId);

    if (mc_dlss::openPersistentD3D12InteropSession(0, 1080).available() ||
        mc_dlss::openPersistentD3D12InteropSession(8193, 1080).available()) {
        std::cerr << "invalid persistent dimensions were accepted\n";
        return 1;
    }

    std::cout << "live_d3d12_interop_session_test passed\n";
    return 0;
}
