#include "live_dlss_session.h"
#include "live_fsr3_session.h"

#include <dxgi1_6.h>
#include <wrl/client.h>

#include <sstream>
#include <stdexcept>
#include <chrono>
#include <cmath>
#include <cstring>
#include <mutex>

namespace mc_dlss {
namespace {

using Microsoft::WRL::ComPtr;

std::mutex processDeviceMutex;
ComPtr<IDXGIAdapter1> processAdapter;
ComPtr<ID3D12Device> processDevice;

void requireSuccess(HRESULT result, const char* operation) {
    if (SUCCEEDED(result)) return;
    std::ostringstream message;
    message << operation << " failed with HRESULT 0x" << std::hex
            << std::uppercase << static_cast<unsigned long>(result);
    throw std::runtime_error(message.str());
}

class ScopedHandle {
public:
    ~ScopedHandle() { reset(nullptr); }
    ScopedHandle() = default;
    ScopedHandle(const ScopedHandle&) = delete;
    ScopedHandle& operator=(const ScopedHandle&) = delete;
    HANDLE get() const noexcept { return value_; }
    void reset(HANDLE value) noexcept {
        if (value_ != nullptr && value_ != INVALID_HANDLE_VALUE) CloseHandle(value_);
        value_ = value;
    }
private:
    HANDLE value_ = nullptr;
};

}

struct LiveDlssSession::Impl {
    struct SharedTexture {
        ComPtr<ID3D12Resource> resource;
        ScopedHandle handle;
    };
    struct Slot {
        SharedTexture color;
        SharedTexture depth;
        SharedTexture motion;
        SharedTexture output;
        SharedTexture generated;
        ComPtr<ID3D12Fence> fence;
        ScopedHandle fenceHandle;
        ComPtr<ID3D12CommandAllocator> allocator;
        ComPtr<ID3D12GraphicsCommandList> commandList;
        ComPtr<ID3D12Resource> outputReadback;
        D3D12_PLACED_SUBRESOURCE_FOOTPRINT outputFootprint{};
        std::size_t outputLogicalRowBytes = 0;
        ScopedHandle completionEvent;
        bool submitted = false;
        std::uint64_t lastSubmittedSignal = 0;
        double evaluationMilliseconds = 0.0;
        LiveUpscalerExecutionMode executionMode =
            LiveUpscalerExecutionMode::validating;
        LiveStreamlineStatus evaluationStatus{};
    };

    std::uint32_t renderWidth = 0;
    std::uint32_t renderHeight = 0;
    std::uint32_t outputWidth = 0;
    std::uint32_t outputHeight = 0;
    ComPtr<IDXGIFactory6> factory;
    ComPtr<IDXGIAdapter1> adapter;
    ComPtr<ID3D12Device> device;
    ComPtr<ID3D12CommandQueue> queue;
    std::array<Slot, 2> slots;
    std::unique_ptr<LiveStreamlineRuntime> streamline;
    std::unique_ptr<LiveFsr3Session> fsr3;
    bool fsr3UpscaleLatchedOff = false;
    bool fsr3FrameGenerationLatchedOff = false;
    std::uint64_t fsr3FrameId = 0;
};

std::shared_ptr<LiveDlssSession> LiveDlssSession::create(
    std::uint32_t renderWidth, std::uint32_t renderHeight,
    std::uint32_t outputWidth, std::uint32_t outputHeight) {
    return std::shared_ptr<LiveDlssSession>(new LiveDlssSession(
        renderWidth, renderHeight, outputWidth, outputHeight));
}

LiveDlssSession::LiveDlssSession(
    std::uint32_t renderWidth, std::uint32_t renderHeight,
    std::uint32_t outputWidth, std::uint32_t outputHeight)
    : impl_(std::make_unique<Impl>()) {
    if (renderWidth == 0 || renderHeight == 0 || outputWidth <= renderWidth
        || outputHeight <= renderHeight || outputWidth > 16384 || outputHeight > 16384) {
        throw std::invalid_argument("Live DLSS dimensions are invalid.");
    }
    auto& state = *impl_;
    state.renderWidth = renderWidth;
    state.renderHeight = renderHeight;
    state.outputWidth = outputWidth;
    state.outputHeight = outputHeight;

    requireSuccess(CreateDXGIFactory2(0, IID_PPV_ARGS(&state.factory)),
        "CreateDXGIFactory2(live DLSS)");
    {
        std::lock_guard<std::mutex> lock(processDeviceMutex);
        if (processDevice == nullptr) {
            for (UINT index = 0;; ++index) {
                ComPtr<IDXGIAdapter1> candidate;
                HRESULT result = state.factory->EnumAdapterByGpuPreference(
                    index, DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
                    IID_PPV_ARGS(&candidate));
                if (result == DXGI_ERROR_NOT_FOUND) break;
                requireSuccess(result, "EnumAdapterByGpuPreference(live DLSS)");
                DXGI_ADAPTER_DESC1 description{};
                requireSuccess(candidate->GetDesc1(&description), "GetDesc1(live DLSS)");
                if ((description.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0) continue;
                ComPtr<ID3D12Device> device;
                if (SUCCEEDED(D3D12CreateDevice(
                        candidate.Get(), D3D_FEATURE_LEVEL_12_0,
                        IID_PPV_ARGS(&device)))) {
                    processAdapter = candidate;
                    processDevice = device;
                    break;
                }
            }
        }
        state.adapter = processAdapter;
        state.device = processDevice;
    }
    if (state.device == nullptr) {
        throw std::runtime_error("No hardware D3D12 adapter is available for live DLSS.");
    }

    D3D12_COMMAND_QUEUE_DESC queueDescription{};
    queueDescription.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
    requireSuccess(state.device->CreateCommandQueue(
        &queueDescription, IID_PPV_ARGS(&state.queue)),
        "CreateCommandQueue(live DLSS)");

    D3D12_HEAP_PROPERTIES heap{};
    heap.Type = D3D12_HEAP_TYPE_DEFAULT;
    heap.CreationNodeMask = 1;
    heap.VisibleNodeMask = 1;
    auto createTexture = [&](std::uint32_t width, std::uint32_t height,
                             DXGI_FORMAT format, D3D12_RESOURCE_FLAGS flags,
                             Impl::SharedTexture& target, const char* label) {
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
        requireSuccess(state.device->CreateCommittedResource(
            &heap, D3D12_HEAP_FLAG_SHARED, &description,
            D3D12_RESOURCE_STATE_COMMON, nullptr, IID_PPV_ARGS(&target.resource)), label);
        HANDLE handle = nullptr;
        requireSuccess(state.device->CreateSharedHandle(
            target.resource.Get(), nullptr, GENERIC_ALL, nullptr, &handle),
            "CreateSharedHandle(live DLSS texture)");
        target.handle.reset(handle);
    };

    for (auto& slot : state.slots) {
        createTexture(renderWidth, renderHeight, DXGI_FORMAT_R16G16B16A16_FLOAT,
            D3D12_RESOURCE_FLAG_NONE, slot.color, "Create live DLSS color");
        createTexture(renderWidth, renderHeight, DXGI_FORMAT_R32_FLOAT,
            D3D12_RESOURCE_FLAG_NONE, slot.depth, "Create live DLSS depth");
        createTexture(renderWidth, renderHeight, DXGI_FORMAT_R16G16_FLOAT,
            D3D12_RESOURCE_FLAG_NONE, slot.motion, "Create live DLSS motion");
        createTexture(outputWidth, outputHeight, DXGI_FORMAT_R16G16B16A16_FLOAT,
            D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS, slot.output,
            "Create live DLSS output");
        createTexture(outputWidth, outputHeight, DXGI_FORMAT_R16G16B16A16_FLOAT,
            D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS, slot.generated,
            "Create live FSR3 generated output");
        requireSuccess(state.device->CreateFence(
            0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&slot.fence)),
            "CreateFence(live DLSS)");
        HANDLE fenceHandle = nullptr;
        requireSuccess(state.device->CreateSharedHandle(
            slot.fence.Get(), nullptr, GENERIC_ALL, nullptr, &fenceHandle),
            "CreateSharedHandle(live DLSS fence)");
        slot.fenceHandle.reset(fenceHandle);

        requireSuccess(state.device->CreateCommandAllocator(
            D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&slot.allocator)),
            "CreateCommandAllocator(live DLSS)");
        requireSuccess(state.device->CreateCommandList(
            0, D3D12_COMMAND_LIST_TYPE_DIRECT, slot.allocator.Get(), nullptr,
            IID_PPV_ARGS(&slot.commandList)), "CreateCommandList(live DLSS)");

        const D3D12_RESOURCE_DESC outputDescription = slot.output.resource->GetDesc();
        UINT rowCount = 0;
        UINT64 rowSize = 0;
        UINT64 totalBytes = 0;
        state.device->GetCopyableFootprints(
            &outputDescription, 0, 1, 0, &slot.outputFootprint,
            &rowCount, &rowSize, &totalBytes);
        slot.outputLogicalRowBytes = static_cast<std::size_t>(outputWidth) * 8U;
        if (rowCount != outputHeight || rowSize != slot.outputLogicalRowBytes
            || totalBytes == 0) {
            throw std::runtime_error("Unexpected live DLSS output footprint.");
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
        requireSuccess(state.device->CreateCommittedResource(
            &readbackHeap, D3D12_HEAP_FLAG_NONE, &readbackDescription,
            D3D12_RESOURCE_STATE_COPY_DEST, nullptr,
            IID_PPV_ARGS(&slot.outputReadback)), "Create live DLSS output readback");
        slot.completionEvent.reset(CreateEventW(nullptr, FALSE, FALSE, nullptr));
        if (slot.completionEvent.get() == nullptr) {
            throw std::runtime_error("CreateEventW failed for live DLSS completion.");
        }
    }
}

LiveDlssSession::~LiveDlssSession() {
    if (impl_->streamline != nullptr) impl_->streamline->freeResources();
}

LiveDlssSlotInfo LiveDlssSession::slotInfo(std::size_t index) const noexcept {
    if (index >= impl_->slots.size()) return {};
    const auto& slot = impl_->slots[index];
    return {
        reinterpret_cast<std::uintptr_t>(slot.color.handle.get()),
        reinterpret_cast<std::uintptr_t>(slot.depth.handle.get()),
        reinterpret_cast<std::uintptr_t>(slot.motion.handle.get()),
        reinterpret_cast<std::uintptr_t>(slot.output.handle.get()),
        reinterpret_cast<std::uintptr_t>(slot.fenceHandle.get()),
        reinterpret_cast<std::uintptr_t>(slot.generated.handle.get())};
}

ID3D12Device* LiveDlssSession::device() const noexcept { return impl_->device.Get(); }
LUID LiveDlssSession::adapterLuid() const noexcept {
    DXGI_ADAPTER_DESC1 description{};
    if (impl_->adapter != nullptr) impl_->adapter->GetDesc1(&description);
    return description.AdapterLuid;
}
ID3D12Resource* LiveDlssSession::colorTexture(std::size_t i) const noexcept {
    return i < 2 ? impl_->slots[i].color.resource.Get() : nullptr;
}
ID3D12Resource* LiveDlssSession::depthTexture(std::size_t i) const noexcept {
    return i < 2 ? impl_->slots[i].depth.resource.Get() : nullptr;
}
ID3D12Resource* LiveDlssSession::motionTexture(std::size_t i) const noexcept {
    return i < 2 ? impl_->slots[i].motion.resource.Get() : nullptr;
}
ID3D12Resource* LiveDlssSession::outputTexture(std::size_t i) const noexcept {
    return i < 2 ? impl_->slots[i].output.resource.Get() : nullptr;
}
ID3D12Fence* LiveDlssSession::fence(std::size_t i) const noexcept {
    return i < 2 ? impl_->slots[i].fence.Get() : nullptr;
}

bool LiveDlssSession::slotReady(std::size_t i) const noexcept {
    if (i >= impl_->slots.size()) return false;
    const auto& slot = impl_->slots[i];
    return slot.lastSubmittedSignal == 0
        || slot.fence->GetCompletedValue() >= slot.lastSubmittedSignal;
}

LiveStreamlineStatus LiveDlssSession::initializeStreamline(
    const std::wstring& pluginPath, const std::wstring& logPath,
    LiveDlssQualityMode qualityMode) noexcept {
    auto& state = *impl_;
    if (state.streamline != nullptr) return state.streamline->status();
    state.streamline = LiveStreamlineRuntime::create(
        state.device.Get(), adapterLuid(), state.outputWidth, state.outputHeight,
        pluginPath, logPath, qualityMode);
    if (state.streamline == nullptr) {
        return {false, LiveStreamlineStage::initialize,
            "Live Streamline runtime allocation failed"};
    }
    const auto optimal = state.streamline->optimalSettings();
    if (state.streamline->available()
        && (optimal.renderWidth != state.renderWidth
            || optimal.renderHeight != state.renderHeight)) {
        return {false, LiveStreamlineStage::settings,
            "Live Streamline optimal settings do not match session resources"};
    }
    return state.streamline->status();
}

Fsr3CapabilitySnapshot LiveDlssSession::initializeFsr3(
    const std::wstring& loaderPath) noexcept {
    try {
        auto& state = *impl_;
        if (state.fsr3 == nullptr) {
            state.fsr3 = LiveFsr3Session::create(
                state.device.Get(), state.queue.Get(), loaderPath,
                state.renderWidth, state.renderHeight,
                state.outputWidth, state.outputHeight);
        }
        if (state.fsr3 == nullptr) {
            Fsr3CapabilitySnapshot unavailable{};
            unavailable.message = "Live FSR3 session creation failed";
            return unavailable;
        }
        return state.fsr3->capability();
    } catch (const std::exception& error) {
        Fsr3CapabilitySnapshot unavailable{};
        unavailable.message = error.what();
        return unavailable;
    } catch (...) {
        Fsr3CapabilitySnapshot unavailable{};
        unavailable.message = "Unknown live FSR3 initialization failure";
        return unavailable;
    }
}

DxgiPresentationResult LiveDlssSession::presentFsr3DxgiFrame(
    std::uintptr_t windowHandle, std::size_t slotIndex,
    std::uint64_t signalValue,
    std::uint32_t width, std::uint32_t height) noexcept {
    auto& state = *impl_;
    if (state.fsr3 == nullptr || slotIndex >= state.slots.size() ||
        signalValue == 0 || width != state.outputWidth ||
        height != state.outputHeight) {
        return {DxgiPresentationState::openGlOnly, false};
    }
    auto& slot = state.slots[slotIndex];
    if (!slot.submitted || slot.lastSubmittedSignal != signalValue) {
        return {DxgiPresentationState::openGlOnly, false};
    }
    return state.fsr3->presentRealFrame(
        windowHandle, slot.output.resource.Get(), slot.fence.Get(),
        signalValue, width, height);
}

Fsr3CapabilitySnapshot LiveDlssSession::fsr3Capability() const noexcept {
    if (impl_->fsr3 == nullptr) return {};
    return impl_->fsr3->capability();
}

LiveDlssOptimalSettingsNative LiveDlssSession::optimalSettings() const noexcept {
    return impl_->streamline == nullptr
        ? LiveDlssOptimalSettingsNative{} : impl_->streamline->optimalSettings();
}

bool LiveDlssSession::submitFsr3Upscale(
    std::size_t slotIndex, std::uint64_t waitValue, std::uint64_t signalValue,
    const LiveDlssConstantsData& constants,
    float frameTimeMilliseconds,
    bool generateFrame) noexcept {
    try {
        auto& state = *impl_;
        if (slotIndex >= state.slots.size() || state.fsr3 == nullptr ||
            !state.fsr3->superResolutionReady() || state.fsr3UpscaleLatchedOff ||
            waitValue == 0 || (waitValue & 1U) == 0 ||
            signalValue != waitValue + 1U ||
            !std::isfinite(frameTimeMilliseconds) || frameTimeMilliseconds <= 0.0F) {
            return false;
        }
        auto& slot = state.slots[slotIndex];
        if (slot.lastSubmittedSignal != 0) {
            if (waitValue <= slot.lastSubmittedSignal ||
                slot.fence->GetCompletedValue() < slot.lastSubmittedSignal) return false;
            requireSuccess(slot.allocator->Reset(), "Live FSR3 allocator reset");
            requireSuccess(slot.commandList->Reset(slot.allocator.Get(), nullptr),
                "Live FSR3 command-list reset");
        }
        requireSuccess(state.queue->Wait(slot.fence.Get(), waitValue),
            "Live FSR3 queue wait");

        ID3D12Resource* resources[] = {
            slot.color.resource.Get(), slot.depth.resource.Get(),
            slot.motion.resource.Get(), slot.output.resource.Get()};
        const D3D12_RESOURCE_STATES targetStates[] = {
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
            D3D12_RESOURCE_STATE_UNORDERED_ACCESS};
        D3D12_RESOURCE_BARRIER before[4]{};
        for (std::size_t index = 0; index < 4; ++index) {
            before[index].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            before[index].Transition.pResource = resources[index];
            before[index].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            before[index].Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
            before[index].Transition.StateAfter = targetStates[index];
        }
        slot.commandList->ResourceBarrier(4, before);
        Fsr3UpscaleDispatchInputs inputs{};
        inputs.commandList = slot.commandList.Get();
        inputs.color = resources[0];
        inputs.depth = resources[1];
        inputs.motionVectors = resources[2];
        inputs.output = resources[3];
        inputs.renderWidth = state.renderWidth;
        inputs.renderHeight = state.renderHeight;
        inputs.displayWidth = state.outputWidth;
        inputs.displayHeight = state.outputHeight;
        inputs.jitterX = constants.jitterX;
        inputs.jitterY = constants.jitterY;
        inputs.motionScaleX = constants.motionScaleX;
        inputs.motionScaleY = constants.motionScaleY;
        inputs.frameTimeMilliseconds = frameTimeMilliseconds;
        inputs.cameraNear = constants.cameraNear;
        inputs.cameraFar = constants.cameraFar;
        inputs.verticalFovRadians = constants.cameraFov;
        inputs.reset = constants.reset;
        const unsigned int dispatchCode = state.fsr3->dispatchUpscale(inputs);

        unsigned int frameGenerationCode = 0;
        if (dispatchCode == 0 && generateFrame &&
            state.fsr3->frameGenerationReady() &&
            !state.fsr3FrameGenerationLatchedOff) {
            D3D12_RESOURCE_BARRIER fgBarriers[2]{};
            fgBarriers[0].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            fgBarriers[0].Transition.pResource = slot.output.resource.Get();
            fgBarriers[0].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            fgBarriers[0].Transition.StateBefore = D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
            fgBarriers[0].Transition.StateAfter =
                D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
            fgBarriers[1].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            fgBarriers[1].Transition.pResource = slot.generated.resource.Get();
            fgBarriers[1].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            fgBarriers[1].Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
            fgBarriers[1].Transition.StateAfter = D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
            slot.commandList->ResourceBarrier(2, fgBarriers);

            Fsr3FrameGenerationDispatchInputs fg{};
            fg.commandList = slot.commandList.Get();
            fg.depth = slot.depth.resource.Get();
            fg.motionVectors = slot.motion.resource.Get();
            fg.presentColor = slot.output.resource.Get();
            fg.hudlessColor = slot.output.resource.Get();
            fg.generatedOutput = slot.generated.resource.Get();
            fg.renderWidth = state.renderWidth;
            fg.renderHeight = state.renderHeight;
            fg.displayWidth = state.outputWidth;
            fg.displayHeight = state.outputHeight;
            fg.jitterX = constants.jitterX;
            fg.jitterY = constants.jitterY;
            fg.motionScaleX = constants.motionScaleX;
            fg.motionScaleY = constants.motionScaleY;
            fg.frameTimeMilliseconds = frameTimeMilliseconds;
            fg.cameraNear = constants.cameraNear;
            fg.cameraFar = constants.cameraFar;
            fg.verticalFovRadians = constants.cameraFov;
            std::copy_n(constants.cameraPosition.data(), 3, fg.cameraPosition);
            std::copy_n(constants.cameraUp.data(), 3, fg.cameraUp);
            std::copy_n(constants.cameraRight.data(), 3, fg.cameraRight);
            std::copy_n(constants.cameraForward.data(), 3, fg.cameraForward);
            fg.frameId = ++state.fsr3FrameId;
            fg.reset = constants.reset;
            frameGenerationCode = state.fsr3->dispatchFrameGeneration(fg);
            if (frameGenerationCode != 0) state.fsr3FrameGenerationLatchedOff = true;

            fgBarriers[0].Transition.StateBefore =
                D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
            fgBarriers[0].Transition.StateAfter = D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
            fgBarriers[1].Transition.StateBefore = D3D12_RESOURCE_STATE_UNORDERED_ACCESS;
            fgBarriers[1].Transition.StateAfter = D3D12_RESOURCE_STATE_COMMON;
            slot.commandList->ResourceBarrier(2, fgBarriers);
        }

        D3D12_RESOURCE_BARRIER after[4]{};
        for (std::size_t index = 0; index < 4; ++index) {
            after[index] = before[index];
            after[index].Transition.StateBefore = targetStates[index];
            after[index].Transition.StateAfter = D3D12_RESOURCE_STATE_COMMON;
        }
        slot.commandList->ResourceBarrier(4, after);
        requireSuccess(slot.commandList->Close(), "Live FSR3 command-list close");
        ID3D12CommandList* lists[] = {slot.commandList.Get()};
        state.queue->ExecuteCommandLists(1, lists);
        requireSuccess(state.queue->Signal(slot.fence.Get(), signalValue),
            "Live FSR3 queue signal");
        slot.submitted = true;
        slot.lastSubmittedSignal = signalValue;
        if (dispatchCode != 0) state.fsr3UpscaleLatchedOff = true;
        return dispatchCode == 0 && frameGenerationCode == 0;
    } catch (...) {
        impl_->fsr3UpscaleLatchedOff = true;
        return false;
    }
}

bool LiveDlssSession::submitEvaluation(
    std::size_t slotIndex, std::uint64_t waitValue, std::uint64_t signalValue,
    const LiveDlssConstantsData& constants,
    LiveUpscalerExecutionMode mode) noexcept {
    try {
        auto& state = *impl_;
        if (slotIndex >= state.slots.size() || state.streamline == nullptr
            || !state.streamline->available() || waitValue == 0
            || (waitValue & 1U) == 0 || signalValue != waitValue + 1U) {
            return false;
        }
        auto& slot = state.slots[slotIndex];
        if (slot.lastSubmittedSignal != 0) {
            if (waitValue <= slot.lastSubmittedSignal
                || slot.fence->GetCompletedValue() < slot.lastSubmittedSignal) {
                return false;
            }
            requireSuccess(slot.allocator->Reset(), "Live DLSS allocator reset");
            requireSuccess(slot.commandList->Reset(slot.allocator.Get(), nullptr),
                "Live DLSS command-list reset");
        }
        const auto started = std::chrono::steady_clock::now();
        requireSuccess(state.queue->Wait(slot.fence.Get(), waitValue),
            "Live DLSS queue wait");

        ID3D12Resource* resources[] = {
            slot.color.resource.Get(), slot.depth.resource.Get(),
            slot.motion.resource.Get(), slot.output.resource.Get()};
        const D3D12_RESOURCE_STATES targetStates[] = {
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
            D3D12_RESOURCE_STATE_UNORDERED_ACCESS};
        D3D12_RESOURCE_BARRIER toEvaluate[4]{};
        for (std::size_t index = 0; index < 4; ++index) {
            toEvaluate[index].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
            toEvaluate[index].Transition.pResource = resources[index];
            toEvaluate[index].Transition.Subresource =
                D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
            toEvaluate[index].Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
            toEvaluate[index].Transition.StateAfter = targetStates[index];
        }
        slot.commandList->ResourceBarrier(4, toEvaluate);
        const LiveDlssEvaluationResources evaluationResources{
            resources[0], resources[1], resources[2], resources[3],
            slot.commandList.Get(), state.renderWidth, state.renderHeight,
            state.outputWidth, state.outputHeight};
        slot.evaluationStatus = state.streamline->evaluate(
            evaluationResources, constants);
        if (!slot.evaluationStatus.success) {
            slot.commandList->Close();
            return false;
        }

        D3D12_RESOURCE_BARRIER afterEvaluate[4]{};
        for (std::size_t index = 0; index < 4; ++index) {
            afterEvaluate[index] = toEvaluate[index];
            afterEvaluate[index].Transition.StateBefore = targetStates[index];
            afterEvaluate[index].Transition.StateAfter =
                index == 3 && mode == LiveUpscalerExecutionMode::validating
                    ? D3D12_RESOURCE_STATE_COPY_SOURCE
                    : D3D12_RESOURCE_STATE_COMMON;
        }
        slot.commandList->ResourceBarrier(4, afterEvaluate);
        if (mode == LiveUpscalerExecutionMode::validating) {
            D3D12_TEXTURE_COPY_LOCATION destination{};
            destination.pResource = slot.outputReadback.Get();
            destination.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
            destination.PlacedFootprint = slot.outputFootprint;
            D3D12_TEXTURE_COPY_LOCATION source{};
            source.pResource = slot.output.resource.Get();
            source.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
            source.SubresourceIndex = 0;
            slot.commandList->CopyTextureRegion(
                &destination, 0, 0, 0, &source, nullptr);
            D3D12_RESOURCE_BARRIER outputToCommon = afterEvaluate[3];
            outputToCommon.Transition.StateBefore =
                D3D12_RESOURCE_STATE_COPY_SOURCE;
            outputToCommon.Transition.StateAfter = D3D12_RESOURCE_STATE_COMMON;
            slot.commandList->ResourceBarrier(1, &outputToCommon);
        }
        requireSuccess(slot.commandList->Close(), "Live DLSS command-list close");
        ID3D12CommandList* lists[] = {slot.commandList.Get()};
        state.queue->ExecuteCommandLists(1, lists);
        requireSuccess(state.queue->Signal(slot.fence.Get(), signalValue),
            "Live DLSS queue signal");
        slot.submitted = true;
        slot.lastSubmittedSignal = signalValue;
        slot.executionMode = mode;
        slot.evaluationMilliseconds = std::chrono::duration<double, std::milli>(
            std::chrono::steady_clock::now() - started).count();
        return true;
    } catch (...) {
        return false;
    }
}

LiveDlssFrameInspection LiveDlssSession::inspectEvaluation(
    std::size_t slotIndex, std::uint64_t signalValue) noexcept {
    LiveDlssFrameInspection result{};
    try {
        auto& state = *impl_;
        if (slotIndex >= state.slots.size()) {
            result.message = "Invalid live DLSS slot";
            return result;
        }
        auto& slot = state.slots[slotIndex];
        result.diagnosticReadbackRequested =
            slot.executionMode == LiveUpscalerExecutionMode::validating;
        result.stage = slot.evaluationStatus.stage;
        result.completedFenceValue = slot.fence->GetCompletedValue();
        result.lastSubmittedSignal = slot.lastSubmittedSignal;
        result.evaluationMilliseconds = slot.evaluationMilliseconds;
        if (!slot.submitted || signalValue != slot.lastSubmittedSignal
            || (signalValue & 1U) != 0) {
            result.message = "Invalid live DLSS inspection request";
            return result;
        }
        if (slot.fence->GetCompletedValue() < signalValue) {
            requireSuccess(slot.fence->SetEventOnCompletion(
                signalValue, slot.completionEvent.get()),
                "Live DLSS SetEventOnCompletion");
            if (WaitForSingleObject(slot.completionEvent.get(), 5000) != WAIT_OBJECT_0) {
                result.message = "Live DLSS output wait timed out";
                return result;
            }
        }
        if (!result.diagnosticReadbackRequested) {
            result.completedFenceValue = slot.fence->GetCompletedValue();
            result.message = "Live upscaler fast evaluation completed without readback";
            return result;
        }
        const SIZE_T readEnd = static_cast<SIZE_T>(slot.outputFootprint.Offset)
            + static_cast<SIZE_T>(state.outputHeight - 1U)
                * slot.outputFootprint.Footprint.RowPitch
            + slot.outputLogicalRowBytes;
        D3D12_RANGE range{0, readEnd};
        void* mapped = nullptr;
        requireSuccess(slot.outputReadback->Map(0, &range, &mapped),
            "Live DLSS output Map");
        const auto* base = static_cast<const std::uint8_t*>(mapped)
            + slot.outputFootprint.Offset;
        constexpr std::uint64_t offsetBasis = 0xcbf29ce484222325ULL;
        constexpr std::uint64_t prime = 0x100000001b3ULL;
        result.outputHash = offsetBasis;
        std::uint64_t firstPixel = 0;
        std::memcpy(&firstPixel, base, sizeof(firstPixel));
        for (std::uint32_t y = 0; y < state.outputHeight; ++y) {
            const auto* row = base + static_cast<std::size_t>(y)
                * slot.outputFootprint.Footprint.RowPitch;
            for (std::uint32_t x = 0; x < state.outputWidth; ++x) {
                const auto* pixel = row + static_cast<std::size_t>(x) * 8U;
                std::uint64_t bits = 0;
                std::memcpy(&bits, pixel, sizeof(bits));
                result.outputNonUniform = result.outputNonUniform || bits != firstPixel;
                std::uint16_t channels[4]{};
                std::memcpy(channels, pixel, sizeof(channels));
                if ((channels[0] & 0x7fffU) != 0 || (channels[1] & 0x7fffU) != 0
                    || (channels[2] & 0x7fffU) != 0) {
                    ++result.outputNonBlackPixelCount;
                }
                for (std::size_t byte = 0; byte < 8; ++byte) {
                    result.outputHash ^= pixel[byte];
                    result.outputHash *= prime;
                }
            }
        }
        D3D12_RANGE written{0, 0};
        slot.outputReadback->Unmap(0, &written);
        result.available = result.outputNonUniform
            && result.outputNonBlackPixelCount > 0;
        result.completedFenceValue = slot.fence->GetCompletedValue();
        result.message = result.available
            ? "Live DLSS output fingerprint ready"
            : "Live DLSS output is uniform or black";
    } catch (const std::exception& error) {
        result.message = error.what();
    } catch (...) {
        result.message = "Unknown live DLSS inspection failure";
    }
    return result;
}

LiveStreamlineStatus LiveDlssSession::freeStreamlineResources() noexcept {
    return impl_->streamline == nullptr
        ? LiveStreamlineStatus{false, LiveStreamlineStage::freeResources,
            "Live Streamline runtime is unavailable"}
        : impl_->streamline->freeResources();
}

}
