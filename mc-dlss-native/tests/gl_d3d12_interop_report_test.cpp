#include "gl_d3d12_interop_report.h"

#include <cassert>
#include <string>

namespace {

mc_dlss::GlD3D12InteropSnapshot completeSnapshot() {
    mc_dlss::GlD3D12InteropSnapshot snapshot{};
    snapshot.adapterName = "NVIDIA GeForce RTX 4070 Laptop GPU";
    snapshot.openGlVendor = "NVIDIA Corporation";
    snapshot.openGlRenderer = "NVIDIA GeForce RTX 4070 Laptop GPU/PCIe/SSE2";
    snapshot.openGlVersion = "4.6.0 NVIDIA";
    snapshot.memoryObjectExtension = true;
    snapshot.memoryObjectWin32Extension = true;
    snapshot.semaphoreExtension = true;
    snapshot.semaphoreWin32Extension = true;
    snapshot.entryPointsLoaded = true;
    snapshot.sharedTextureCreated = true;
    snapshot.sharedFenceCreated = true;
    snapshot.memoryImported = true;
    snapshot.semaphoreImported = true;
    snapshot.openGlWriteSubmitted = true;
    snapshot.d3d12WaitCompleted = true;
    snapshot.readbackMatched = true;
    snapshot.d3d12SignalCompleted = true;
    snapshot.openGlWaitCompleted = true;
    snapshot.message = "OpenGL-D3D12 interop completed.";
    return snapshot;
}

}

int main() {
    const auto complete = completeSnapshot();
    assert(mc_dlss::validateGlD3D12InteropSnapshot(complete));

    const std::string successJson = mc_dlss::toJson(complete);
    assert(successJson.find("\"schemaVersion\":1") != std::string::npos);
    assert(successJson.find("\"success\":true") != std::string::npos);
    assert(successJson.find("\"GL_EXT_memory_object\":true") != std::string::npos);
    assert(successJson.find("\"GL_EXT_memory_object_win32\":true") != std::string::npos);
    assert(successJson.find("\"GL_EXT_semaphore\":true") != std::string::npos);
    assert(successJson.find("\"GL_EXT_semaphore_win32\":true") != std::string::npos);

    auto missingReadback = complete;
    missingReadback.readbackMatched = false;
    assert(!mc_dlss::validateGlD3D12InteropSnapshot(missingReadback));

    auto missingReturnWait = complete;
    missingReturnWait.openGlWaitCompleted = false;
    assert(!mc_dlss::validateGlD3D12InteropSnapshot(missingReturnWait));

    auto escaped = complete;
    escaped.message = "Import \"failed\" at C:\\interop.\nRetry stopped.";
    const std::string escapedJson = mc_dlss::toJson(escaped);
    assert(escapedJson.find(
        "Import \\\"failed\\\" at C:\\\\interop.\\nRetry stopped.") !=
        std::string::npos);

    return 0;
}
