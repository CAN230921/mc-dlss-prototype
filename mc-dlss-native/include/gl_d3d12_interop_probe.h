#pragma once

#include "gl_d3d12_interop_report.h"

#include <cstdint>

namespace mc_dlss {

GlD3D12InteropSnapshot runGlD3D12InteropProbe(
    std::uint32_t width,
    std::uint32_t height) noexcept;

}
