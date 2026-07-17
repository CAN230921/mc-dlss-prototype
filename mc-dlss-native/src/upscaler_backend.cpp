#include "upscaler_backend.h"

namespace mc_dlss {

UpscalerBackend selectUpscaler(
    UpscalerBackend requested,
    const BackendAvailability& availability) noexcept {
    if (requested == UpscalerBackend::Native) return UpscalerBackend::Native;
    if (requested == UpscalerBackend::Dlss && availability.dlssSuperResolution) {
        return UpscalerBackend::Dlss;
    }
    if (requested == UpscalerBackend::Fsr3 && availability.fsr3SuperResolution) {
        return UpscalerBackend::Fsr3;
    }
    if (availability.dlssSuperResolution) return UpscalerBackend::Dlss;
    if (availability.fsr3SuperResolution) return UpscalerBackend::Fsr3;
    return UpscalerBackend::Native;
}

FrameGenerationBackend selectFrameGeneration(
    FrameGenerationBackend requested,
    const BackendAvailability& availability) noexcept {
    return requested == FrameGenerationBackend::Fsr3 &&
        availability.fsr3FrameGeneration &&
        availability.frameInputsValid &&
        !availability.frameGenerationLatchedOff
        ? FrameGenerationBackend::Fsr3
        : FrameGenerationBackend::Disabled;
}

}
