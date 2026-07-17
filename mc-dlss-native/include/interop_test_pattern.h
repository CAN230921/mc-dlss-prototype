#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace mc_dlss {

enum class InteropReadbackStatus : std::uint64_t {
    unavailable = 0,
    ready = 1,
    invalidRequest = 2,
    timeout = 3,
    missingSession = 4,
    exception = 5,
};

struct InteropReadbackFingerprint {
    bool available = false;
    std::uint64_t hash = 0;
    bool nonUniform = false;
    std::uint32_t nonBlackPixelCount = 0;
    InteropReadbackStatus status = InteropReadbackStatus::unavailable;
    std::uint64_t completedFenceValue = 0;
    std::uint64_t lastSubmittedSignal = 0;
};

std::size_t interopReadbackRangeEnd(
    std::size_t offset,
    std::size_t rowPitch,
    std::size_t logicalRowBytes,
    std::uint32_t height) noexcept;

std::vector<std::uint8_t> makeInteropTestPattern(
    std::uint32_t width,
    std::uint32_t height);

bool interopReadbackMatches(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    const std::vector<std::uint8_t>& expected,
    std::uint32_t width,
    std::uint32_t height) noexcept;

InteropReadbackFingerprint fingerprintInteropReadback(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    std::uint32_t width,
    std::uint32_t height) noexcept;

}
