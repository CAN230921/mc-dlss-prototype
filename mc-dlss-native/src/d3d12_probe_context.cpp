#include "d3d12_probe_context.h"

#include <dxgi1_6.h>
#include <wrl/client.h>

#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>

namespace mc_dlss {
namespace {

using Microsoft::WRL::ComPtr;

class ScopedHandle {
public:
    explicit ScopedHandle(HANDLE handle) noexcept : handle_(handle) {}
    ~ScopedHandle() {
        if (handle_ != nullptr) {
            CloseHandle(handle_);
        }
    }

    ScopedHandle(const ScopedHandle&) = delete;
    ScopedHandle& operator=(const ScopedHandle&) = delete;

    HANDLE get() const noexcept { return handle_; }

private:
    HANDLE handle_;
};

void requireSuccess(HRESULT result, const char* operation) {
    if (SUCCEEDED(result)) {
        return;
    }

    std::ostringstream message;
    message << operation << " failed with HRESULT 0x"
            << std::hex << std::uppercase << static_cast<unsigned long>(result);
    throw std::runtime_error(message.str());
}

std::string toUtf8(const wchar_t* value) {
    if (value == nullptr || value[0] == L'\0') {
        return {};
    }

    const int required = WideCharToMultiByte(
        CP_UTF8, 0, value, -1, nullptr, 0, nullptr, nullptr);
    if (required <= 1) {
        return {};
    }

    std::string converted(static_cast<std::size_t>(required), '\0');
    const int written = WideCharToMultiByte(
        CP_UTF8, 0, value, -1, converted.data(), required, nullptr, nullptr);
    if (written != required) {
        throw std::runtime_error("Failed to convert the adapter name to UTF-8.");
    }
    converted.resize(static_cast<std::size_t>(required - 1));
    return converted;
}

D3D12_RESOURCE_DESC textureDescription(
    std::uint32_t width,
    std::uint32_t height,
    DXGI_FORMAT format,
    D3D12_RESOURCE_FLAGS flags) noexcept {
    D3D12_RESOURCE_DESC description{};
    description.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
    description.Width = width;
    description.Height = height;
    description.DepthOrArraySize = 1;
    description.MipLevels = 1;
    description.Format = format;
    description.SampleDesc.Count = 1;
    description.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;
    description.Flags = flags;
    return description;
}

ComPtr<ID3D12Resource> createTexture(
    ID3D12Device* device,
    std::uint32_t width,
    std::uint32_t height,
    DXGI_FORMAT format,
    D3D12_RESOURCE_FLAGS flags,
    D3D12_RESOURCE_STATES initialState,
    const D3D12_CLEAR_VALUE* clearValue) {
    D3D12_HEAP_PROPERTIES heapProperties{};
    heapProperties.Type = D3D12_HEAP_TYPE_DEFAULT;
    heapProperties.CreationNodeMask = 1;
    heapProperties.VisibleNodeMask = 1;

    const auto description = textureDescription(width, height, format, flags);
    ComPtr<ID3D12Resource> resource;
    requireSuccess(
        device->CreateCommittedResource(
            &heapProperties,
            D3D12_HEAP_FLAG_NONE,
            &description,
            initialState,
            clearValue,
            IID_PPV_ARGS(&resource)),
        "ID3D12Device::CreateCommittedResource");
    return resource;
}

bool resourceMatches(
    ID3D12Resource* resource,
    std::uint32_t width,
    std::uint32_t height,
    DXGI_FORMAT format,
    D3D12_RESOURCE_FLAGS requiredFlags) noexcept {
    if (resource == nullptr) {
        return false;
    }
    const auto description = resource->GetDesc();
    return description.Dimension == D3D12_RESOURCE_DIMENSION_TEXTURE2D &&
        description.Width == width &&
        description.Height == height &&
        description.DepthOrArraySize == 1 &&
        description.MipLevels == 1 &&
        description.Format == format &&
        description.SampleDesc.Count == 1 &&
        (description.Flags & requiredFlags) == requiredFlags;
}

}

struct D3D12ProbeContext::Impl {
    ProbeSnapshot snapshot{};
    LUID adapterLuid{};
    ComPtr<ID3D12Debug> debugController;
    ComPtr<IDXGIFactory6> factory;
    ComPtr<IDXGIAdapter1> adapter;
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> commandQueue;
    ComPtr<ID3D12CommandAllocator> commandAllocator;
    ComPtr<ID3D12GraphicsCommandList> commandList;
    ComPtr<ID3D12Fence> fence;
    std::unique_ptr<ScopedHandle> fenceEvent;
    ComPtr<ID3D12Resource> inputColor;
    ComPtr<ID3D12Resource> depth;
    ComPtr<ID3D12Resource> motionVectors;
    ComPtr<ID3D12Resource> outputColor;
    ComPtr<ID3D12DescriptorHeap> rtvHeap;
    ComPtr<ID3D12DescriptorHeap> dsvHeap;
    bool submitted = false;
};

std::unique_ptr<D3D12ProbeContext> D3D12ProbeContext::create(
    std::uint32_t inputWidth,
    std::uint32_t inputHeight,
    std::uint32_t outputWidth,
    std::uint32_t outputHeight) {
    return std::unique_ptr<D3D12ProbeContext>(new D3D12ProbeContext(
        inputWidth, inputHeight, outputWidth, outputHeight));
}

D3D12ProbeContext::D3D12ProbeContext(
    std::uint32_t inputWidth,
    std::uint32_t inputHeight,
    std::uint32_t outputWidth,
    std::uint32_t outputHeight)
    : impl_(std::make_unique<Impl>()) {
    auto& state = *impl_;
    state.snapshot.inputWidth = inputWidth;
    state.snapshot.inputHeight = inputHeight;
    state.snapshot.outputWidth = outputWidth;
    state.snapshot.outputHeight = outputHeight;

    if (inputWidth == 0 || inputHeight == 0 ||
        outputWidth == 0 || outputHeight == 0 ||
        (inputWidth == outputWidth && inputHeight == outputHeight)) {
        throw std::invalid_argument(
            "Input and output dimensions must be non-zero and different.");
    }

    UINT factoryFlags = 0;
    if (SUCCEEDED(D3D12GetDebugInterface(IID_PPV_ARGS(&state.debugController)))) {
        state.debugController->EnableDebugLayer();
        state.snapshot.debugLayerAvailable = true;
        factoryFlags |= DXGI_CREATE_FACTORY_DEBUG;
    }

    requireSuccess(
        CreateDXGIFactory2(factoryFlags, IID_PPV_ARGS(&state.factory)),
        "CreateDXGIFactory2");

    DXGI_ADAPTER_DESC1 adapterDescription{};
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

        DXGI_ADAPTER_DESC1 candidateDescription{};
        requireSuccess(candidate->GetDesc1(&candidateDescription), "IDXGIAdapter1::GetDesc1");
        if ((candidateDescription.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0) {
            continue;
        }

        ComPtr<ID3D12Device> candidateDevice;
        if (SUCCEEDED(D3D12CreateDevice(
                candidate.Get(), D3D_FEATURE_LEVEL_12_0,
                IID_PPV_ARGS(&candidateDevice)))) {
            state.adapter = candidate;
            state.device = candidateDevice;
            adapterDescription = candidateDescription;
            break;
        }
    }

    if (state.adapter == nullptr || state.device == nullptr) {
        throw std::runtime_error("No hardware D3D12 adapter is available.");
    }

    state.adapterLuid = adapterDescription.AdapterLuid;
    state.snapshot.adapterName = toUtf8(adapterDescription.Description);
    state.snapshot.vendorId = adapterDescription.VendorId;
    state.snapshot.softwareAdapter =
        (adapterDescription.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0;
    state.snapshot.deviceAvailable = true;

    D3D12_COMMAND_QUEUE_DESC queueDescription{};
    queueDescription.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
    queueDescription.Priority = D3D12_COMMAND_QUEUE_PRIORITY_NORMAL;
    requireSuccess(
        state.device->CreateCommandQueue(
            &queueDescription, IID_PPV_ARGS(&state.commandQueue)),
        "ID3D12Device::CreateCommandQueue");
    requireSuccess(
        state.device->CreateCommandAllocator(
            D3D12_COMMAND_LIST_TYPE_DIRECT,
            IID_PPV_ARGS(&state.commandAllocator)),
        "ID3D12Device::CreateCommandAllocator");
    requireSuccess(
        state.device->CreateCommandList(
            0, D3D12_COMMAND_LIST_TYPE_DIRECT,
            state.commandAllocator.Get(), nullptr,
            IID_PPV_ARGS(&state.commandList)),
        "ID3D12Device::CreateCommandList");
    requireSuccess(
        state.device->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&state.fence)),
        "ID3D12Device::CreateFence");

    state.fenceEvent = std::make_unique<ScopedHandle>(
        CreateEventW(nullptr, FALSE, FALSE, nullptr));
    if (state.fenceEvent->get() == nullptr) {
        throw std::runtime_error("CreateEventW failed for the D3D12 fence.");
    }
    state.snapshot.commandContextAvailable = true;

    D3D12_CLEAR_VALUE colorClear{};
    colorClear.Format = DXGI_FORMAT_R16G16B16A16_FLOAT;
    colorClear.Color[0] = 0.05F;
    colorClear.Color[1] = 0.10F;
    colorClear.Color[2] = 0.15F;
    colorClear.Color[3] = 1.0F;

    D3D12_CLEAR_VALUE motionClear{};
    motionClear.Format = DXGI_FORMAT_R16G16_FLOAT;

    D3D12_CLEAR_VALUE depthClear{};
    depthClear.Format = DXGI_FORMAT_D32_FLOAT;
    depthClear.DepthStencil.Depth = 1.0F;

    state.inputColor = createTexture(
        state.device.Get(), inputWidth, inputHeight,
        DXGI_FORMAT_R16G16B16A16_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET,
        D3D12_RESOURCE_STATE_RENDER_TARGET, &colorClear);
    state.depth = createTexture(
        state.device.Get(), inputWidth, inputHeight,
        DXGI_FORMAT_D32_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_DEPTH_STENCIL,
        D3D12_RESOURCE_STATE_DEPTH_WRITE, &depthClear);
    state.motionVectors = createTexture(
        state.device.Get(), inputWidth, inputHeight,
        DXGI_FORMAT_R16G16_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET,
        D3D12_RESOURCE_STATE_RENDER_TARGET, &motionClear);
    state.outputColor = createTexture(
        state.device.Get(), outputWidth, outputHeight,
        DXGI_FORMAT_R16G16B16A16_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS,
        D3D12_RESOURCE_STATE_UNORDERED_ACCESS, nullptr);

    state.snapshot.inputColor = resourceMatches(
        state.inputColor.Get(), inputWidth, inputHeight,
        DXGI_FORMAT_R16G16B16A16_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET);
    state.snapshot.depth = resourceMatches(
        state.depth.Get(), inputWidth, inputHeight,
        DXGI_FORMAT_D32_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_DEPTH_STENCIL);
    state.snapshot.motionVectors = resourceMatches(
        state.motionVectors.Get(), inputWidth, inputHeight,
        DXGI_FORMAT_R16G16_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET);
    state.snapshot.outputColor = resourceMatches(
        state.outputColor.Get(), outputWidth, outputHeight,
        DXGI_FORMAT_R16G16B16A16_FLOAT,
        D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS);

    D3D12_DESCRIPTOR_HEAP_DESC rtvHeapDescription{};
    rtvHeapDescription.Type = D3D12_DESCRIPTOR_HEAP_TYPE_RTV;
    rtvHeapDescription.NumDescriptors = 2;
    requireSuccess(
        state.device->CreateDescriptorHeap(
            &rtvHeapDescription, IID_PPV_ARGS(&state.rtvHeap)),
        "ID3D12Device::CreateDescriptorHeap(RTV)");

    D3D12_DESCRIPTOR_HEAP_DESC dsvHeapDescription{};
    dsvHeapDescription.Type = D3D12_DESCRIPTOR_HEAP_TYPE_DSV;
    dsvHeapDescription.NumDescriptors = 1;
    requireSuccess(
        state.device->CreateDescriptorHeap(
            &dsvHeapDescription, IID_PPV_ARGS(&state.dsvHeap)),
        "ID3D12Device::CreateDescriptorHeap(DSV)");

    const UINT rtvIncrement = state.device->GetDescriptorHandleIncrementSize(
        D3D12_DESCRIPTOR_HEAP_TYPE_RTV);
    D3D12_CPU_DESCRIPTOR_HANDLE inputColorRtv =
        state.rtvHeap->GetCPUDescriptorHandleForHeapStart();
    D3D12_CPU_DESCRIPTOR_HANDLE motionVectorsRtv = inputColorRtv;
    motionVectorsRtv.ptr += rtvIncrement;
    const D3D12_CPU_DESCRIPTOR_HANDLE depthDsv =
        state.dsvHeap->GetCPUDescriptorHandleForHeapStart();

    state.device->CreateRenderTargetView(state.inputColor.Get(), nullptr, inputColorRtv);
    state.device->CreateRenderTargetView(
        state.motionVectors.Get(), nullptr, motionVectorsRtv);
    state.device->CreateDepthStencilView(state.depth.Get(), nullptr, depthDsv);

    state.commandList->ClearRenderTargetView(
        inputColorRtv, colorClear.Color, 0, nullptr);
    state.commandList->ClearRenderTargetView(
        motionVectorsRtv, motionClear.Color, 0, nullptr);
    state.commandList->ClearDepthStencilView(
        depthDsv, D3D12_CLEAR_FLAG_DEPTH, 1.0F, 0, 0, nullptr);
}

D3D12ProbeContext::~D3D12ProbeContext() = default;

ID3D12Device* D3D12ProbeContext::device() const noexcept { return impl_->device.Get(); }
IDXGIFactory6* D3D12ProbeContext::factory() const noexcept {
    return impl_->factory.Get();
}
ID3D12CommandQueue* D3D12ProbeContext::commandQueue() const noexcept {
    return impl_->commandQueue.Get();
}
ID3D12GraphicsCommandList* D3D12ProbeContext::commandList() const noexcept {
    return impl_->commandList.Get();
}
ID3D12Resource* D3D12ProbeContext::inputColor() const noexcept {
    return impl_->inputColor.Get();
}
ID3D12Resource* D3D12ProbeContext::depth() const noexcept { return impl_->depth.Get(); }
ID3D12Resource* D3D12ProbeContext::motionVectors() const noexcept {
    return impl_->motionVectors.Get();
}
ID3D12Resource* D3D12ProbeContext::outputColor() const noexcept {
    return impl_->outputColor.Get();
}
LUID D3D12ProbeContext::adapterLuid() const noexcept { return impl_->adapterLuid; }

std::uint32_t D3D12ProbeContext::inputColorState() const noexcept {
    return D3D12_RESOURCE_STATE_RENDER_TARGET;
}
std::uint32_t D3D12ProbeContext::depthState() const noexcept {
    return D3D12_RESOURCE_STATE_DEPTH_WRITE;
}
std::uint32_t D3D12ProbeContext::motionVectorsState() const noexcept {
    return D3D12_RESOURCE_STATE_RENDER_TARGET;
}
std::uint32_t D3D12ProbeContext::outputColorState() const noexcept {
    return D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
}

ProbeSnapshot D3D12ProbeContext::snapshot() const { return impl_->snapshot; }

bool D3D12ProbeContext::submitAndWait() {
    auto& state = *impl_;
    if (state.submitted) {
        return state.snapshot.commandSubmissionCompleted;
    }

    requireSuccess(state.commandList->Close(), "ID3D12GraphicsCommandList::Close");
    ID3D12CommandList* commandLists[] = {state.commandList.Get()};
    state.commandQueue->ExecuteCommandLists(1, commandLists);

    constexpr UINT64 fenceValue = 1;
    requireSuccess(
        state.commandQueue->Signal(state.fence.Get(), fenceValue),
        "ID3D12CommandQueue::Signal");
    if (state.fence->GetCompletedValue() < fenceValue) {
        requireSuccess(
            state.fence->SetEventOnCompletion(fenceValue, state.fenceEvent->get()),
            "ID3D12Fence::SetEventOnCompletion");
        if (WaitForSingleObject(state.fenceEvent->get(), 10000) != WAIT_OBJECT_0) {
            throw std::runtime_error("Timed out waiting for D3D12 command completion.");
        }
    }

    state.submitted = true;
    state.snapshot.commandSubmissionCompleted =
        state.fence->GetCompletedValue() >= fenceValue;
    return state.snapshot.commandSubmissionCompleted;
}

}
