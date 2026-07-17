#include "dxgi_window_present_probe_report.h"

#include <array>
#include <iostream>
#include <string>

namespace {

bool require(bool condition, const char* message) {
    if (!condition) {
        std::cerr << "FAILED: " << message << '\n';
        return false;
    }
    return true;
}

mc_dlss::DxgiWindowPresentProbeSnapshot completeSnapshot() {
    mc_dlss::DxgiWindowPresentProbeSnapshot snapshot{};
    snapshot.adapterName = "NVIDIA GeForce RTX 4070 Laptop GPU";
    snapshot.openGlVendor = "NVIDIA Corporation";
    snapshot.openGlRenderer = "NVIDIA GeForce RTX 4070 Laptop GPU/PCIe/SSE2";
    snapshot.sameWindowHandle = true;
    snapshot.openGlContextCurrent = true;
    snapshot.openGlSwapCompleted = true;
    snapshot.dxgiSwapchainCreated = true;
    snapshot.firstBackBufferMatched = true;
    snapshot.firstPresentSucceeded = true;
    snapshot.resizeSucceeded = true;
    snapshot.secondBackBufferMatched = true;
    snapshot.secondPresentSucceeded = true;
    snapshot.openGlContextStillCurrent = true;
    snapshot.deviceRemovedReason = 0;
    snapshot.message = "Same-HWND DXGI presentation completed.";
    return snapshot;
}

bool jsonContainsAllBooleanFieldNames(const std::string& json) {
    constexpr std::array<const char*, 10> booleanFieldNames = {
        "sameWindowHandle",
        "openGlContextCurrent",
        "openGlSwapCompleted",
        "dxgiSwapchainCreated",
        "firstBackBufferMatched",
        "firstPresentSucceeded",
        "resizeSucceeded",
        "secondBackBufferMatched",
        "secondPresentSucceeded",
        "openGlContextStillCurrent",
    };
    for (const char* fieldName : booleanFieldNames) {
        if (json.find(std::string("\"") + fieldName + "\":") ==
            std::string::npos) {
            std::cerr << "Missing JSON boolean field: " << fieldName << '\n';
            return false;
        }
    }
    return true;
}

}

int main() {
    const auto complete = completeSnapshot();
    if (!require(mc_dlss::validateDxgiWindowPresentProbeSnapshot(complete),
            "complete snapshot should validate")) return 1;

    const std::string completeJson = mc_dlss::toJson(complete);
    if (!require(completeJson.find("\"schemaVersion\":1") != std::string::npos,
            "JSON should contain schemaVersion 1")) return 1;
    if (!require(completeJson.find("\"success\":true") != std::string::npos,
            "JSON should report success")) return 1;
    if (!jsonContainsAllBooleanFieldNames(completeJson)) return 1;

    auto invalid = complete;
    invalid.sameWindowHandle = false;
    if (!require(!mc_dlss::validateDxgiWindowPresentProbeSnapshot(invalid),
            "sameWindowHandle=false should fail validation")) return 1;
    invalid = complete;
    invalid.firstPresentSucceeded = false;
    if (!require(!mc_dlss::validateDxgiWindowPresentProbeSnapshot(invalid),
            "firstPresentSucceeded=false should fail validation")) return 1;
    invalid = complete;
    invalid.resizeSucceeded = false;
    if (!require(!mc_dlss::validateDxgiWindowPresentProbeSnapshot(invalid),
            "resizeSucceeded=false should fail validation")) return 1;
    invalid = complete;
    invalid.secondPresentSucceeded = false;
    if (!require(!mc_dlss::validateDxgiWindowPresentProbeSnapshot(invalid),
            "secondPresentSucceeded=false should fail validation")) return 1;
    invalid = complete;
    invalid.openGlContextStillCurrent = false;
    if (!require(!mc_dlss::validateDxgiWindowPresentProbeSnapshot(invalid),
            "openGlContextStillCurrent=false should fail validation")) return 1;

    auto escaped = complete;
    escaped.message = "Present \"failed\" at C:\\dxgi.\nStopped.";
    const std::string escapedJson = mc_dlss::toJson(escaped);
    if (!require(escapedJson.find(
            "\"message\":\"Present \\\"failed\\\" at C:\\\\dxgi.\\nStopped.\"") !=
            std::string::npos,
            "JSON should escape quotes, backslashes, and newlines")) return 1;

    std::cout << "dxgi_window_present_probe_report_test passed\n";
    return 0;
}
