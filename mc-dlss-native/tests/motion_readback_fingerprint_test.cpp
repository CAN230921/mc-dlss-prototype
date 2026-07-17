#include "motion_readback_fingerprint.h"

#include <cstdint>
#include <cstring>
#include <iostream>
#include <vector>

namespace {

void put(std::uint8_t* target, std::uint16_t x, std::uint16_t y) {
    std::memcpy(target, &x, sizeof(x));
    std::memcpy(target + 2, &y, sizeof(y));
}

}

int main() {
    std::vector<std::uint8_t> padded(32, 0x7f);
    put(padded.data(), 0x0000, 0x0000);       // 0, 0
    put(padded.data() + 4, 0x3c00, 0xc000);   // 1, -2
    put(padded.data() + 16, 0x4200, 0x0000);  // 3, 0
    put(padded.data() + 20, 0x7c00, 0x7e00);  // inf, nan

    const auto result = mc_dlss::fingerprintMotionReadback(
        padded.data(), 16, 2, 2, 4.0f, 3.0f);
    if (!result.available || result.finiteVectorCount != 3
        || result.nonZeroVectorCount != 2 || result.outOfBoundsVectorCount != 0
        || result.minimumX != 0.0f || result.maximumX != 3.0f
        || result.minimumY != -2.0f || result.maximumY != 0.0f
        || result.maximumMagnitude < 2.999f || result.maximumMagnitude > 3.001f) {
        std::cerr << "motion fingerprint statistics mismatch\n";
        return 1;
    }

    padded[8] ^= 0xff;
    const auto paddingChanged = mc_dlss::fingerprintMotionReadback(
        padded.data(), 16, 2, 2, 4.0f, 3.0f);
    if (paddingChanged.hash != result.hash) {
        std::cerr << "row padding affected motion hash\n";
        return 1;
    }

    put(padded.data(), 0x4500, 0x0000); // 5, 0 exceeds width bound.
    const auto bounded = mc_dlss::fingerprintMotionReadback(
        padded.data(), 16, 2, 2, 4.0f, 3.0f);
    if (bounded.outOfBoundsVectorCount != 1) {
        std::cerr << "out-of-bounds motion was accepted\n";
        return 1;
    }

    if (mc_dlss::fingerprintMotionReadback(nullptr, 16, 2, 2, 4, 3).available
        || mc_dlss::fingerprintMotionReadback(padded.data(), 7, 2, 2, 4, 3).available) {
        std::cerr << "invalid motion layout was accepted\n";
        return 1;
    }

    std::cout << "motion_readback_fingerprint_test passed\n";
    return 0;
}
