#include "live_d3d12_frame_session.h"

#include "live_d3d12_interop_session.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>

#include <sstream>
#include <stdexcept>

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
    ~ScopedHandle() {
        if (value_ != nullptr && value_ != INVALID_HANDLE_VALUE) {
            CloseHandle(value_);
        }
    }
    ScopedHandle() = default;
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

struct LiveD3D12FrameSession::Impl {
    struct SharedTexture {
        ComPtr<ID3D12Resource> texture;
        ScopedHandle handle;
        ComPtr<ID3D12Resource> readback;
        D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint{};
        std::size_t logicalRowBytes = 0;
    };

    std::uint32_t width = 0;
    std::uint32_t height = 0;
    ComPtr<IDXGIFactory6> factory;
    ComPtr<IDXGIAdapter1> adapter;
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList> commandList;
    SharedTexture color;
    SharedTexture depth;
    SharedTexture motion;
    ComPtr<ID3D12Fence> fence;
    ScopedHandle fenceHandle;
    ScopedHandle completionEvent;
    bool submitted = false;
    std::uint64_t lastSubmittedSignal = 0;
};

std::shared_ptr<LiveD3D12FrameSession> LiveD3D12FrameSession::create(
    std::uint32_t width,
    std::uint32_t height) {
    return std::shared_ptr<LiveD3D12FrameSession>(
        new LiveD3D12FrameSession(width, height));
}

LiveD3D12FrameSession::LiveD3D12FrameSession(
    std::uint32_t width,
    std::uint32_t height)
    : impl_(std::make_unique<Impl>()) {
    if (width == 0 || height == 0 || width > 8192 || height > 8192) {
        throw std::invalid_argument("Frame session dimensions must be within 1..8192.");
    }
    auto& state = *impl_;
    state.width = width;
    state.height = height;

    requireSuccess(CreateDXGIFactory2(0, IID_PPV_ARGS(&state.factory)),
        "CreateDXGIFactory2");
    for (UINT index = 0;; ++index) {
        ComPtr<IDXGIAdapter1> candidate;
        const HRESULT result = state.factory->EnumAdapterByGpuPreference(
            index, DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
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
        if (SUCCEEDED(D3D12CreateDevice(candidate.Get(), D3D_FEATURE_LEVEL_12_0,
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
    requireSuccess(state.device->CreateCommandQueue(
        &queueDescription, IID_PPV_ARGS(&state.queue)), "CreateCommandQueue");
    requireSuccess(state.device->CreateCommandAllocator(
        D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&state.allocator)),
        "CreateCommandAllocator");
    requireSuccess(state.device->CreateCommandList(
        0, D3D12_COMMAND_LIST_TYPE_DIRECT, state.allocator.Get(), nullptr,
        IID_PPV_ARGS(&state.commandList)), "CreateCommandList");

    D3D12_HEAP_PROPERTIES defaultHeap{};
    defaultHeap.Type = D3D12_HEAP_TYPE_DEFAULT;
    defaultHeap.CreationNodeMask = 1;
    defaultHeap.VisibleNodeMask = 1;
    D3D12_HEAP_PROPERTIES readbackHeap{};
    readbackHeap.Type = D3D12_HEAP_TYPE_READBACK;
    readbackHeap.CreationNodeMask = 1;
    readbackHeap.VisibleNodeMask = 1;

    auto createTexture = [&](DXGI_FORMAT format, Impl::SharedTexture& target,
                             const char* label) {
        D3D12_RESOURCE_DESC textureDescription{};
        textureDescription.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
        textureDescription.Width = width;
        textureDescription.Height = height;
        textureDescription.DepthOrArraySize = 1;
        textureDescription.MipLevels = 1;
        textureDescription.Format = format;
        textureDescription.SampleDesc.Count = 1;
        textureDescription.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
        requireSuccess(state.device->CreateCommittedResource(
            &defaultHeap, D3D12_HEAP_FLAG_SHARED, &textureDescription,
            D3D12_RESOURCE_STATE_COMMON, nullptr, IID_PPV_ARGS(&target.texture)),
            label);
        HANDLE sharedHandle = nullptr;
        requireSuccess(state.device->CreateSharedHandle(
            target.texture.Get(), nullptr, GENERIC_ALL, nullptr, &sharedHandle),
            "CreateSharedHandle(frame texture)");
        target.handle.reset(sharedHandle);

        UINT rowCount = 0;
        UINT64 rowSize = 0;
        UINT64 totalBytes = 0;
        state.device->GetCopyableFootprints(
            &textureDescription, 0, 1, 0, &target.footprint,
            &rowCount, &rowSize, &totalBytes);
        target.logicalRowBytes = static_cast<std::size_t>(width) * 4U;
        if (rowCount != height || rowSize != target.logicalRowBytes || totalBytes == 0) {
            throw std::runtime_error("Unexpected frame readback footprint.");
        }
        D3D12_RESOURCE_DESC readbackDescription{};
        readbackDescription.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
        readbackDescription.Width = totalBytes;
        readbackDescription.Height = 1;
        readbackDescription.DepthOrArraySize = 1;
        readbackDescription.MipLevels = 1;
        readbackDescription.SampleDesc.Count = 1;
        readbackDescription.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
        requireSuccess(state.device->CreateCommittedResource(
            &readbackHeap, D3D12_HEAP_FLAG_NONE, &readbackDescription,
            D3D12_RESOURCE_STATE_COPY_DEST, nullptr,
            IID_PPV_ARGS(&target.readback)), "CreateCommittedResource(frame readback)");
    };

    createTexture(DXGI_FORMAT_R8G8B8A8_UNORM, state.color,
        "CreateCommittedResource(frame color)");
    createTexture(DXGI_FORMAT_R32_FLOAT, state.depth,
        "CreateCommittedResource(frame depth)");
    createTexture(DXGI_FORMAT_R16G16_FLOAT, state.motion,
        "CreateCommittedResource(frame motion)");
    requireSuccess(state.device->CreateFence(
        0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&state.fence)),
        "CreateFence(frame)");
    HANDLE fenceHandle = nullptr;
    requireSuccess(state.device->CreateSharedHandle(
        state.fence.Get(), nullptr, GENERIC_ALL, nullptr, &fenceHandle),
        "CreateSharedHandle(frame fence)");
    state.fenceHandle.reset(fenceHandle);
    state.completionEvent.reset(CreateEventW(nullptr, FALSE, FALSE, nullptr));
    if (state.completionEvent.get() == nullptr) {
        throw std::runtime_error("CreateEventW failed for frame completion.");
    }
}

LiveD3D12FrameSession::~LiveD3D12FrameSession() = default;

std::uintptr_t LiveD3D12FrameSession::colorTextureHandle() const noexcept {
    return reinterpret_cast<std::uintptr_t>(impl_->color.handle.get());
}

std::uintptr_t LiveD3D12FrameSession::depthTextureHandle() const noexcept {
    return reinterpret_cast<std::uintptr_t>(impl_->depth.handle.get());
}

std::uintptr_t LiveD3D12FrameSession::motionTextureHandle() const noexcept {
    return reinterpret_cast<std::uintptr_t>(impl_->motion.handle.get());
}

std::uintptr_t LiveD3D12FrameSession::fenceHandle() const noexcept {
    return reinterpret_cast<std::uintptr_t>(impl_->fenceHandle.get());
}

bool LiveD3D12FrameSession::submitReadback(
    std::uint64_t waitValue,
    std::uint64_t signalValue) {
    auto& state = *impl_;
    if (!validPersistentFencePair(state.lastSubmittedSignal, waitValue, signalValue)) {
        return false;
    }
    if (state.lastSubmittedSignal != 0) {
        if (state.fence->GetCompletedValue() < state.lastSubmittedSignal) {
            return false;
        }
        requireSuccess(state.allocator->Reset(), "Frame allocator reset");
        requireSuccess(state.commandList->Reset(state.allocator.Get(), nullptr),
            "Frame command-list reset");
    }
    requireSuccess(state.queue->Wait(state.fence.Get(), waitValue),
        "Frame queue wait");

    ID3D12Resource* textures[] = {
        state.color.texture.Get(), state.depth.texture.Get(), state.motion.texture.Get()};
    D3D12_RESOURCE_BARRIER toCopy[3]{};
    for (std::size_t index = 0; index < 3; ++index) {
        toCopy[index].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        toCopy[index].Transition.pResource = textures[index];
        toCopy[index].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        toCopy[index].Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
        toCopy[index].Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
    }
    state.commandList->ResourceBarrier(3, toCopy);

    auto copyTexture = [&](Impl::SharedTexture& resource) {
        D3D12_TEXTURE_COPY_LOCATION destination{};
        destination.pResource = resource.readback.Get();
        destination.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
        destination.PlacedFootprint = resource.footprint;
        D3D12_TEXTURE_COPY_LOCATION source{};
        source.pResource = resource.texture.Get();
        source.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
        source.SubresourceIndex = 0;
        state.commandList->CopyTextureRegion(
            &destination, 0, 0, 0, &source, nullptr);
    };
    copyTexture(state.color);
    copyTexture(state.depth);
    copyTexture(state.motion);

    D3D12_RESOURCE_BARRIER toCommon[3] = {toCopy[0], toCopy[1], toCopy[2]};
    for (auto& barrier : toCommon) {
        barrier.Transition.StateBefore = D3D12_RESOURCE_STATE_COPY_SOURCE;
        barrier.Transition.StateAfter = D3D12_RESOURCE_STATE_COMMON;
    }
    state.commandList->ResourceBarrier(3, toCommon);
    requireSuccess(state.commandList->Close(), "Frame command-list close");
    ID3D12CommandList* lists[] = {state.commandList.Get()};
    state.queue->ExecuteCommandLists(1, lists);
    requireSuccess(state.queue->Signal(state.fence.Get(), signalValue),
        "Frame queue signal");
    state.submitted = true;
    state.lastSubmittedSignal = signalValue;
    return true;
}

FrameReadbackFingerprint LiveD3D12FrameSession::inspectReadback(
    std::uint64_t signalValue) {
    auto& state = *impl_;
    FrameReadbackFingerprint result{};
    result.completedFenceValue = state.fence->GetCompletedValue();
    result.lastSubmittedSignal = state.lastSubmittedSignal;
    if (!state.submitted || signalValue != state.lastSubmittedSignal
        || signalValue % 2U != 0) {
        result.status = InteropReadbackStatus::invalidRequest;
        return result;
    }
    if (state.fence->GetCompletedValue() < signalValue) {
        requireSuccess(state.fence->SetEventOnCompletion(
            signalValue, state.completionEvent.get()), "Frame SetEventOnCompletion");
        if (WaitForSingleObject(state.completionEvent.get(), 5000) != WAIT_OBJECT_0) {
            result.status = InteropReadbackStatus::timeout;
            result.completedFenceValue = state.fence->GetCompletedValue();
            return result;
        }
    }

    auto mapResource = [&](Impl::SharedTexture& resource, auto fingerprint) {
        void* mapped = nullptr;
        const SIZE_T readEnd = interopReadbackRangeEnd(
            static_cast<SIZE_T>(resource.footprint.Offset),
            static_cast<SIZE_T>(resource.footprint.Footprint.RowPitch),
            resource.logicalRowBytes, state.height);
        D3D12_RANGE readRange{0, readEnd};
        requireSuccess(resource.readback->Map(0, &readRange, &mapped),
            "Frame readback Map");
        const auto* bytes = static_cast<const std::uint8_t*>(mapped)
            + resource.footprint.Offset;
        auto value = fingerprint(bytes, resource.footprint.Footprint.RowPitch,
            state.width, state.height);
        D3D12_RANGE writtenRange{0, 0};
        resource.readback->Unmap(0, &writtenRange);
        return value;
    };
    result.color = mapResource(state.color, fingerprintInteropReadback);
    result.depth = mapResource(state.depth, fingerprintDepthReadback);
    result.motion = mapResource(state.motion,
        [](const std::uint8_t* bytes, std::size_t rowPitch,
           std::uint32_t width, std::uint32_t height) {
            return fingerprintMotionReadback(
                bytes, rowPitch, width, height,
                static_cast<float>(width), static_cast<float>(height));
        });
    result.status = result.color.available && result.depth.available
        && result.motion.available
        ? InteropReadbackStatus::ready
        : InteropReadbackStatus::unavailable;
    result.completedFenceValue = state.fence->GetCompletedValue();
    return result;
}

}
