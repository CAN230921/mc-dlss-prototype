#include "interop_test_pattern.h"

#include <cstdint>
#include <iostream>
#include <vector>

int main() {
    const std::size_t paddedRangeEnd = mc_dlss::interopReadbackRangeEnd(
        0, 3584, 3416, 480);
    if (paddedRangeEnd != 3584U * 479U + 3416U ||
        paddedRangeEnd == 3584U * 480U) {
        std::cerr << "padded readback range includes trailing row padding\n";
        return 1;
    }

    const auto expected = mc_dlss::makeInteropTestPattern(2, 2);
    const std::vector<std::uint8_t> required{
        0, 0, 0, 255,
        3, 1, 1, 255,
        1, 5, 1, 255,
        4, 6, 0, 255,
    };
    if (expected != required) {
        std::cerr << "deterministic pattern mismatch\n";
        return 1;
    }

    std::vector<std::uint8_t> padded(24, 0x7f);
    for (std::size_t row = 0; row < 2; ++row) {
        for (std::size_t byte = 0; byte < 8; ++byte) {
            padded[row * 12 + byte] = expected[row * 8 + byte];
        }
    }
    if (!mc_dlss::interopReadbackMatches(
            padded.data(), 12, expected, 2, 2)) {
        std::cerr << "padded readback should match\n";
        return 1;
    }

    const auto fingerprint = mc_dlss::fingerprintInteropReadback(
        padded.data(), 12, 2, 2);
    if (!fingerprint.available || fingerprint.hash != 0x23c04fe567ee71cdULL ||
        !fingerprint.nonUniform || fingerprint.nonBlackPixelCount != 3) {
        std::cerr << "padded fingerprint mismatch\n";
        return 1;
    }

    const std::vector<std::uint8_t> uniformBlack{
        0, 0, 0, 255, 0, 0, 0, 255,
        0, 0, 0, 255, 0, 0, 0, 255,
    };
    const auto blackFingerprint = mc_dlss::fingerprintInteropReadback(
        uniformBlack.data(), 8, 2, 2);
    if (!blackFingerprint.available || blackFingerprint.nonUniform ||
        blackFingerprint.nonBlackPixelCount != 0) {
        std::cerr << "uniform black fingerprint mismatch\n";
        return 1;
    }

    padded[12 + 3] ^= 1;
    if (mc_dlss::interopReadbackMatches(
            padded.data(), 12, expected, 2, 2)) {
        std::cerr << "one-byte mismatch was accepted\n";
        return 1;
    }
    if (mc_dlss::fingerprintInteropReadback(
            padded.data(), 12, 2, 2).hash == fingerprint.hash) {
        std::cerr << "one-byte fingerprint change was missed\n";
        return 1;
    }

    std::cout << "interop_test_pattern_test passed\n";
    return 0;
}
