#pragma once

namespace mc_dlss {

enum class UpscalerBackend {
    Native,
    Dlss,
    Fsr3
};

enum class FrameGenerationBackend {
    Disabled,
    Fsr3
};

struct BackendAvailability {
    bool dlssSuperResolution = false;
    bool fsr3SuperResolution = false;
    bool fsr3FrameGeneration = false;
    bool frameInputsValid = false;
    bool frameGenerationLatchedOff = false;
};

UpscalerBackend selectUpscaler(
    UpscalerBackend requested,
    const BackendAvailability& availability) noexcept;

FrameGenerationBackend selectFrameGeneration(
    FrameGenerationBackend requested,
    const BackendAvailability& availability) noexcept;

}
