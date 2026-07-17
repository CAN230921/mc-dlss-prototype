#pragma once

#include <array>
#include <cstdint>
#include <string>

namespace mc_dlss {

std::string formatAdapterLuid(const std::array<std::uint8_t, 8>& bytes);
std::string queryHighPerformanceD3D12AdapterLuid();

}
