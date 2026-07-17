#pragma once

#include <cstddef>
#include <cstdint>

namespace mc_dlss {

struct DepthReadbackFingerprint {
    bool available = false;
    std::uint64_t hash = 0;
    bool nonUniform = false;
    std::uint32_t finiteSampleCount = 0;
    std::uint32_t inRangeSampleCount = 0;
    std::uint32_t sceneSampleCount = 0;
    std::uint32_t farSampleCount = 0;
    float minimum = 0.0f;
    float maximum = 0.0f;
};

DepthReadbackFingerprint fingerprintDepthReadback(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    std::uint32_t width,
    std::uint32_t height) noexcept;

}
