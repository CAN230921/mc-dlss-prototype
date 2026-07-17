#include "motion_readback_fingerprint.h"

#include <cmath>
#include <cstring>

namespace mc_dlss {
namespace {

float decodeHalf(std::uint16_t bits) noexcept {
    const bool negative = (bits & 0x8000U) != 0;
    const std::uint32_t exponent = (bits >> 10U) & 0x1fU;
    const std::uint32_t fraction = bits & 0x03ffU;
    float value = 0.0f;
    if (exponent == 0) {
        value = fraction == 0 ? 0.0f : std::ldexp(static_cast<float>(fraction), -24);
    } else if (exponent == 0x1fU) {
        value = fraction == 0 ? INFINITY : NAN;
    } else {
        value = std::ldexp(static_cast<float>(fraction + 1024U),
                           static_cast<int>(exponent) - 25);
    }
    return negative ? -value : value;
}

}

MotionReadbackFingerprint fingerprintMotionReadback(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    std::uint32_t width,
    std::uint32_t height,
    float maximumAbsoluteX,
    float maximumAbsoluteY) noexcept {
    constexpr std::uint64_t offsetBasis = 0xcbf29ce484222325ULL;
    constexpr std::uint64_t prime = 0x100000001b3ULL;
    const std::size_t logicalRowBytes = static_cast<std::size_t>(width) * 4U;
    if (actual == nullptr || width == 0 || height == 0
        || rowPitch < logicalRowBytes || !std::isfinite(maximumAbsoluteX)
        || !std::isfinite(maximumAbsoluteY)
        || maximumAbsoluteX <= 0.0f || maximumAbsoluteY <= 0.0f) {
        return {};
    }

    MotionReadbackFingerprint result{};
    result.available = true;
    result.hash = offsetBasis;
    bool hasFinite = false;
    for (std::uint32_t y = 0; y < height; ++y) {
        const auto* row = actual + static_cast<std::size_t>(y) * rowPitch;
        for (std::size_t byte = 0; byte < logicalRowBytes; ++byte) {
            result.hash ^= row[byte];
            result.hash *= prime;
        }
        for (std::uint32_t x = 0; x < width; ++x) {
            const auto* pixel = row + static_cast<std::size_t>(x) * 4U;
            std::uint16_t xBits = 0;
            std::uint16_t yBits = 0;
            std::memcpy(&xBits, pixel, sizeof(xBits));
            std::memcpy(&yBits, pixel + 2, sizeof(yBits));
            const float vectorX = decodeHalf(xBits);
            const float vectorY = decodeHalf(yBits);
            if (!std::isfinite(vectorX) || !std::isfinite(vectorY)) {
                continue;
            }
            ++result.finiteVectorCount;
            if (vectorX != 0.0f || vectorY != 0.0f) {
                ++result.nonZeroVectorCount;
            }
            if (std::abs(vectorX) > maximumAbsoluteX
                || std::abs(vectorY) > maximumAbsoluteY) {
                ++result.outOfBoundsVectorCount;
            }
            const float magnitude = std::hypot(vectorX, vectorY);
            result.maximumMagnitude = magnitude > result.maximumMagnitude
                ? magnitude : result.maximumMagnitude;
            if (!hasFinite) {
                result.minimumX = result.maximumX = vectorX;
                result.minimumY = result.maximumY = vectorY;
                hasFinite = true;
            } else {
                result.minimumX = vectorX < result.minimumX ? vectorX : result.minimumX;
                result.maximumX = vectorX > result.maximumX ? vectorX : result.maximumX;
                result.minimumY = vectorY < result.minimumY ? vectorY : result.minimumY;
                result.maximumY = vectorY > result.maximumY ? vectorY : result.maximumY;
            }
        }
    }
    return result;
}

}
