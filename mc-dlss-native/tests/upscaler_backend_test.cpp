#include "upscaler_backend.h"

#include <iostream>

int main() {
    using namespace mc_dlss;
    BackendAvailability all{true, true, true, true};
    if (selectUpscaler(UpscalerBackend::Fsr3, all) != UpscalerBackend::Fsr3) return 1;
    if (selectUpscaler(UpscalerBackend::Dlss, all) != UpscalerBackend::Dlss) return 1;

    BackendAvailability noFsr{true, false, true, true};
    if (selectUpscaler(UpscalerBackend::Fsr3, noFsr) != UpscalerBackend::Dlss) return 1;

    BackendAvailability fsrOnly{false, true, true, true};
    if (selectUpscaler(UpscalerBackend::Dlss, fsrOnly) != UpscalerBackend::Fsr3) return 1;

    BackendAvailability nativeOnly{};
    if (selectUpscaler(UpscalerBackend::Dlss, nativeOnly) != UpscalerBackend::Native) return 1;

    if (selectFrameGeneration(FrameGenerationBackend::Fsr3, all) !=
        FrameGenerationBackend::Fsr3) return 1;
    all.frameInputsValid = false;
    if (selectFrameGeneration(FrameGenerationBackend::Fsr3, all) !=
        FrameGenerationBackend::Disabled) return 1;
    all.frameInputsValid = true;
    all.frameGenerationLatchedOff = true;
    if (selectFrameGeneration(FrameGenerationBackend::Fsr3, all) !=
        FrameGenerationBackend::Disabled) return 1;

    std::cout << "upscaler_backend_test passed\n";
    return 0;
}
