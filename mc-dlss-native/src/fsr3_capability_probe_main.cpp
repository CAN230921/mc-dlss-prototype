#include "d3d12_probe_context.h"
#include "fsr3_api_runtime.h"

#include <iostream>
#include <string>

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) {
        std::cerr << "usage: mc_dlss_fsr3_capability_probe <loader-dll>\n";
        return 2;
    }
    auto loaded = mc_dlss::Fsr3ApiRuntime::load(argv[1]);
    if (!loaded.runtime) {
        std::cerr << "FSR3 loader failed: " << loaded.message << '\n';
        return 1;
    }
    auto context = mc_dlss::D3D12ProbeContext::create(1920, 1080, 3840, 2160);
    const auto snapshot = loaded.runtime->initializeContexts(
        context->device(), 1920, 1080, 3840, 2160);
    std::cout << mc_dlss::summarizeFsr3Capability(snapshot) << '\n';
    return snapshot.upscaler.state == mc_dlss::Fsr3FeatureState::ContextReady &&
        snapshot.frameGeneration.state == mc_dlss::Fsr3FeatureState::ContextReady
        ? 0 : 1;
}
