#pragma once

#include "fsr3_api_runtime.h"
#include "fsr3_dxgi_presenter.h"

#include <memory>
#include <string>

struct ID3D12Device;
struct ID3D12CommandQueue;
struct ID3D12Fence;
struct ID3D12Resource;

namespace mc_dlss {

class LiveFsr3Session {
public:
    static std::unique_ptr<LiveFsr3Session> create(
        ID3D12Device* device,
        ID3D12CommandQueue* queue,
        const std::wstring& loaderPath,
        unsigned int renderWidth,
        unsigned int renderHeight,
        unsigned int displayWidth,
        unsigned int displayHeight);

    ~LiveFsr3Session();
    LiveFsr3Session(const LiveFsr3Session&) = delete;
    LiveFsr3Session& operator=(const LiveFsr3Session&) = delete;

    const Fsr3CapabilitySnapshot& capability() const noexcept;
    bool superResolutionReady() const noexcept;
    bool frameGenerationReady() const noexcept;
    unsigned int dispatchUpscale(
        const Fsr3UpscaleDispatchInputs& inputs) noexcept;
    unsigned int dispatchFrameGeneration(
        const Fsr3FrameGenerationDispatchInputs& inputs) noexcept;
    DxgiPresentationResult presentRealFrame(
        std::uintptr_t windowHandle,
        ID3D12Resource* source,
        ID3D12Fence* sourceFence,
        std::uint64_t sourceFenceValue,
        std::uint32_t width,
        std::uint32_t height) noexcept;

private:
    struct Impl;
    explicit LiveFsr3Session(std::unique_ptr<Impl> impl);
    std::unique_ptr<Impl> impl_;
};

}
