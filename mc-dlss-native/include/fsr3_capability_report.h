#pragma once

#include <cstdint>
#include <string>

namespace mc_dlss {

enum class Fsr3FeatureState {
    Unavailable,
    ProviderAvailable,
    ContextReady,
    ContextFailed
};

struct Fsr3FeatureCapability {
    Fsr3FeatureState state = Fsr3FeatureState::Unavailable;
    std::string providerName;
    std::uint32_t returnCode = 0;
    std::uint64_t memoryMiB = 0;
};

struct Fsr3CapabilitySnapshot {
    bool loaderAvailable = false;
    Fsr3FeatureCapability upscaler;
    Fsr3FeatureCapability frameGeneration;
    std::string message;
};

std::string summarizeFsr3Capability(const Fsr3CapabilitySnapshot& snapshot);

}
