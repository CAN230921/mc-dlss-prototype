#include "dxgi_window_present_probe_report.h"

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

bool validateDxgiWindowPresentProbeSnapshot(
    const DxgiWindowPresentProbeSnapshot& snapshot) noexcept {
    return !snapshot.adapterName.empty() &&
        !snapshot.openGlVendor.empty() &&
        !snapshot.openGlRenderer.empty() &&
        snapshot.sameWindowHandle &&
        snapshot.openGlContextCurrent &&
        snapshot.openGlSwapCompleted &&
        snapshot.dxgiSwapchainCreated &&
        snapshot.firstBackBufferMatched &&
        snapshot.firstPresentSucceeded &&
        snapshot.resizeSucceeded &&
        snapshot.secondBackBufferMatched &&
        snapshot.secondPresentSucceeded &&
        snapshot.openGlContextStillCurrent &&
        snapshot.deviceRemovedReason == 0;
}

std::string toJson(const DxgiWindowPresentProbeSnapshot& snapshot) {
    std::ostringstream json;
    json << '{'
         << "\"schemaVersion\":1,"
         << "\"success\":"
         << jsonBoolean(validateDxgiWindowPresentProbeSnapshot(snapshot)) << ','
         << "\"adapterName\":\"" << escapeJson(snapshot.adapterName) << "\","
         << "\"openGlVendor\":\"" << escapeJson(snapshot.openGlVendor) << "\","
         << "\"openGlRenderer\":\"" << escapeJson(snapshot.openGlRenderer) << "\","
         << "\"sameWindowHandle\":" << jsonBoolean(snapshot.sameWindowHandle) << ','
         << "\"openGlContextCurrent\":"
         << jsonBoolean(snapshot.openGlContextCurrent) << ','
         << "\"openGlSwapCompleted\":"
         << jsonBoolean(snapshot.openGlSwapCompleted) << ','
         << "\"dxgiSwapchainCreated\":"
         << jsonBoolean(snapshot.dxgiSwapchainCreated) << ','
         << "\"firstBackBufferMatched\":"
         << jsonBoolean(snapshot.firstBackBufferMatched) << ','
         << "\"firstPresentSucceeded\":"
         << jsonBoolean(snapshot.firstPresentSucceeded) << ','
         << "\"resizeSucceeded\":" << jsonBoolean(snapshot.resizeSucceeded) << ','
         << "\"secondBackBufferMatched\":"
         << jsonBoolean(snapshot.secondBackBufferMatched) << ','
         << "\"secondPresentSucceeded\":"
         << jsonBoolean(snapshot.secondPresentSucceeded) << ','
         << "\"openGlContextStillCurrent\":"
         << jsonBoolean(snapshot.openGlContextStillCurrent) << ','
         << "\"deviceRemovedReason\":" << snapshot.deviceRemovedReason << ','
         << "\"message\":\"" << escapeJson(snapshot.message) << "\""
         << '}';
    return json.str();
}

}
