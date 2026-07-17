#pragma once

#include <cstdint>
#include <string>

namespace mc_dlss {

struct FrameGenerationProbeSnapshot {
    std::string adapterName;
    bool streamlineInitialized = false;
    bool d3dDeviceAccepted = false;
    bool dlssGSupported = false;
    bool reflexSupported = false;
    bool requirementsAvailable = false;
    bool d3d12Supported = false;
    bool hardwareSchedulingRequired = false;
    std::uint32_t maxFramesToGenerate = 0;
    bool swapChainCreated = false;
    bool reflexEnabled = false;
    bool optionsAccepted = false;
    std::uint32_t applicationFramesPresented = 0;
    std::uint32_t framesActuallyPresented = 0;
    bool isolatedProcessExit = false;
    bool streamlineShutdownSucceeded = false;
    std::string lastResult;
    std::string message;
};

bool validateFrameGenerationProbeSnapshot(
    const FrameGenerationProbeSnapshot& snapshot) noexcept;
std::string toJson(const FrameGenerationProbeSnapshot& snapshot);

}
