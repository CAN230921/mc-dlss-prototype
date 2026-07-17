#pragma once

#include <cstddef>
#include <cstdint>

namespace mc_dlss {

struct MotionReadbackFingerprint {
    bool available = false;
    std::uint64_t hash = 0;
    std::uint32_t finiteVectorCount = 0;
    std::uint32_t nonZeroVectorCount = 0;
    std::uint32_t outOfBoundsVectorCount = 0;
    float minimumX = 0.0f;
    float maximumX = 0.0f;
    float minimumY = 0.0f;
    float maximumY = 0.0f;
    float maximumMagnitude = 0.0f;
};

MotionReadbackFingerprint fingerprintMotionReadback(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    std::uint32_t width,
    std::uint32_t height,
    float maximumAbsoluteX,
    float maximumAbsoluteY) noexcept;

}
