#include "fsr3_dxgi_presenter.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>

#include <array>
#include <cstdio>
#include <iomanip>
#include <sstream>
#include <stdexcept>

namespace mc_dlss {
namespace {

using Microsoft::WRL::ComPtr;
constexpr UINT kBufferCount = 2;
constexpr DWORD kQueueWaitMilliseconds = 5000;

void requireSuccess(HRESULT result, const char* operation) {
    if (SUCCEEDED(result)) return;
    std::ostringstream message;
    message << operation << " failed with HRESULT 0x"
            << std::hex << std::uppercase << std::setw(8) << std::setfill('0')
            << static_cast<unsigned long>(result);
    throw std::runtime_error(message.str());
}

}

struct Fsr3DxgiPresenter::Impl {
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<IDXGIFactory6> factory;
    ComPtr<IDXGISwapChain4> swapchain;
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList> commandList;
    ComPtr<ID3D12Fence> completionFence;
    std::array<ComPtr<ID3D12Resource>, kBufferCount> backBuffers;
    HANDLE completionEvent = nullptr;
    HWND window = nullptr;
    std::uint32_t width = 0;
    std::uint32_t height = 0;
    std::uint64_t nextFenceValue = 1;
    std::uint64_t pendingFenceValue = 0;
    std::uint64_t successfulPresents = 0;
    DxgiPresentationState state = DxgiPresentationState::openGlOnly;

    ~Impl() {
        try {
            waitForPreviousSubmission();
        } catch (...) {
        }
        releaseSwapchain();
        if (completionEvent != nullptr) CloseHandle(completionEvent);
    }

    void waitForPreviousSubmission() {
        if (pendingFenceValue == 0 ||
            completionFence->GetCompletedValue() >= pendingFenceValue) return;
        requireSuccess(completionFence->SetEventOnCompletion(
            pendingFenceValue, completionEvent), "SetEventOnCompletion");
        if (WaitForSingleObject(completionEvent, kQueueWaitMilliseconds)
            != WAIT_OBJECT_0) {
            throw std::runtime_error("DXGI presenter queue wait timed out");
        }
    }

    void releaseSwapchain() noexcept {
        if (swapchain != nullptr) swapchain->SetFullscreenState(FALSE, nullptr);
        for (auto& buffer : backBuffers) buffer.Reset();
        swapchain.Reset();
        window = nullptr;
        width = 0;
        height = 0;
        successfulPresents = 0;
    }

    void createSwapchain(HWND nextWindow, std::uint32_t nextWidth,
                         std::uint32_t nextHeight) {
        waitForPreviousSubmission();
        releaseSwapchain();

        DXGI_SWAP_CHAIN_DESC1 description{};
        description.Width = nextWidth;
        description.Height = nextHeight;
        description.Format = DXGI_FORMAT_R16G16B16A16_FLOAT;
        description.SampleDesc.Count = 1;
        description.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
        description.BufferCount = kBufferCount;
        description.Scaling = DXGI_SCALING_STRETCH;
        description.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
        description.AlphaMode = DXGI_ALPHA_MODE_IGNORE;

        ComPtr<IDXGISwapChain1> baseSwapchain;
        requireSuccess(factory->CreateSwapChainForHwnd(
            queue.Get(), nextWindow, &description, nullptr, nullptr,
            &baseSwapchain), "CreateSwapChainForHwnd");
        requireSuccess(baseSwapchain.As(&swapchain), "Query IDXGISwapChain4");
        requireSuccess(factory->MakeWindowAssociation(
            nextWindow, DXGI_MWA_NO_ALT_ENTER), "MakeWindowAssociation");
        for (UINT index = 0; index < kBufferCount; ++index) {
            requireSuccess(swapchain->GetBuffer(
                index, IID_PPV_ARGS(&backBuffers[index])), "GetBuffer");
        }
        window = nextWindow;
        width = nextWidth;
        height = nextHeight;
        state = DxgiPresentationState::warming;
    }

    DxgiPresentationResult present(
        HWND nextWindow, ID3D12Resource* source, ID3D12Fence* sourceFence,
        std::uint64_t sourceFenceValue, std::uint32_t nextWidth,
        std::uint32_t nextHeight) {
        if (state == DxgiPresentationState::fallback) return {state, false};
        if (nextWindow == nullptr || IsWindow(nextWindow) == FALSE ||
            IsWindowVisible(nextWindow) == FALSE || IsIconic(nextWindow) != FALSE ||
            source == nullptr || sourceFence == nullptr || sourceFenceValue == 0 ||
            nextWidth == 0 || nextHeight == 0) {
            return {DxgiPresentationState::openGlOnly, false};
        }
        const D3D12_RESOURCE_DESC sourceDescription = source->GetDesc();
        if (sourceDescription.Dimension != D3D12_RESOURCE_DIMENSION_TEXTURE2D ||
            sourceDescription.Width != nextWidth ||
            sourceDescription.Height != nextHeight ||
            sourceDescription.Format != DXGI_FORMAT_R16G16B16A16_FLOAT ||
            sourceDescription.SampleDesc.Count != 1) {
            throw std::runtime_error("DXGI source texture does not match the output");
        }
        if (swapchain == nullptr || window != nextWindow ||
            width != nextWidth || height != nextHeight) {
            createSwapchain(nextWindow, nextWidth, nextHeight);
        }

        waitForPreviousSubmission();
        requireSuccess(allocator->Reset(), "Reset DXGI presenter allocator");
        requireSuccess(commandList->Reset(allocator.Get(), nullptr),
            "Reset DXGI presenter command list");
        requireSuccess(queue->Wait(sourceFence, sourceFenceValue),
            "Wait for FSR3 output");

        const UINT bufferIndex = swapchain->GetCurrentBackBufferIndex();
        ID3D12Resource* backBuffer = backBuffers[bufferIndex].Get();
        D3D12_RESOURCE_BARRIER barriers[2]{};
        barriers[0].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        barriers[0].Transition.pResource = backBuffer;
        barriers[0].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        barriers[0].Transition.StateBefore = D3D12_RESOURCE_STATE_PRESENT;
        barriers[0].Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_DEST;
        barriers[1].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        barriers[1].Transition.pResource = source;
        barriers[1].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        barriers[1].Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
        barriers[1].Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
        commandList->ResourceBarrier(2, barriers);
        commandList->CopyResource(backBuffer, source);
        for (auto& barrier : barriers) {
            const auto before = barrier.Transition.StateBefore;
            barrier.Transition.StateBefore = barrier.Transition.StateAfter;
            barrier.Transition.StateAfter = before;
        }
        commandList->ResourceBarrier(2, barriers);
        requireSuccess(commandList->Close(), "Close DXGI presenter command list");
        ID3D12CommandList* lists[] = {commandList.Get()};
        queue->ExecuteCommandLists(1, lists);
        pendingFenceValue = nextFenceValue++;
        requireSuccess(queue->Signal(completionFence.Get(), pendingFenceValue),
            "Signal DXGI presenter completion");
        const HRESULT presentResult = swapchain->Present(0, 0);
        if (presentResult != S_OK) requireSuccess(presentResult, "DXGI Present");

        const bool wasWarm = successfulPresents == 0;
        ++successfulPresents;
        state = wasWarm ? DxgiPresentationState::warming
                        : DxgiPresentationState::active;
        return {state, state == DxgiPresentationState::active};
    }
};

Fsr3DxgiPresenter::Fsr3DxgiPresenter(std::unique_ptr<Impl> impl)
    : impl_(std::move(impl)) {}

Fsr3DxgiPresenter::~Fsr3DxgiPresenter() = default;

std::unique_ptr<Fsr3DxgiPresenter> Fsr3DxgiPresenter::create(
    ID3D12Device* device, ID3D12CommandQueue* queue) {
    if (device == nullptr || queue == nullptr) return nullptr;
    auto impl = std::make_unique<Impl>();
    impl->device = device;
    impl->queue = queue;
    requireSuccess(CreateDXGIFactory2(0, IID_PPV_ARGS(&impl->factory)),
        "CreateDXGIFactory2");
    requireSuccess(device->CreateCommandAllocator(
        D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&impl->allocator)),
        "Create DXGI presenter allocator");
    requireSuccess(device->CreateCommandList(
        0, D3D12_COMMAND_LIST_TYPE_DIRECT, impl->allocator.Get(), nullptr,
        IID_PPV_ARGS(&impl->commandList)), "Create DXGI presenter command list");
    requireSuccess(impl->commandList->Close(),
        "Close initial DXGI presenter command list");
    requireSuccess(device->CreateFence(
        0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&impl->completionFence)),
        "Create DXGI presenter fence");
    impl->completionEvent = CreateEventW(nullptr, FALSE, FALSE, nullptr);
    if (impl->completionEvent == nullptr) {
        throw std::runtime_error("CreateEventW failed for DXGI presenter");
    }
    return std::unique_ptr<Fsr3DxgiPresenter>(
        new Fsr3DxgiPresenter(std::move(impl)));
}

DxgiPresentationResult Fsr3DxgiPresenter::presentRealFrame(
    std::uintptr_t windowHandle, ID3D12Resource* source,
    ID3D12Fence* sourceFence, std::uint64_t sourceFenceValue,
    std::uint32_t width, std::uint32_t height) noexcept {
    try {
        return impl_->present(reinterpret_cast<HWND>(windowHandle), source,
            sourceFence, sourceFenceValue, width, height);
    } catch (const std::exception& error) {
        std::fprintf(stderr, "[mc_dlss/dxgi] FALLBACK %s\n", error.what());
        impl_->state = DxgiPresentationState::fallback;
        try {
            impl_->waitForPreviousSubmission();
        } catch (...) {
        }
        impl_->releaseSwapchain();
        return {DxgiPresentationState::fallback, false};
    } catch (...) {
        std::fprintf(stderr, "[mc_dlss/dxgi] FALLBACK unknown native failure\n");
        impl_->state = DxgiPresentationState::fallback;
        try {
            impl_->waitForPreviousSubmission();
        } catch (...) {
        }
        impl_->releaseSwapchain();
        return {DxgiPresentationState::fallback, false};
    }
}

void Fsr3DxgiPresenter::reset() noexcept {
    try {
        impl_->waitForPreviousSubmission();
    } catch (...) {
    }
    impl_->releaseSwapchain();
    impl_->state = DxgiPresentationState::openGlOnly;
}

}
