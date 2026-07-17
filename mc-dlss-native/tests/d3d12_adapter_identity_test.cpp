#include "d3d12_adapter_identity.h"

#include <array>
#include <cassert>
#include <cstdint>

int main() {
    const std::array<std::uint8_t, 8> bytes{
        0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef};
    assert(mc_dlss::formatAdapterLuid(bytes) == "0123456789abcdef");

    const std::array<std::uint8_t, 8> zeros{};
    assert(mc_dlss::formatAdapterLuid(zeros) == "0000000000000000");
    return 0;
}
