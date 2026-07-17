#include "fsr3_capability_report.h"

#include <iostream>
#include <string>

namespace {

bool contains(const std::string& value, const std::string& expected) {
    return value.find(expected) != std::string::npos;
}

bool requireSummary(
    const mc_dlss::Fsr3CapabilitySnapshot& snapshot,
    const std::string& expected) {
    const std::string summary = mc_dlss::summarizeFsr3Capability(snapshot);
    if (contains(summary, expected)) return true;
    std::cerr << "Expected '" << expected << "' in: " << summary << '\n';
    return false;
}

}

int main() {
    using mc_dlss::Fsr3CapabilitySnapshot;
    using mc_dlss::Fsr3FeatureState;

    Fsr3CapabilitySnapshot missing{};
    missing.message = "loader missing";
    if (!requireSummary(missing, "loader=unavailable")) return 1;

    Fsr3CapabilitySnapshot srOnly{};
    srOnly.loaderAvailable = true;
    srOnly.upscaler = {Fsr3FeatureState::ProviderAvailable, "FSR3 Upscaler 3.1.5", 0, 0};
    if (!requireSummary(srOnly, "sr=provider:FSR3 Upscaler 3.1.5") ||
        !requireSummary(srOnly, "fg=unavailable")) return 1;

    Fsr3CapabilitySnapshot fgOnly{};
    fgOnly.loaderAvailable = true;
    fgOnly.frameGeneration = {
        Fsr3FeatureState::ProviderAvailable, "FSR3 Frame Generation 3.1.6", 0, 0};
    if (!requireSummary(fgOnly, "sr=unavailable") ||
        !requireSummary(fgOnly, "fg=provider:FSR3 Frame Generation 3.1.6")) return 1;

    Fsr3CapabilitySnapshot dual{};
    dual.loaderAvailable = true;
    dual.upscaler = {Fsr3FeatureState::ContextReady, "FSR3 Upscaler 3.1.5", 0, 128};
    dual.frameGeneration = {
        Fsr3FeatureState::ContextReady, "FSR3 Frame Generation 3.1.6", 0, 256};
    if (!requireSummary(dual, "sr=ready:FSR3 Upscaler 3.1.5:128MiB") ||
        !requireSummary(dual, "fg=ready:FSR3 Frame Generation 3.1.6:256MiB")) return 1;

    Fsr3CapabilitySnapshot failed = dual;
    failed.frameGeneration.state = Fsr3FeatureState::ContextFailed;
    failed.frameGeneration.returnCode = 6;
    if (!requireSummary(failed, "fg=failed:6")) return 1;

    std::cout << "fsr3_capability_report_test passed\n";
    return 0;
}
