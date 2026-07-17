#pragma once

#include "streamline_probe_report.h"

#include <string>

namespace mc_dlss {

StreamlineProbeSnapshot runStreamlineDlssProbe(
    const std::wstring& pluginPath,
    const std::wstring& logPath) noexcept;

}
