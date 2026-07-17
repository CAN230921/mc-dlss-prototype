#include "d3d12_probe_report.h"

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
        case '"':
            escaped += "\\\"";
            break;
        case '\\':
            escaped += "\\\\";
            break;
        case '\n':
            escaped += "\\n";
            break;
        case '\r':
            escaped += "\\r";
            break;
        case '\t':
            escaped += "\\t";
            break;
        default:
            escaped += character;
            break;
        }
    }

    return escaped;
}

}

bool validateProbeSnapshot(const ProbeSnapshot& snapshot) noexcept {
    const bool dimensionsValid =
        snapshot.inputWidth > 0 && snapshot.inputHeight > 0 &&
        snapshot.outputWidth > 0 && snapshot.outputHeight > 0 &&
        (snapshot.inputWidth != snapshot.outputWidth ||
         snapshot.inputHeight != snapshot.outputHeight);

    return !snapshot.adapterName.empty() &&
        !snapshot.softwareAdapter &&
        snapshot.deviceAvailable &&
        snapshot.commandContextAvailable &&
        dimensionsValid &&
        snapshot.inputColor &&
        snapshot.depth &&
        snapshot.motionVectors &&
        snapshot.outputColor &&
        snapshot.commandSubmissionCompleted;
}

std::string toJson(const ProbeSnapshot& snapshot) {
    const bool success = validateProbeSnapshot(snapshot);
    std::ostringstream json;
    json << '{'
         << "\"schemaVersion\":1,"
         << "\"success\":" << jsonBoolean(success) << ','
         << "\"backend\":\"D3D12\","
         << "\"adapter\":{"
         << "\"name\":\"" << escapeJson(snapshot.adapterName) << "\","
         << "\"vendorId\":" << snapshot.vendorId << ','
         << "\"software\":" << jsonBoolean(snapshot.softwareAdapter)
         << "},"
         << "\"debugLayerAvailable\":" << jsonBoolean(snapshot.debugLayerAvailable) << ','
         << "\"deviceAvailable\":" << jsonBoolean(snapshot.deviceAvailable) << ','
         << "\"commandContextAvailable\":" << jsonBoolean(snapshot.commandContextAvailable) << ','
         << "\"inputResolution\":{"
         << "\"width\":" << snapshot.inputWidth << ','
         << "\"height\":" << snapshot.inputHeight
         << "},"
         << "\"outputResolution\":{"
         << "\"width\":" << snapshot.outputWidth << ','
         << "\"height\":" << snapshot.outputHeight
         << "},"
         << "\"resources\":{"
         << "\"inputColor\":" << jsonBoolean(snapshot.inputColor) << ','
         << "\"depth\":" << jsonBoolean(snapshot.depth) << ','
         << "\"motionVectors\":" << jsonBoolean(snapshot.motionVectors) << ','
         << "\"outputColor\":" << jsonBoolean(snapshot.outputColor)
         << "},"
         << "\"commandSubmissionCompleted\":" << jsonBoolean(snapshot.commandSubmissionCompleted) << ','
         << "\"message\":\"" << escapeJson(snapshot.message) << "\""
         << '}';
    return json.str();
}

}
