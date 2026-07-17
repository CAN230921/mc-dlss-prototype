#include "live_d3d12_frame_registry.h"

#include <iostream>

int main() {
    const auto missing = mc_dlss::inspectPersistentD3D12FrameReadback(999999, 2);
    if (missing.status != mc_dlss::InteropReadbackStatus::missingSession) {
        std::cerr << "missing frame session diagnostic mismatch\n";
        return 1;
    }
    const auto session = mc_dlss::openPersistentD3D12FrameSession(854, 480);
    if (!session.available() || session.colorTextureHandle == 0
        || session.depthTextureHandle == 0 || session.motionTextureHandle == 0
        || session.fenceHandle == 0) {
        std::cerr << "full-resolution frame session did not open\n";
        return 1;
    }
    const auto rejected = mc_dlss::inspectPersistentD3D12FrameReadback(
        session.sessionId, 2);
    if (rejected.status != mc_dlss::InteropReadbackStatus::invalidRequest
        || rejected.lastSubmittedSignal != 0) {
        std::cerr << "unsubmitted frame inspection diagnostic mismatch\n";
        return 1;
    }
    mc_dlss::closePersistentD3D12FrameSession(session.sessionId);
    mc_dlss::closePersistentD3D12FrameSession(session.sessionId);
    if (mc_dlss::hasPersistentD3D12FrameSession(session.sessionId)) {
        std::cerr << "closed frame session remained registered\n";
        return 1;
    }
    if (mc_dlss::openPersistentD3D12FrameSession(0, 480).available()
        || mc_dlss::openPersistentD3D12FrameSession(8193, 480).available()) {
        std::cerr << "invalid frame dimensions were accepted\n";
        return 1;
    }
    std::cout << "live_d3d12_frame_session_test passed\n";
    return 0;
}
