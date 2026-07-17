#include "live_d3d12_interop_session.h"

#include "interop_test_pattern.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>

#include <sstream>
#include <stdexcept>
#include <vector>
#include <cstdio>

namespace mc_dlss {
namespace {

using Microsoft::WRL::ComPtr;

void requireSuccess(HRESULT result, const char* operation) {
    if (SUCCEEDED(result)) {
        return;
    }
    std::ostringstream message;
    message << operation << " failed with HRESULT 0x" << std::hex
            << std::uppercase << static_cast<unsigned long>(result);
    throw std::runtime_error(message.str());
}

class ScopedHandle {
public:
    ScopedHandle() noexcept = default;
    explicit ScopedHandle(HANDLE value) noexcept : value_(value) {}
    ~ScopedHandle() {
        if (value_ != nullptr && value_ != INVALID_HANDLE_VALUE) {
            CloseHandle(value_);
        }
    }

    ScopedHandle(const ScopedHandle&) = delete;
    ScopedHandle& operator=(const ScopedHandle&) = delete;

    HANDLE get() const noexcept { return value_; }
    void reset(HANDLE value) noexcept {
        if (value_ != nullptr && value_ != INVALID_HANDLE_VALUE) {
            CloseHandle(value_);
        }
        value_ = value;
    }

private:
    HANDLE value_ = nullptr;
};

}

struct LiveD3D12InteropSession::Impl {
    std::uint32_t width = 0;
    std::uint32_t height = 0;
    ComPtr<IDXGIFactory6> factory;
    ComPtr<IDXGIAdapter1> adapter;
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList> commandList;
    ComPtr<ID3D12Resource> texture;
    ComPtr<ID3D12Fence> fence;
    ScopedHandle textureHandle;
    ScopedHandle fenceHandle;
    ComPtr<ID3D12Resource> readback;
    D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint{};
    ScopedHandle completionEvent;
    std::vector<std::uint8_t> expected;
    bool submitted = false;
    bool verified = false;
    bool matched = false;
    std::uint64_t lastSubmittedSignal = 0;
    std::uint64_t lastInspectedSignal = 0;
};

bool validPersistentFencePair(
    std::uint64_t previousSignalValue,
    std::uint64_t waitValue,
    std::uint64_t signalValue) noexcept {
    return previousSignalValue % 2U == 0 &&
        previousSignalValue < UINT64_MAX - 1U &&
        waitValue == previousSignalValue + 1U &&
        waitValue % 2U == 1U &&
        signalValue == waitValue + 1U;
}

std::shared_ptr<LiveD3D12InteropSession> LiveD3D12InteropSession::create(
    std::uint32_t width,
    std::uint32_t height) {
    return std::shared_ptr<LiveD3D12InteropSession>(
        new LiveD3D12InteropSession(width, height));
}

LiveD3D12InteropSession::LiveD3D12InteropSession(
    std::uint32_t width,
    std::uint32_t height)
    : impl_(std::make_unique<Impl>()) {
    if (width == 0 || height == 0 || width > 8192 || height > 8192) {
        throw std::invalid_argument("Interop session dimensions must be within 1..8192.");
    }
    auto& state = *impl_;
    state.width = width;
    state.height = height;
    if (width == 64 && height == 64) {
        state.expected = makeInteropTestPattern(width, height);
    }

    requireSuccess(
        CreateDXGIFactory2(0, IID_PPV_ARGS(&state.factory)),
        "CreateDXGIFactory2");
    for (UINT index = 0;; ++index) {
        ComPtr<IDXGIAdapter1> candidate;
        const HRESULT result = state.factory->EnumAdapterByGpuPreference(
            index,
            DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
            IID_PPV_ARGS(&candidate));
        if (result == DXGI_ERROR_NOT_FOUND) {
            break;
        }
        requireSuccess(result, "IDXGIFactory6::EnumAdapterByGpuPreference");

        DXGI_ADAPTER_DESC1 description{};
        requireSuccess(candidate->GetDesc1(&description), "IDXGIAdapter1::GetDesc1");
        if ((description.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0) {
            continue;
        }
        ComPtr<ID3D12Device> candidateDevice;
        if (SUCCEEDED(D3D12CreateDevice(
                candidate.Get(), D3D_FEATURE_LEVEL_12_0,
                IID_PPV_ARGS(&candidateDevice)))) {
            state.adapter = candidate;
            state.device = candidateDevice;
            break;
        }
    }
    if (state.device == nullptr) {
        throw std::runtime_error("No hardware D3D12 adapter is available.");
    }

    D3D12_COMMAND_QUEUE_DESC queueDescription{};
    queueDescription.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
    requireSuccess(
        state.device->CreateCommandQueue(
            &queueDescription, IID_PPV_ARGS(&state.queue)),
        "ID3D12Device::CreateCommandQueue");
    requireSuccess(
        state.device->CreateCommandAllocator(
            D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&state.allocator)),
        "ID3D12Device::CreateCommandAllocator");
    requireSuccess(
        state.device->CreateCommandList(
            0, D3D12_COMMAND_LIST_TYPE_DIRECT, state.allocator.Get(), nullptr,
            IID_PPV_ARGS(&state.commandList)),
        "ID3D12Device::CreateCommandList");

    D3D12_HEAP_PROPERTIES defaultHeap{};
    defaultHeap.Type = D3D12_HEAP_TYPE_DEFAULT;
    defaultHeap.CreationNodeMask = 1;
    defaultHeap.VisibleNodeMask = 1;
    D3D12_RESOURCE_DESC textureDescription{};
    textureDescription.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
    textureDescription.Width = width;
    textureDescription.Height = height;
    textureDescription.DepthOrArraySize = 1;
    textureDescription.MipLevels = 1;
    textureDescription.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    textureDescription.SampleDesc.Count = 1;
    textureDescription.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
    requireSuccess(
        state.device->CreateCommittedResource(
            &defaultHeap, D3D12_HEAP_FLAG_SHARED, &textureDescription,
            D3D12_RESOURCE_STATE_COMMON, nullptr,
            IID_PPV_ARGS(&state.texture)),
        "ID3D12Device::CreateCommittedResource(shared texture)");
    requireSuccess(
        state.device->CreateFence(
            0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&state.fence)),
        "ID3D12Device::CreateFence(shared)");

    HANDLE textureHandle = nullptr;
    requireSuccess(
        state.device->CreateSharedHandle(
            state.texture.Get(), nullptr, GENERIC_ALL, nullptr, &textureHandle),
        "ID3D12Device::CreateSharedHandle(texture)");
    state.textureHandle.reset(textureHandle);
    HANDLE fenceHandle = nullptr;
    requireSuccess(
        state.device->CreateSharedHandle(
            state.fence.Get(), nullptr, GENERIC_ALL, nullptr, &fenceHandle),
        "ID3D12Device::CreateSharedHandle(fence)");
    state.fenceHandle.reset(fenceHandle);

    UINT rowCount = 0;
    UINT64 rowSize = 0;
    UINT64 totalBytes = 0;
    state.device->GetCopyableFootprints(
        &textureDescription, 0, 1, 0, &state.footprint,
        &rowCount, &rowSize, &totalBytes);
    if (rowCount != height || rowSize != width * 4ULL || totalBytes == 0) {
        throw std::runtime_error("Unexpected live interop readback footprint.");
    }

    D3D12_HEAP_PROPERTIES readbackHeap{};
    readbackHeap.Type = D3D12_HEAP_TYPE_READBACK;
    readbackHeap.CreationNodeMask = 1;
    readbackHeap.VisibleNodeMask = 1;
    D3D12_RESOURCE_DESC readbackDescription{};
    readbackDescription.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
    readbackDescription.Width = totalBytes;
    readbackDescription.Height = 1;
    readbackDescription.DepthOrArraySize = 1;
    readbackDescription.MipLevels = 1;
    readbackDescription.SampleDesc.Count = 1;
    readbackDescription.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
    requireSuccess(
        state.device->CreateCommittedResource(
            &readbackHeap, D3D12_HEAP_FLAG_NONE, &readbackDescription,
            D3D12_RESOURCE_STATE_COPY_DEST, nullptr,
            IID_PPV_ARGS(&state.readback)),
        "ID3D12Device::CreateCommittedResource(readback)");

    state.completionEvent.reset(CreateEventW(nullptr, FALSE, FALSE, nullptr));
    if (state.completionEvent.get() == nullptr) {
        throw std::runtime_error("CreateEventW failed for live interop completion.");
    }
}

LiveD3D12InteropSession::~LiveD3D12InteropSession() = default;

std::uintptr_t LiveD3D12InteropSession::textureHandle() const noexcept {
    return reinterpret_cast<std::uintptr_t>(impl_->textureHandle.get());
}

std::uintptr_t LiveD3D12InteropSession::fenceHandle() const noexcept {
    return reinterpret_cast<std::uintptr_t>(impl_->fenceHandle.get());
}

bool LiveD3D12InteropSession::submitReadback() {
    auto& state = *impl_;
    return state.lastSubmittedSignal == 2 || submitReadback(1, 2);
}

bool LiveD3D12InteropSession::submitReadback(
    std::uint64_t waitValue,
    std::uint64_t signalValue) {
    auto& state = *impl_;
    if (!validPersistentFencePair(
            state.lastSubmittedSignal, waitValue, signalValue)) {
        return false;
    }
    if (state.lastSubmittedSignal != 0) {
        if (state.fence->GetCompletedValue() < state.lastSubmittedSignal) {
            return false;
        }
        requireSuccess(state.allocator->Reset(), "ID3D12CommandAllocator::Reset");
        requireSuccess(
            state.commandList->Reset(state.allocator.Get(), nullptr),
            "ID3D12GraphicsCommandList::Reset");
    }
    requireSuccess(
        state.queue->Wait(state.fence.Get(), waitValue),
        "ID3D12CommandQueue::Wait(OpenGL fence value)");

    D3D12_RESOURCE_BARRIER toCopySource{};
    toCopySource.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    toCopySource.Transition.pResource = state.texture.Get();
    toCopySource.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    toCopySource.Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
    toCopySource.Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
    state.commandList->ResourceBarrier(1, &toCopySource);

    D3D12_TEXTURE_COPY_LOCATION destination{};
    destination.pResource = state.readback.Get();
    destination.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
    destination.PlacedFootprint = state.footprint;
    D3D12_TEXTURE_COPY_LOCATION source{};
    source.pResource = state.texture.Get();
    source.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
    source.SubresourceIndex = 0;
    state.commandList->CopyTextureRegion(&destination, 0, 0, 0, &source, nullptr);

    D3D12_RESOURCE_BARRIER toCommon = toCopySource;
    toCommon.Transition.StateBefore = D3D12_RESOURCE_STATE_COPY_SOURCE;
    toCommon.Transition.StateAfter = D3D12_RESOURCE_STATE_COMMON;
    state.commandList->ResourceBarrier(1, &toCommon);
    requireSuccess(state.commandList->Close(), "ID3D12GraphicsCommandList::Close");
    ID3D12CommandList* commandLists[] = {state.commandList.Get()};
    state.queue->ExecuteCommandLists(1, commandLists);
    requireSuccess(
        state.queue->Signal(state.fence.Get(), signalValue),
        "ID3D12CommandQueue::Signal(fence value)");
    state.submitted = true;
    state.verified = false;
    state.lastSubmittedSignal = signalValue;
    return true;
}

bool LiveD3D12InteropSession::verifyReadback() {
    auto& state = *impl_;
    if (!state.submitted || state.lastSubmittedSignal != 2) {
        return false;
    }
    if (state.verified) {
        return state.matched;
    }
    if (state.fence->GetCompletedValue() < 2) {
        requireSuccess(
            state.fence->SetEventOnCompletion(2, state.completionEvent.get()),
            "ID3D12Fence::SetEventOnCompletion");
        if (WaitForSingleObject(state.completionEvent.get(), 5000) != WAIT_OBJECT_0) {
            return false;
        }
    }

    void* mapped = nullptr;
    const SIZE_T readEnd = interopReadbackRangeEnd(
        static_cast<SIZE_T>(state.footprint.Offset),
        static_cast<SIZE_T>(state.footprint.Footprint.RowPitch),
        static_cast<SIZE_T>(state.width) * 4U,
        state.height);
    D3D12_RANGE readRange{0, readEnd};
    requireSuccess(state.readback->Map(0, &readRange, &mapped), "ID3D12Resource::Map");
    const auto* bytes = static_cast<const std::uint8_t*>(mapped) +
        state.footprint.Offset;
    state.matched = interopReadbackMatches(
        bytes, state.footprint.Footprint.RowPitch, state.expected,
        state.width, state.height);
    D3D12_RANGE writtenRange{0, 0};
    state.readback->Unmap(0, &writtenRange);
    state.verified = true;
    return state.matched;
}

InteropReadbackFingerprint LiveD3D12InteropSession::inspectReadback() {
    return inspectReadback(2);
}

InteropReadbackFingerprint LiveD3D12InteropSession::inspectReadback(
    std::uint64_t signalValue) {
    auto& state = *impl_;
    if (!state.submitted || signalValue != state.lastSubmittedSignal ||
        signalValue % 2U != 0) {
        std::fprintf(
            stderr,
            "[mc_dlss/native/persistent] inspect rejected submitted=%d requested=%llu last=%llu\n",
            state.submitted ? 1 : 0,
            static_cast<unsigned long long>(signalValue),
            static_cast<unsigned long long>(state.lastSubmittedSignal));
        InteropReadbackFingerprint result{};
        result.status = InteropReadbackStatus::invalidRequest;
        result.completedFenceValue = state.fence->GetCompletedValue();
        result.lastSubmittedSignal = state.lastSubmittedSignal;
        return result;
    }
    if (state.fence->GetCompletedValue() < signalValue) {
        requireSuccess(
            state.fence->SetEventOnCompletion(signalValue, state.completionEvent.get()),
            "ID3D12Fence::SetEventOnCompletion");
        if (WaitForSingleObject(state.completionEvent.get(), 5000) != WAIT_OBJECT_0) {
            std::fprintf(
                stderr,
                "[mc_dlss/native/persistent] inspect timeout requested=%llu completed=%llu\n",
                static_cast<unsigned long long>(signalValue),
                static_cast<unsigned long long>(state.fence->GetCompletedValue()));
            InteropReadbackFingerprint result{};
            result.status = InteropReadbackStatus::timeout;
            result.completedFenceValue = state.fence->GetCompletedValue();
            result.lastSubmittedSignal = state.lastSubmittedSignal;
            return result;
        }
    }

    void* mapped = nullptr;
    const SIZE_T readEnd = interopReadbackRangeEnd(
        static_cast<SIZE_T>(state.footprint.Offset),
        static_cast<SIZE_T>(state.footprint.Footprint.RowPitch),
        static_cast<SIZE_T>(state.width) * 4U,
        state.height);
    D3D12_RANGE readRange{0, readEnd};
    requireSuccess(state.readback->Map(0, &readRange, &mapped), "ID3D12Resource::Map");
    const auto* bytes = static_cast<const std::uint8_t*>(mapped) +
        state.footprint.Offset;
    auto result = fingerprintInteropReadback(
        bytes, state.footprint.Footprint.RowPitch, state.width, state.height);
    D3D12_RANGE writtenRange{0, 0};
    state.readback->Unmap(0, &writtenRange);
    state.lastInspectedSignal = signalValue;
    result.completedFenceValue = state.fence->GetCompletedValue();
    result.lastSubmittedSignal = state.lastSubmittedSignal;
    return result;
}

}
