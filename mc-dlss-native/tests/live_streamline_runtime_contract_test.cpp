#include "live_streamline_runtime.h"

#include <iostream>
#include <vector>

namespace {

class FakeBackend final : public mc_dlss::LiveStreamlineStageBackend {
public:
    explicit FakeBackend(mc_dlss::LiveStreamlineStage failure) : failure_(failure) {}

    bool invoke(mc_dlss::LiveStreamlineStage stage) override {
        calls.push_back(stage);
        return stage != failure_;
    }

    std::vector<mc_dlss::LiveStreamlineStage> calls;
private:
    mc_dlss::LiveStreamlineStage failure_;
};

}

int main() {
    using mc_dlss::LiveStreamlineStage;
    mc_dlss::LiveStreamlineFrameSequence frames;
    std::uint32_t frame = 99;
    for (std::uint32_t expected = 0; expected < 3; ++expected) {
        if (!frames.next(frame) || frame != expected) {
            std::cerr << "Streamline frame sequence is not monotonic\n";
            return 1;
        }
    }
    const LiveStreamlineStage stages[] = {
        LiveStreamlineStage::initialize,
        LiveStreamlineStage::device,
        LiveStreamlineStage::support,
        LiveStreamlineStage::settings,
        LiveStreamlineStage::options,
        LiveStreamlineStage::token,
        LiveStreamlineStage::constants,
        LiveStreamlineStage::tags,
        LiveStreamlineStage::evaluate,
        LiveStreamlineStage::freeResources,
    };
    for (const auto failure : stages) {
        FakeBackend backend(failure);
        const auto result = mc_dlss::runLiveStreamlineStageContract(backend);
        if (result.success || result.stage != failure || backend.calls.back() != failure) {
            std::cerr << "Streamline failure stage was not preserved\n";
            return 1;
        }
    }
    FakeBackend success(LiveStreamlineStage::none);
    const auto result = mc_dlss::runLiveStreamlineStageContract(success);
    if (!result.success || result.stage != LiveStreamlineStage::ready
        || success.calls.size() != 10) {
        std::cerr << "Streamline success contract mismatch\n";
        return 1;
    }
    if (std::string(mc_dlss::liveStreamlineStageName(LiveStreamlineStage::evaluate))
            != "EVALUATE") {
        std::cerr << "Streamline stage label mismatch\n";
        return 1;
    }
    std::cout << "live_streamline_runtime_contract_test passed\n";
    return 0;
}
