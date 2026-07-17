#include "d3d12_probe_report.h"

#include <cassert>
#include <string>

namespace {
mc_dlss::ProbeSnapshot completeSnapshot() {
    mc_dlss::ProbeSnapshot snapshot{};
    snapshot.adapterName = "NVIDIA GeForce RTX 4070 Laptop GPU";
    snapshot.vendorId = 0x10de;
    snapshot.softwareAdapter = false;
    snapshot.debugLayerAvailable = true;
    snapshot.deviceAvailable = true;
    snapshot.commandContextAvailable = true;
    snapshot.inputWidth = 1280;
    snapshot.inputHeight = 720;
    snapshot.outputWidth = 1920;
    snapshot.outputHeight = 1080;
    snapshot.inputColor = true;
    snapshot.depth = true;
    snapshot.motionVectors = true;
    snapshot.outputColor = true;
    snapshot.commandSubmissionCompleted = true;
    snapshot.message = "D3D12 DLSS resource contract is available.";
    return snapshot;
}
}

int main() {
    const auto complete = completeSnapshot();
    assert(mc_dlss::validateProbeSnapshot(complete));

    const std::string successJson = mc_dlss::toJson(complete);
    assert(successJson.find("\"schemaVersion\":1") != std::string::npos);
    assert(successJson.find("\"success\":true") != std::string::npos);
    assert(successJson.find("\"backend\":\"D3D12\"") != std::string::npos);
    assert(successJson.find("\"inputColor\":true") != std::string::npos);
    assert(successJson.find("\"depth\":true") != std::string::npos);
    assert(successJson.find("\"motionVectors\":true") != std::string::npos);
    assert(successJson.find("\"outputColor\":true") != std::string::npos);

    auto incomplete = complete;
    incomplete.motionVectors = false;
    incomplete.message = "Missing \"motion vectors\".\nRetry.";
    assert(!mc_dlss::validateProbeSnapshot(incomplete));

    const std::string failureJson = mc_dlss::toJson(incomplete);
    assert(failureJson.find("\"success\":false") != std::string::npos);
    assert(failureJson.find("Missing \\\"motion vectors\\\".\\nRetry.") != std::string::npos);

    auto sameResolution = complete;
    sameResolution.outputWidth = sameResolution.inputWidth;
    sameResolution.outputHeight = sameResolution.inputHeight;
    assert(!mc_dlss::validateProbeSnapshot(sameResolution));

    auto software = complete;
    software.softwareAdapter = true;
    assert(!mc_dlss::validateProbeSnapshot(software));

    return 0;
}
