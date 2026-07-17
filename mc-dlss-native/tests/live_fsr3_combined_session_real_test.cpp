#include "live_dlss_session.h"

#include <chrono>
#include <iostream>
#include <thread>

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) return 2;
    auto session = mc_dlss::LiveDlssSession::create(1920, 1080, 3840, 2160);
    const auto capability = session->initializeFsr3(argv[1]);
    if (capability.upscaler.state != mc_dlss::Fsr3FeatureState::ContextReady ||
        capability.frameGeneration.state != mc_dlss::Fsr3FeatureState::ContextReady) {
        return 1;
    }
    if (FAILED(session->fence(0)->Signal(1))) return 1;
    mc_dlss::LiveDlssConstantsData constants{};
    constants.cameraUp[1] = 1.0F;
    constants.cameraRight[0] = 1.0F;
    constants.cameraForward[2] = -1.0F;
    constants.cameraNear = 0.1F;
    constants.cameraFar = 1000.0F;
    constants.cameraFov = 1.2F;
    constants.motionScaleX = 1920.0F;
    constants.motionScaleY = 1080.0F;
    constants.reset = true;
    if (!session->submitFsr3Upscale(0, 1, 2, constants, 16.67F, true)) {
        std::cerr << "Combined FSR3 submission failed\n";
        return 1;
    }
    for (int attempt = 0; attempt < 500 &&
            session->fence(0)->GetCompletedValue() < 2; ++attempt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
    if (session->fence(0)->GetCompletedValue() < 2) {
        std::cerr << "Combined FSR3 fence timed out\n";
        return 1;
    }
    if (session->slotInfo(0).generatedTextureHandle == 0) return 1;
    std::cout << "live_fsr3_combined_session_real_test passed\n";
    return 0;
}
