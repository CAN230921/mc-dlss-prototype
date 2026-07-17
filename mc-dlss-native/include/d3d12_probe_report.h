#pragma once

#include <cstdint>
#include <string>

namespace mc_dlss {

struct ProbeSnapshot {
    std::string adapterName;
    std::uint32_t vendorId = 0;
    bool softwareAdapter = false;
    bool debugLayerAvailable = false;
    bool deviceAvailable = false;
    bool commandContextAvailable = false;
    std::uint32_t inputWidth = 0;
    std::uint32_t inputHeight = 0;
    std::uint32_t outputWidth = 0;
    std::uint32_t outputHeight = 0;
    bool inputColor = false;
    bool depth = false;
    bool motionVectors = false;
    bool outputColor = false;
    bool commandSubmissionCompleted = false;
    std::string message;
};

bool validateProbeSnapshot(const ProbeSnapshot& snapshot) noexcept;
std::string toJson(const ProbeSnapshot& snapshot);

}
