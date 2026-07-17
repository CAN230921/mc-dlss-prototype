#pragma once

#include "frame_generation_probe_report.h"

#include <string>

namespace mc_dlss {

FrameGenerationProbeSnapshot runFrameGenerationProbe(
    const std::wstring& pluginPath,
    const std::wstring& logPath) noexcept;

}
