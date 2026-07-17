#pragma once

#include <cstdint>
#include <memory>

struct ID3D12CommandQueue;
struct ID3D12Device;
struct ID3D12Fence;
struct ID3D12Resource;

namespace mc_dlss {

enum class DxgiPresentationState : std::uint32_t {
    openGlOnly = 0,
    warming = 1,
    active = 2,
    fallback = 3,
};

struct DxgiPresentationResult {
    DxgiPresentationState state = DxgiPresentationState::openGlOnly;
    bool suppressOpenGlSwap = false;
};

class Fsr3DxgiPresenter {
public:
    static std::unique_ptr<Fsr3DxgiPresenter> create(
        ID3D12Device* device, ID3D12CommandQueue* queue);

    ~Fsr3DxgiPresenter();
    Fsr3DxgiPresenter(const Fsr3DxgiPresenter&) = delete;
    Fsr3DxgiPresenter& operator=(const Fsr3DxgiPresenter&) = delete;

    DxgiPresentationResult presentRealFrame(
        std::uintptr_t windowHandle,
        ID3D12Resource* source,
        ID3D12Fence* sourceFence,
        std::uint64_t sourceFenceValue,
        std::uint32_t width,
        std::uint32_t height) noexcept;
    void reset() noexcept;

private:
    struct Impl;
    explicit Fsr3DxgiPresenter(std::unique_ptr<Impl> impl);
    std::unique_ptr<Impl> impl_;
};

}
