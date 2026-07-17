#include "frame_generation_probe_report.h"

#include <sstream>

namespace mc_dlss {
namespace {

std::string escaped(const std::string& value) {
    std::ostringstream output;
    for (const char character : value) {
        if (character == '"' || character == '\\') output << '\\';
        if (character == '\n') {
            output << "\\n";
        } else {
            output << character;
        }
    }
    return output.str();
}

}

bool validateFrameGenerationProbeSnapshot(
    const FrameGenerationProbeSnapshot& snapshot) noexcept {
    return snapshot.streamlineInitialized && snapshot.d3dDeviceAccepted &&
        snapshot.dlssGSupported && snapshot.reflexSupported &&
        snapshot.requirementsAvailable && snapshot.d3d12Supported &&
        snapshot.maxFramesToGenerate > 0 && snapshot.swapChainCreated &&
        snapshot.reflexEnabled && snapshot.optionsAccepted &&
        snapshot.applicationFramesPresented > 0 &&
        snapshot.framesActuallyPresented > snapshot.applicationFramesPresented &&
        snapshot.streamlineShutdownSucceeded && snapshot.lastResult == "eOk";
}

std::string toJson(const FrameGenerationProbeSnapshot& snapshot) {
    std::ostringstream output;
    output << "{\"schemaVersion\":1"
           << ",\"success\":"
           << (validateFrameGenerationProbeSnapshot(snapshot) ? "true" : "false")
           << ",\"adapterName\":\"" << escaped(snapshot.adapterName) << '"'
           << ",\"streamlineInitialized\":"
           << (snapshot.streamlineInitialized ? "true" : "false")
           << ",\"d3dDeviceAccepted\":"
           << (snapshot.d3dDeviceAccepted ? "true" : "false")
           << ",\"dlssGSupported\":"
           << (snapshot.dlssGSupported ? "true" : "false")
           << ",\"reflexSupported\":"
           << (snapshot.reflexSupported ? "true" : "false")
           << ",\"requirementsAvailable\":"
           << (snapshot.requirementsAvailable ? "true" : "false")
           << ",\"d3d12Supported\":"
           << (snapshot.d3d12Supported ? "true" : "false")
           << ",\"hardwareSchedulingRequired\":"
           << (snapshot.hardwareSchedulingRequired ? "true" : "false")
           << ",\"maxFramesToGenerate\":" << snapshot.maxFramesToGenerate
           << ",\"swapChainCreated\":"
           << (snapshot.swapChainCreated ? "true" : "false")
           << ",\"reflexEnabled\":"
           << (snapshot.reflexEnabled ? "true" : "false")
           << ",\"optionsAccepted\":"
           << (snapshot.optionsAccepted ? "true" : "false")
           << ",\"applicationFramesPresented\":"
           << snapshot.applicationFramesPresented
           << ",\"framesActuallyPresented\":"
           << snapshot.framesActuallyPresented
           << ",\"isolatedProcessExit\":"
           << (snapshot.isolatedProcessExit ? "true" : "false")
           << ",\"streamlineShutdownSucceeded\":"
           << (snapshot.streamlineShutdownSucceeded ? "true" : "false")
           << ",\"lastResult\":\"" << escaped(snapshot.lastResult) << '"'
           << ",\"message\":\"" << escaped(snapshot.message) << "\"}";
    return output.str();
}

}
