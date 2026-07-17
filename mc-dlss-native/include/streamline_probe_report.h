#pragma once

#include <cstdint>
#include <string>

namespace mc_dlss {

inline constexpr const char* kStreamlineProbeProjectId =
    "7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e";
inline constexpr const char* kStreamlineProbeEngineVersion =
    "mc-dlss-prototype/0.1.0";

struct StreamlineProbeSnapshot {
    std::string streamlineVersion;
    std::string projectId;
    std::string adapterName;
    std::uint32_t optimalRenderWidth = 0;
    std::uint32_t optimalRenderHeight = 0;
    std::uint32_t outputWidth = 0;
    std::uint32_t outputHeight = 0;
    bool streamlineInitialized = false;
    bool d3dDeviceAccepted = false;
    bool dlssSupported = false;
    bool optionsAccepted = false;
    bool constantsAccepted = false;
    bool resourcesTagged = false;
    bool evaluationCalled = false;
    bool streamlineDlssEvaluationSucceeded = false;
    bool commandSubmissionCompleted = false;
    bool resourcesFreed = false;
    bool streamlineShutdownSucceeded = false;
    std::string lastResult;
    std::string message;
};

bool validateStreamlineProbeSnapshot(
    const StreamlineProbeSnapshot& snapshot) noexcept;
std::string toJson(const StreamlineProbeSnapshot& snapshot);

}
