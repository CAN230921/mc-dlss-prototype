#include "streamline_probe_report.h"

#include <cassert>
#include <string>

namespace {

mc_dlss::StreamlineProbeSnapshot completeSnapshot() {
    mc_dlss::StreamlineProbeSnapshot snapshot{};
    snapshot.streamlineVersion = "2.12.0";
    snapshot.projectId = "7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e";
    snapshot.adapterName = "NVIDIA GeForce RTX 4070 Laptop GPU";
    snapshot.optimalRenderWidth = 1280;
    snapshot.optimalRenderHeight = 720;
    snapshot.outputWidth = 1920;
    snapshot.outputHeight = 1080;
    snapshot.streamlineInitialized = true;
    snapshot.d3dDeviceAccepted = true;
    snapshot.dlssSupported = true;
    snapshot.optionsAccepted = true;
    snapshot.constantsAccepted = true;
    snapshot.resourcesTagged = true;
    snapshot.evaluationCalled = true;
    snapshot.streamlineDlssEvaluationSucceeded = true;
    snapshot.commandSubmissionCompleted = true;
    snapshot.resourcesFreed = true;
    snapshot.streamlineShutdownSucceeded = true;
    snapshot.lastResult = "eOk";
    snapshot.message = "Streamline DLSS evaluation completed.";
    return snapshot;
}

}

int main() {
    const auto complete = completeSnapshot();
    assert(mc_dlss::validateStreamlineProbeSnapshot(complete));

    const std::string successJson = mc_dlss::toJson(complete);
    assert(successJson.find("\"schemaVersion\":1") != std::string::npos);
    assert(successJson.find("\"success\":true") != std::string::npos);
    assert(successJson.find(
        "\"projectId\":\"7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e\"") !=
        std::string::npos);
    assert(successJson.find(
        "\"streamlineDlssEvaluationSucceeded\":true") != std::string::npos);
    assert(successJson.find("\"resourcesFreed\":true") != std::string::npos);

    auto missingTags = complete;
    missingTags.resourcesTagged = false;
    missingTags.lastResult = "eErrorMissingInputParameter";
    missingTags.message = "Missing \"resource tags\".\nEvaluation skipped.";
    assert(!mc_dlss::validateStreamlineProbeSnapshot(missingTags));

    const std::string failureJson = mc_dlss::toJson(missingTags);
    assert(failureJson.find("\"success\":false") != std::string::npos);
    assert(failureJson.find("Missing \\\"resource tags\\\".\\nEvaluation skipped.") !=
        std::string::npos);

    auto sameResolution = complete;
    sameResolution.outputWidth = sameResolution.optimalRenderWidth;
    sameResolution.outputHeight = sameResolution.optimalRenderHeight;
    assert(!mc_dlss::validateStreamlineProbeSnapshot(sameResolution));

    auto wrongIdentity = complete;
    wrongIdentity.projectId = "sample-id";
    assert(!mc_dlss::validateStreamlineProbeSnapshot(wrongIdentity));

    return 0;
}
