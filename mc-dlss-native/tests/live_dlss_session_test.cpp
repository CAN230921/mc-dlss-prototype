#include "live_dlss_registry.h"

#include <iostream>

int main() {
    const auto qualitySize = mc_dlss::liveDlssRenderDimensions(
        1920, 1080, mc_dlss::LiveDlssQualityMode::quality);
    const auto balancedSize = mc_dlss::liveDlssRenderDimensions(
        1920, 1080, mc_dlss::LiveDlssQualityMode::balanced);
    const auto performanceSize = mc_dlss::liveDlssRenderDimensions(
        1920, 1080, mc_dlss::LiveDlssQualityMode::performance);
    const auto ultraSize = mc_dlss::liveDlssRenderDimensions(
        1920, 1080, mc_dlss::LiveDlssQualityMode::ultraPerformance);
    if (qualitySize[0] != 1280 || qualitySize[1] != 720
        || balancedSize[0] != 1114 || balancedSize[1] != 626
        || performanceSize[0] != 960 || performanceSize[1] != 540
        || ultraSize[0] != 640 || ultraSize[1] != 360) {
        std::cerr << "live DLSS quality render dimensions mismatch\n";
        return 1;
    }
    if (static_cast<std::uint32_t>(mc_dlss::LiveUpscalerExecutionMode::validating) != 0
        || static_cast<std::uint32_t>(mc_dlss::LiveUpscalerExecutionMode::fast) != 1) {
        std::cerr << "live upscaler execution mode values changed\n";
        return 1;
    }
    const auto session = mc_dlss::openLiveDlssSession(1280, 720, 1920, 1080);
    if (!session.available() || session.renderWidth != 1280
        || session.renderHeight != 720 || session.outputWidth != 1920
        || session.outputHeight != 1080) {
        std::cerr << "live DLSS session did not open\n";
        return 1;
    }
    for (const auto& slot : session.slots) {
        if (!slot.available()) {
            std::cerr << "live DLSS slot handles are incomplete\n";
            return 1;
        }
    }
    if (session.slots[0].colorTextureHandle == session.slots[1].colorTextureHandle
        || session.slots[0].outputTextureHandle == session.slots[1].outputTextureHandle) {
        std::cerr << "live DLSS slots share texture handles\n";
        return 1;
    }
    const auto nativeSession = mc_dlss::findLiveDlssSession(session.sessionId);
    if (nativeSession == nullptr || !nativeSession->slotReady(0)
        || !nativeSession->slotReady(1) || nativeSession->slotReady(2)) {
        std::cerr << "fresh live upscaler slot readiness mismatch\n";
        return 1;
    }
    const mc_dlss::LiveDlssFrameInspection emptyInspection{};
    if (emptyInspection.diagnosticReadbackRequested) {
        std::cerr << "empty inspection requested diagnostic readback\n";
        return 1;
    }
    mc_dlss::closeLiveDlssSession(session.sessionId);
    mc_dlss::closeLiveDlssSession(session.sessionId);
    if (mc_dlss::hasLiveDlssSession(session.sessionId)) {
        std::cerr << "closed live DLSS session remained registered\n";
        return 1;
    }
    if (mc_dlss::openLiveDlssSession(0, 720, 1920, 1080).available()
        || mc_dlss::openLiveDlssSession(1920, 1080, 1920, 1080).available()) {
        std::cerr << "invalid live DLSS dimensions were accepted\n";
        return 1;
    }
    std::cout << "live_dlss_session_test passed\n";
    return 0;
}
