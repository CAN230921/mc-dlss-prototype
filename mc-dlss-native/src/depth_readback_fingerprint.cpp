#include "depth_readback_fingerprint.h"

#include <cmath>
#include <cstring>
#include <limits>

namespace mc_dlss {

DepthReadbackFingerprint fingerprintDepthReadback(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    std::uint32_t width,
    std::uint32_t height) noexcept {
    constexpr std::uint64_t offsetBasis = 0xcbf29ce484222325ULL;
    constexpr std::uint64_t prime = 0x100000001b3ULL;
    constexpr float farThreshold = 0.9999f;
    const std::size_t logicalRowBytes = static_cast<std::size_t>(width) * 4U;
    if (actual == nullptr || width == 0 || height == 0
        || rowPitch < logicalRowBytes) {
        return {};
    }

    DepthReadbackFingerprint result{};
    result.available = true;
    result.hash = offsetBasis;
    std::uint32_t firstBits = 0;
    std::memcpy(&firstBits, actual, sizeof(firstBits));
    bool hasFinite = false;

    for (std::uint32_t y = 0; y < height; ++y) {
        const auto* row = actual + static_cast<std::size_t>(y) * rowPitch;
        for (std::uint32_t x = 0; x < width; ++x) {
            const auto* bytes = row + static_cast<std::size_t>(x) * 4U;
            std::uint32_t bits = 0;
            float value = 0.0f;
            std::memcpy(&bits, bytes, sizeof(bits));
            std::memcpy(&value, bytes, sizeof(value));
            result.nonUniform = result.nonUniform || bits != firstBits;
            for (std::size_t index = 0; index < 4U; ++index) {
                result.hash ^= bytes[index];
                result.hash *= prime;
            }
            if (!std::isfinite(value)) {
                continue;
            }
            ++result.finiteSampleCount;
            if (!hasFinite) {
                result.minimum = value;
                result.maximum = value;
                hasFinite = true;
            } else {
                result.minimum = value < result.minimum ? value : result.minimum;
                result.maximum = value > result.maximum ? value : result.maximum;
            }
            if (value < 0.0f || value > 1.0f) {
                continue;
            }
            ++result.inRangeSampleCount;
            if (value < farThreshold) {
                ++result.sceneSampleCount;
            } else {
                ++result.farSampleCount;
            }
        }
    }
    return result;
}

}
