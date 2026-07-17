#pragma once

#include <cstdint>
#include <string>

namespace mc_dlss {

struct DxgiWindowPresentProbeSnapshot {
    std::string adapterName;
    std::string openGlVendor;
    std::string openGlRenderer;
    bool sameWindowHandle = false;
    bool openGlContextCurrent = false;
    bool openGlSwapCompleted = false;
    bool dxgiSwapchainCreated = false;
    bool firstBackBufferMatched = false;
    bool firstPresentSucceeded = false;
    bool resizeSucceeded = false;
    bool secondBackBufferMatched = false;
    bool secondPresentSucceeded = false;
    bool openGlContextStillCurrent = false;
    std::int64_t deviceRemovedReason = 0;
    std::string message;
};

bool validateDxgiWindowPresentProbeSnapshot(
    const DxgiWindowPresentProbeSnapshot& snapshot) noexcept;
std::string toJson(const DxgiWindowPresentProbeSnapshot& snapshot);

}
