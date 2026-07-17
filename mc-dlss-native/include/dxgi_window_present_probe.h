#pragma once

#include "dxgi_window_present_probe_report.h"

#include <cstdint>

namespace mc_dlss {

DxgiWindowPresentProbeSnapshot runDxgiWindowPresentProbe(
    std::uint32_t width,
    std::uint32_t height,
    std::uint32_t resizedWidth,
    std::uint32_t resizedHeight) noexcept;

}
