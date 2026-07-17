#pragma once

#include "d3d12_probe_report.h"

#include <cstdint>

namespace mc_dlss {

ProbeSnapshot runD3D12ResourceProbe(
    std::uint32_t inputWidth,
    std::uint32_t inputHeight,
    std::uint32_t outputWidth,
    std::uint32_t outputHeight) noexcept;

}
