#include "streamline_probe_report.h"

#include <sstream>

namespace mc_dlss {
namespace {

const char* jsonBoolean(bool value) noexcept {
    return value ? "true" : "false";
}

std::string escapeJson(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size());
    for (const char character : value) {
        switch (character) {
        case '"': escaped += "\\\""; break;
        case '\\': escaped += "\\\\"; break;
        case '\n': escaped += "\\n"; break;
        case '\r': escaped += "\\r"; break;
        case '\t': escaped += "\\t"; break;
        default: escaped += character; break;
        }
    }
    return escaped;
}

}

bool validateStreamlineProbeSnapshot(
    const StreamlineProbeSnapshot& snapshot) noexcept {
    const bool dimensionsValid =
        snapshot.optimalRenderWidth > 0 &&
        snapshot.optimalRenderHeight > 0 &&
        snapshot.outputWidth > 0 &&
        snapshot.outputHeight > 0 &&
        (snapshot.optimalRenderWidth != snapshot.outputWidth ||
         snapshot.optimalRenderHeight != snapshot.outputHeight);

    return snapshot.streamlineVersion == "2.12.0" &&
        snapshot.projectId == kStreamlineProbeProjectId &&
        !snapshot.adapterName.empty() &&
        dimensionsValid &&
        snapshot.streamlineInitialized &&
        snapshot.d3dDeviceAccepted &&
        snapshot.dlssSupported &&
        snapshot.optionsAccepted &&
        snapshot.constantsAccepted &&
        snapshot.resourcesTagged &&
        snapshot.evaluationCalled &&
        snapshot.streamlineDlssEvaluationSucceeded &&
        snapshot.commandSubmissionCompleted &&
        snapshot.resourcesFreed &&
        snapshot.streamlineShutdownSucceeded &&
        snapshot.lastResult == "eOk";
}

std::string toJson(const StreamlineProbeSnapshot& snapshot) {
    std::ostringstream json;
    json << '{'
         << "\"schemaVersion\":1,"
         << "\"success\":" << jsonBoolean(validateStreamlineProbeSnapshot(snapshot)) << ','
         << "\"streamlineVersion\":\"" << escapeJson(snapshot.streamlineVersion) << "\","
         << "\"projectId\":\"" << escapeJson(snapshot.projectId) << "\","
         << "\"adapterName\":\"" << escapeJson(snapshot.adapterName) << "\","
         << "\"optimalRenderResolution\":{"
         << "\"width\":" << snapshot.optimalRenderWidth << ','
         << "\"height\":" << snapshot.optimalRenderHeight << "},"
         << "\"outputResolution\":{"
         << "\"width\":" << snapshot.outputWidth << ','
         << "\"height\":" << snapshot.outputHeight << "},"
         << "\"streamlineInitialized\":" << jsonBoolean(snapshot.streamlineInitialized) << ','
         << "\"d3dDeviceAccepted\":" << jsonBoolean(snapshot.d3dDeviceAccepted) << ','
         << "\"dlssSupported\":" << jsonBoolean(snapshot.dlssSupported) << ','
         << "\"optionsAccepted\":" << jsonBoolean(snapshot.optionsAccepted) << ','
         << "\"constantsAccepted\":" << jsonBoolean(snapshot.constantsAccepted) << ','
         << "\"resourcesTagged\":" << jsonBoolean(snapshot.resourcesTagged) << ','
         << "\"evaluationCalled\":" << jsonBoolean(snapshot.evaluationCalled) << ','
         << "\"streamlineDlssEvaluationSucceeded\":"
         << jsonBoolean(snapshot.streamlineDlssEvaluationSucceeded) << ','
         << "\"commandSubmissionCompleted\":"
         << jsonBoolean(snapshot.commandSubmissionCompleted) << ','
         << "\"resourcesFreed\":" << jsonBoolean(snapshot.resourcesFreed) << ','
         << "\"streamlineShutdownSucceeded\":"
         << jsonBoolean(snapshot.streamlineShutdownSucceeded) << ','
         << "\"lastResult\":\"" << escapeJson(snapshot.lastResult) << "\","
         << "\"message\":\"" << escapeJson(snapshot.message) << "\""
         << '}';
    return json.str();
}

}
