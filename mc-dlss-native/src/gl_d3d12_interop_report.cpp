#include "gl_d3d12_interop_report.h"

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

bool validateGlD3D12InteropSnapshot(
    const GlD3D12InteropSnapshot& snapshot) noexcept {
    return !snapshot.adapterName.empty() &&
        !snapshot.openGlVendor.empty() &&
        !snapshot.openGlRenderer.empty() &&
        !snapshot.openGlVersion.empty() &&
        snapshot.memoryObjectExtension &&
        snapshot.memoryObjectWin32Extension &&
        snapshot.semaphoreExtension &&
        snapshot.semaphoreWin32Extension &&
        snapshot.entryPointsLoaded &&
        snapshot.sharedTextureCreated &&
        snapshot.sharedFenceCreated &&
        snapshot.memoryImported &&
        snapshot.semaphoreImported &&
        snapshot.openGlWriteSubmitted &&
        snapshot.d3d12WaitCompleted &&
        snapshot.readbackMatched &&
        snapshot.d3d12SignalCompleted &&
        snapshot.openGlWaitCompleted;
}

std::string toJson(const GlD3D12InteropSnapshot& snapshot) {
    std::ostringstream json;
    json << '{'
         << "\"schemaVersion\":1,"
         << "\"success\":"
         << jsonBoolean(validateGlD3D12InteropSnapshot(snapshot)) << ','
         << "\"adapterName\":\"" << escapeJson(snapshot.adapterName) << "\","
         << "\"openGlVendor\":\"" << escapeJson(snapshot.openGlVendor) << "\","
         << "\"openGlRenderer\":\"" << escapeJson(snapshot.openGlRenderer) << "\","
         << "\"openGlVersion\":\"" << escapeJson(snapshot.openGlVersion) << "\","
         << "\"requiredExtensions\":{"
         << "\"GL_EXT_memory_object\":" << jsonBoolean(snapshot.memoryObjectExtension) << ','
         << "\"GL_EXT_memory_object_win32\":"
         << jsonBoolean(snapshot.memoryObjectWin32Extension) << ','
         << "\"GL_EXT_semaphore\":" << jsonBoolean(snapshot.semaphoreExtension) << ','
         << "\"GL_EXT_semaphore_win32\":"
         << jsonBoolean(snapshot.semaphoreWin32Extension) << "},"
         << "\"entryPointsLoaded\":" << jsonBoolean(snapshot.entryPointsLoaded) << ','
         << "\"sharedTextureCreated\":" << jsonBoolean(snapshot.sharedTextureCreated) << ','
         << "\"sharedFenceCreated\":" << jsonBoolean(snapshot.sharedFenceCreated) << ','
         << "\"memoryImported\":" << jsonBoolean(snapshot.memoryImported) << ','
         << "\"semaphoreImported\":" << jsonBoolean(snapshot.semaphoreImported) << ','
         << "\"openGlWriteSubmitted\":" << jsonBoolean(snapshot.openGlWriteSubmitted) << ','
         << "\"d3d12WaitCompleted\":" << jsonBoolean(snapshot.d3d12WaitCompleted) << ','
         << "\"readbackMatched\":" << jsonBoolean(snapshot.readbackMatched) << ','
         << "\"d3d12SignalCompleted\":"
         << jsonBoolean(snapshot.d3d12SignalCompleted) << ','
         << "\"openGlWaitCompleted\":" << jsonBoolean(snapshot.openGlWaitCompleted) << ','
         << "\"message\":\"" << escapeJson(snapshot.message) << "\""
         << '}';
    return json.str();
}

}
