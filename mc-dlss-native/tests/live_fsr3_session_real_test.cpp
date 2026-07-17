#include "d3d12_probe_context.h"
#include "live_fsr3_session.h"

#include <iostream>

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) return 2;
    auto device = mc_dlss::D3D12ProbeContext::create(1920, 1080, 3840, 2160);
    for (int iteration = 0; iteration < 3; ++iteration) {
        auto session = mc_dlss::LiveFsr3Session::create(
            device->device(), argv[1], 1920, 1080, 3840, 2160);
        if (!session || !session->superResolutionReady() ||
            !session->frameGenerationReady()) {
            std::cerr << "Live FSR3 context creation failed at iteration "
                      << iteration << '\n';
            return 1;
        }
    }
    std::cout << "live_fsr3_session_real_test passed\n";
    return 0;
}
