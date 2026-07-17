#include "depth_readback_fingerprint.h"

#include <cmath>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <limits>
#include <vector>

int main() {
    const float values[] = {0.25f, 1.0f, 0.5f, 1.0f};
    std::vector<std::uint8_t> padded(32, 0x7f);
    std::memcpy(padded.data(), values, 8);
    std::memcpy(padded.data() + 16, values + 2, 8);
    const auto result = mc_dlss::fingerprintDepthReadback(
        padded.data(), 16, 2, 2);
    if (!result.available || !result.nonUniform || result.finiteSampleCount != 4
        || result.inRangeSampleCount != 4 || result.sceneSampleCount != 2
        || result.farSampleCount != 2 || result.minimum != 0.25f
        || result.maximum != 1.0f) {
        std::cerr << "padded depth fingerprint mismatch\n";
        return 1;
    }

    const float invalidValues[] = {
        std::numeric_limits<float>::quiet_NaN(),
        std::numeric_limits<float>::infinity(),
        -0.1f,
        1.1f,
    };
    const auto invalid = mc_dlss::fingerprintDepthReadback(
        reinterpret_cast<const std::uint8_t*>(invalidValues), 16, 4, 1);
    if (!invalid.available || invalid.finiteSampleCount != 2
        || invalid.inRangeSampleCount != 0) {
        std::cerr << "invalid depth samples were accepted\n";
        return 1;
    }

    if (mc_dlss::fingerprintDepthReadback(nullptr, 16, 4, 1).available
        || mc_dlss::fingerprintDepthReadback(padded.data(), 7, 2, 2).available) {
        std::cerr << "invalid depth layout was accepted\n";
        return 1;
    }

    std::cout << "depth_readback_fingerprint_test passed\n";
    return 0;
}
