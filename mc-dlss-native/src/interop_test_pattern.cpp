#include "interop_test_pattern.h"

#include <cstring>
#include <limits>

namespace mc_dlss {

std::size_t interopReadbackRangeEnd(
    std::size_t offset,
    std::size_t rowPitch,
    std::size_t logicalRowBytes,
    std::uint32_t height) noexcept {
    if (height == 0 || logicalRowBytes == 0 || rowPitch < logicalRowBytes ||
        offset > std::numeric_limits<std::size_t>::max() - logicalRowBytes) {
        return 0;
    }
    const std::size_t base = offset + logicalRowBytes;
    const std::size_t precedingRows = static_cast<std::size_t>(height - 1U);
    if (precedingRows >
        (std::numeric_limits<std::size_t>::max() - base) / rowPitch) {
        return 0;
    }
    return base + precedingRows * rowPitch;
}

std::vector<std::uint8_t> makeInteropTestPattern(
    std::uint32_t width,
    std::uint32_t height) {
    if (width == 0 || height == 0 ||
        width > std::numeric_limits<std::size_t>::max() / height / 4U) {
        return {};
    }

    std::vector<std::uint8_t> pixels(
        static_cast<std::size_t>(width) * height * 4U);
    for (std::uint32_t y = 0; y < height; ++y) {
        for (std::uint32_t x = 0; x < width; ++x) {
            const std::size_t offset =
                (static_cast<std::size_t>(y) * width + x) * 4U;
            pixels[offset] = static_cast<std::uint8_t>((x * 3U + y) & 0xffU);
            pixels[offset + 1] = static_cast<std::uint8_t>((x + y * 5U) & 0xffU);
            pixels[offset + 2] = static_cast<std::uint8_t>((x ^ y) & 0xffU);
            pixels[offset + 3] = 0xffU;
        }
    }
    return pixels;
}

bool interopReadbackMatches(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    const std::vector<std::uint8_t>& expected,
    std::uint32_t width,
    std::uint32_t height) noexcept {
    const std::size_t logicalRowBytes = static_cast<std::size_t>(width) * 4U;
    const std::size_t expectedSize = logicalRowBytes * height;
    if (actual == nullptr || width == 0 || height == 0 ||
        rowPitch < logicalRowBytes || expected.size() != expectedSize) {
        return false;
    }

    for (std::uint32_t y = 0; y < height; ++y) {
        const auto* actualRow = actual + static_cast<std::size_t>(y) * rowPitch;
        const auto* expectedRow = expected.data() +
            static_cast<std::size_t>(y) * logicalRowBytes;
        if (std::memcmp(actualRow, expectedRow, logicalRowBytes) != 0) {
            return false;
        }
    }
    return true;
}

InteropReadbackFingerprint fingerprintInteropReadback(
    const std::uint8_t* actual,
    std::size_t rowPitch,
    std::uint32_t width,
    std::uint32_t height) noexcept {
    constexpr std::uint64_t offsetBasis = 0xcbf29ce484222325ULL;
    constexpr std::uint64_t prime = 0x100000001b3ULL;
    const std::size_t logicalRowBytes = static_cast<std::size_t>(width) * 4U;
    if (actual == nullptr || width == 0 || height == 0 ||
        rowPitch < logicalRowBytes) {
        return {};
    }

    InteropReadbackFingerprint result{};
    result.available = true;
    result.status = InteropReadbackStatus::ready;
    result.hash = offsetBasis;
    const std::uint8_t* firstPixel = actual;
    for (std::uint32_t y = 0; y < height; ++y) {
        const auto* row = actual + static_cast<std::size_t>(y) * rowPitch;
        for (std::uint32_t x = 0; x < width; ++x) {
            const auto* pixel = row + static_cast<std::size_t>(x) * 4U;
            result.nonUniform = result.nonUniform ||
                std::memcmp(pixel, firstPixel, 4U) != 0;
            if (pixel[0] != 0 || pixel[1] != 0 || pixel[2] != 0) {
                ++result.nonBlackPixelCount;
            }
            for (std::size_t channel = 0; channel < 4U; ++channel) {
                result.hash ^= pixel[channel];
                result.hash *= prime;
            }
        }
    }
    return result;
}

}
