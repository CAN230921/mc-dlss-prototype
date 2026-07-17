#include "live_fsr3_session.h"

#include <d3d12.h>
#include <wrl/client.h>

#include <utility>

namespace mc_dlss {

struct LiveFsr3Session::Impl {
    Microsoft::WRL::ComPtr<ID3D12Device> device;
    std::unique_ptr<Fsr3ApiRuntime> runtime;
    std::unique_ptr<Fsr3DxgiPresenter> presenter;
    Fsr3CapabilitySnapshot capability;
};

LiveFsr3Session::LiveFsr3Session(std::unique_ptr<Impl> impl)
    : impl_(std::move(impl)) {}

LiveFsr3Session::~LiveFsr3Session() = default;

std::unique_ptr<LiveFsr3Session> LiveFsr3Session::create(
    ID3D12Device* device,
    ID3D12CommandQueue* queue,
    const std::wstring& loaderPath,
    unsigned int renderWidth,
    unsigned int renderHeight,
    unsigned int displayWidth,
    unsigned int displayHeight) {
    if (device == nullptr || queue == nullptr) return nullptr;
    auto loaded = Fsr3ApiRuntime::load(loaderPath);
    if (!loaded.runtime) return nullptr;
    auto impl = std::make_unique<Impl>();
    impl->device = device;
    impl->runtime = std::move(loaded.runtime);
    impl->presenter = Fsr3DxgiPresenter::create(device, queue);
    impl->capability = impl->runtime->initializeContexts(
        device, renderWidth, renderHeight, displayWidth, displayHeight);
    return std::unique_ptr<LiveFsr3Session>(
        new LiveFsr3Session(std::move(impl)));
}

const Fsr3CapabilitySnapshot& LiveFsr3Session::capability() const noexcept {
    return impl_->capability;
}

bool LiveFsr3Session::superResolutionReady() const noexcept {
    return impl_->capability.upscaler.state == Fsr3FeatureState::ContextReady;
}

bool LiveFsr3Session::frameGenerationReady() const noexcept {
    return impl_->capability.frameGeneration.state == Fsr3FeatureState::ContextReady;
}

unsigned int LiveFsr3Session::dispatchUpscale(
    const Fsr3UpscaleDispatchInputs& inputs) noexcept {
    return impl_->runtime == nullptr ? 4U : impl_->runtime->dispatchUpscale(inputs);
}

unsigned int LiveFsr3Session::dispatchFrameGeneration(
    const Fsr3FrameGenerationDispatchInputs& inputs) noexcept {
    return impl_->runtime == nullptr ? 4U
        : impl_->runtime->dispatchFrameGeneration(inputs);
}

DxgiPresentationResult LiveFsr3Session::presentRealFrame(
    std::uintptr_t windowHandle, ID3D12Resource* source,
    ID3D12Fence* sourceFence, std::uint64_t sourceFenceValue,
    std::uint32_t width, std::uint32_t height) noexcept {
    return impl_->presenter == nullptr
        ? DxgiPresentationResult{DxgiPresentationState::fallback, false}
        : impl_->presenter->presentRealFrame(
            windowHandle, source, sourceFence, sourceFenceValue, width, height);
}

}
