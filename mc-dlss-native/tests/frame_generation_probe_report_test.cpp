#include "frame_generation_probe_report.h"

#include <cassert>
#include <string>

int main() {
    mc_dlss::FrameGenerationProbeSnapshot snapshot{};
    snapshot.adapterName = "NVIDIA GeForce RTX 4070 Laptop GPU";
    snapshot.streamlineInitialized = true;
    snapshot.d3dDeviceAccepted = true;
    snapshot.dlssGSupported = true;
    snapshot.reflexSupported = true;
    snapshot.requirementsAvailable = true;
    snapshot.d3d12Supported = true;
    snapshot.hardwareSchedulingRequired = true;
    snapshot.maxFramesToGenerate = 1;
    snapshot.swapChainCreated = true;
    snapshot.reflexEnabled = true;
    snapshot.optionsAccepted = true;
    snapshot.applicationFramesPresented = 8;
    snapshot.framesActuallyPresented = 16;
    snapshot.streamlineShutdownSucceeded = true;
    snapshot.lastResult = "eOk";
    snapshot.message = "DLSS frame generation capability is available.";

    assert(mc_dlss::validateFrameGenerationProbeSnapshot(snapshot));
    const std::string json = mc_dlss::toJson(snapshot);
    assert(json.find("\"success\":true") != std::string::npos);
    assert(json.find("\"maxFramesToGenerate\":1") != std::string::npos);
    assert(json.find("\"reflexSupported\":true") != std::string::npos);
    assert(json.find("\"streamlineShutdownSucceeded\":true") != std::string::npos);
    assert(json.find("\"framesActuallyPresented\":16") != std::string::npos);

    snapshot.maxFramesToGenerate = 0;
    assert(!mc_dlss::validateFrameGenerationProbeSnapshot(snapshot));
    return 0;
}
