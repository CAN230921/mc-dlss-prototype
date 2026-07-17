#pragma once

#include <string>

namespace mc_dlss {

struct GlD3D12InteropSnapshot {
    std::string adapterName;
    std::string openGlVendor;
    std::string openGlRenderer;
    std::string openGlVersion;
    bool memoryObjectExtension = false;
    bool memoryObjectWin32Extension = false;
    bool semaphoreExtension = false;
    bool semaphoreWin32Extension = false;
    bool entryPointsLoaded = false;
    bool sharedTextureCreated = false;
    bool sharedFenceCreated = false;
    bool memoryImported = false;
    bool semaphoreImported = false;
    bool openGlWriteSubmitted = false;
    bool d3d12WaitCompleted = false;
    bool readbackMatched = false;
    bool d3d12SignalCompleted = false;
    bool openGlWaitCompleted = false;
    std::string message;
};

bool validateGlD3D12InteropSnapshot(
    const GlD3D12InteropSnapshot& snapshot) noexcept;
std::string toJson(const GlD3D12InteropSnapshot& snapshot);

}
