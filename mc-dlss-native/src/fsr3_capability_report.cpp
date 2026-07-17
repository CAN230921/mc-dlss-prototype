#include "fsr3_capability_report.h"

#include <sstream>

namespace mc_dlss {
namespace {

std::string summarizeFeature(const Fsr3FeatureCapability& feature) {
    switch (feature.state) {
        case Fsr3FeatureState::ProviderAvailable:
            return "provider:" + feature.providerName;
        case Fsr3FeatureState::ContextReady: {
            std::ostringstream value;
            value << "ready:" << feature.providerName << ':'
                  << feature.memoryMiB << "MiB";
            return value.str();
        }
        case Fsr3FeatureState::ContextFailed:
            return "failed:" + std::to_string(feature.returnCode);
        case Fsr3FeatureState::Unavailable:
        default:
            return "unavailable";
    }
}

}

std::string summarizeFsr3Capability(const Fsr3CapabilitySnapshot& snapshot) {
    std::ostringstream summary;
    summary << "loader=" << (snapshot.loaderAvailable ? "ready" : "unavailable")
            << " sr=" << summarizeFeature(snapshot.upscaler)
            << " fg=" << summarizeFeature(snapshot.frameGeneration);
    if (!snapshot.message.empty()) summary << " message=" << snapshot.message;
    return summary.str();
}

}
