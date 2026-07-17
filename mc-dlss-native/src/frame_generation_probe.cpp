#include "frame_generation_probe.h"

#include "d3d12_probe_context.h"
#include "streamline_probe_report.h"

#include <sl.h>
#include <sl_dlss_g.h>
#include <sl_pcl.h>
#include <sl_reflex.h>

#include <wrl/client.h>

#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>

namespace mc_dlss {
namespace {

using Microsoft::WRL::ComPtr;

constexpr std::uint32_t kRenderWidth = 1280;
constexpr std::uint32_t kRenderHeight = 720;
constexpr std::uint32_t kOutputWidth = 1920;
constexpr std::uint32_t kOutputHeight = 1080;

std::string resultName(sl::Result result) {
    switch (result) {
    case sl::Result::eOk:
        return "eOk";
    case sl::Result::eErrorInvalidIntegration:
        return "eErrorInvalidIntegration";
    case sl::Result::eErrorFeatureNotSupported:
        return "eErrorFeatureNotSupported";
    case sl::Result::eErrorFeatureFailedToLoad:
        return "eErrorFeatureFailedToLoad";
    default:
        return "result(" + std::to_string(static_cast<int>(result)) + ")";
    }
}

void requireSl(sl::Result result, const char* operation) {
    if (result != sl::Result::eOk) {
        throw std::runtime_error(
            std::string(operation) + " failed with " + resultName(result) + '.');
    }
}

void requireHr(HRESULT result, const char* operation) {
    if (FAILED(result)) {
        throw std::runtime_error(std::string(operation) + " failed.");
    }
}

sl::float4x4 identityMatrix() {
    sl::float4x4 matrix{};
    matrix.setRow(0, {1.0F, 0.0F, 0.0F, 0.0F});
    matrix.setRow(1, {0.0F, 1.0F, 0.0F, 0.0F});
    matrix.setRow(2, {0.0F, 0.0F, 1.0F, 0.0F});
    matrix.setRow(3, {0.0F, 0.0F, 0.0F, 1.0F});
    return matrix;
}

sl::Constants frameConstants(bool reset) {
    sl::Constants constants{};
    constants.cameraViewToClip = identityMatrix();
    constants.clipToCameraView = identityMatrix();
    constants.clipToLensClip = identityMatrix();
    constants.clipToPrevClip = identityMatrix();
    constants.prevClipToClip = identityMatrix();
    constants.jitterOffset = {0.0F, 0.0F};
    constants.mvecScale = {
        1.0F / static_cast<float>(kRenderWidth),
        1.0F / static_cast<float>(kRenderHeight)};
    constants.cameraPinholeOffset = {0.0F, 0.0F};
    constants.cameraPos = {0.0F, 0.0F, 0.0F};
    constants.cameraUp = {0.0F, 1.0F, 0.0F};
    constants.cameraRight = {1.0F, 0.0F, 0.0F};
    constants.cameraFwd = {0.0F, 0.0F, 1.0F};
    constants.cameraNear = 0.1F;
    constants.cameraFar = 1000.0F;
    constants.cameraFOV = 1.0471976F;
    constants.cameraAspectRatio =
        static_cast<float>(kOutputWidth) / static_cast<float>(kOutputHeight);
    constants.cameraMotionIncluded = sl::Boolean::eTrue;
    constants.depthInverted = sl::Boolean::eFalse;
    constants.motionVectors3D = sl::Boolean::eFalse;
    constants.motionVectorsDilated = sl::Boolean::eFalse;
    constants.motionVectorsJittered = sl::Boolean::eFalse;
    constants.orthographicProjection = sl::Boolean::eFalse;
    constants.reset = reset ? sl::Boolean::eTrue : sl::Boolean::eFalse;
    return constants;
}

class HiddenSwapChain {
public:
    HiddenSwapChain(IDXGIFactory6* factory, ID3D12CommandQueue* queue) {
        WNDCLASSW windowClass{};
        windowClass.lpfnWndProc = DefWindowProcW;
        windowClass.hInstance = GetModuleHandleW(nullptr);
        windowClass.lpszClassName = L"McDlssFrameGenerationProbe";
        classAtom_ = RegisterClassW(&windowClass);
        if (classAtom_ == 0 && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
            throw std::runtime_error("RegisterClassW failed.");
        }
        window_ = CreateWindowExW(
            0, windowClass.lpszClassName, L"MC DLSS FG Probe", WS_OVERLAPPED,
            -32000, -32000, kOutputWidth, kOutputHeight, nullptr, nullptr,
            windowClass.hInstance, nullptr);
        if (window_ == nullptr) throw std::runtime_error("CreateWindowExW failed.");
        ShowWindow(window_, SW_SHOWNOACTIVATE);
        UpdateWindow(window_);

        DXGI_SWAP_CHAIN_DESC1 description{};
        description.Width = kOutputWidth;
        description.Height = kOutputHeight;
        description.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
        description.SampleDesc.Count = 1;
        description.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
        description.BufferCount = 3;
        description.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
        ComPtr<IDXGISwapChain1> swapChain1;
        requireHr(
            factory->CreateSwapChainForHwnd(
                queue, window_, &description, nullptr, nullptr, &swapChain1),
            "CreateSwapChainForHwnd");
        requireHr(swapChain1.As(&swapChain_), "Query IDXGISwapChain3");
    }

    ~HiddenSwapChain() {
        swapChain_.Reset();
        if (window_ != nullptr) DestroyWindow(window_);
        if (classAtom_ != 0) {
            UnregisterClassW(
                L"McDlssFrameGenerationProbe", GetModuleHandleW(nullptr));
        }
    }

    IDXGISwapChain3* get() const noexcept { return swapChain_.Get(); }

private:
    ATOM classAtom_{};
    HWND window_{};
    ComPtr<IDXGISwapChain3> swapChain_;
};

}

FrameGenerationProbeSnapshot runFrameGenerationProbe(
    const std::wstring& pluginPath,
    const std::wstring& logPath) noexcept {
    FrameGenerationProbeSnapshot snapshot{};
    bool initialized = false;
    std::unique_ptr<D3D12ProbeContext> context;

    try {
        const sl::Feature features[] = {
            sl::kFeatureDLSS_G,
            sl::kFeatureReflex};
        const wchar_t* pluginPaths[] = {pluginPath.c_str()};
        sl::Preferences preferences{};
        preferences.pathsToPlugins = pluginPaths;
        preferences.numPathsToPlugins = 1;
        preferences.pathToLogsAndData = logPath.c_str();
        preferences.featuresToLoad = features;
        preferences.numFeaturesToLoad = 2;
        preferences.applicationId = 100721531;
        preferences.engine = sl::EngineType::eCustom;
        preferences.engineVersion = nullptr;
        preferences.projectId = nullptr;
        preferences.renderAPI = sl::RenderAPI::eD3D12;
        preferences.flags = sl::PreferenceFlags::eUseFrameBasedResourceTagging;

        requireSl(slInit(preferences, sl::kSDKVersion), "slInit");
        initialized = true;
        snapshot.streamlineInitialized = true;

        context = D3D12ProbeContext::create(
            kRenderWidth, kRenderHeight, kOutputWidth, kOutputHeight);
        snapshot.adapterName = context->snapshot().adapterName;
        void* nativeDevice = nullptr;
        requireSl(
            slGetNativeInterface(context->device(), &nativeDevice),
            "slGetNativeInterface(ID3D12Device)");
        requireSl(slSetD3DDevice(nativeDevice), "slSetD3DDevice");
        snapshot.d3dDeviceAccepted = true;

        LUID adapterLuid = context->adapterLuid();
        sl::AdapterInfo adapterInfo{};
        adapterInfo.deviceLUID = reinterpret_cast<std::uint8_t*>(&adapterLuid);
        adapterInfo.deviceLUIDSizeInBytes = sizeof(adapterLuid);
        requireSl(
            slIsFeatureSupported(sl::kFeatureDLSS_G, adapterInfo),
            "slIsFeatureSupported(DLSS-G)");
        snapshot.dlssGSupported = true;
        requireSl(
            slIsFeatureSupported(sl::kFeatureReflex, adapterInfo),
            "slIsFeatureSupported(Reflex)");
        snapshot.reflexSupported = true;

        sl::FeatureRequirements requirements{};
        requireSl(
            slGetFeatureRequirements(sl::kFeatureDLSS_G, requirements),
            "slGetFeatureRequirements(DLSS-G)");
        snapshot.requirementsAvailable = true;
        const auto requirementFlags =
            static_cast<std::uint32_t>(requirements.flags);
        snapshot.d3d12Supported =
            (requirementFlags & static_cast<std::uint32_t>(
                sl::FeatureRequirementFlags::eD3D12Supported)) != 0;
        snapshot.hardwareSchedulingRequired =
            (requirementFlags & static_cast<std::uint32_t>(
            sl::FeatureRequirementFlags::eHardwareSchedulingRequired)) != 0;

        D3D12_RESOURCE_BARRIER inputBarriers[2]{};
        inputBarriers[0].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        inputBarriers[0].Transition.pResource = context->depth();
        inputBarriers[0].Transition.Subresource =
            D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        inputBarriers[0].Transition.StateBefore = D3D12_RESOURCE_STATE_DEPTH_WRITE;
        inputBarriers[0].Transition.StateAfter =
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
        inputBarriers[1].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        inputBarriers[1].Transition.pResource = context->motionVectors();
        inputBarriers[1].Transition.Subresource =
            D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        inputBarriers[1].Transition.StateBefore = D3D12_RESOURCE_STATE_RENDER_TARGET;
        inputBarriers[1].Transition.StateAfter =
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
        context->commandList()->ResourceBarrier(2, inputBarriers);
        if (!context->submitAndWait()) {
            throw std::runtime_error("Failed to prepare DLSS-G input resources.");
        }

        HiddenSwapChain swapChain(
            context->factory(), context->commandQueue());
        snapshot.swapChainCreated = true;

        sl::ReflexOptions reflexOptions{};
        reflexOptions.mode = sl::ReflexMode::eLowLatency;
        requireSl(slReflexSetOptions(reflexOptions), "slReflexSetOptions");
        snapshot.reflexEnabled = true;

        sl::DLSSGOptions options{};
        options.mode = sl::DLSSGMode::eOff;
        options.numFramesToGenerate = 1;
        options.numBackBuffers = 3;
        options.mvecDepthWidth = kRenderWidth;
        options.mvecDepthHeight = kRenderHeight;
        options.colorWidth = kOutputWidth;
        options.colorHeight = kOutputHeight;
        options.colorBufferFormat = DXGI_FORMAT_R8G8B8A8_UNORM;
        options.mvecBufferFormat = DXGI_FORMAT_R16G16_FLOAT;
        options.depthBufferFormat = DXGI_FORMAT_D32_FLOAT;
        const sl::ViewportHandle viewport(0U);
        requireSl(slDLSSGSetOptions(viewport, options), "slDLSSGSetOptions");

        for (std::uint32_t warmupIndex = 0; warmupIndex < 2; ++warmupIndex) {
            sl::FrameToken* warmupToken = nullptr;
            std::uint32_t requestedIndex = warmupIndex;
            requireSl(
                slGetNewFrameToken(warmupToken, &requestedIndex),
                "slGetNewFrameToken(warmup)");
            if (warmupToken == nullptr) {
                throw std::runtime_error("Streamline returned a null warmup token.");
            }
            requireSl(
                slPCLSetMarker(sl::PCLMarker::ePresentStart, *warmupToken),
                "slPCLSetMarker(PresentStart warmup)");
            (void)swapChain.get()->GetCurrentBackBufferIndex();
            requireHr(
                swapChain.get()->Present(0, 0),
                "IDXGISwapChain::Present(warmup)");
            requireSl(
                slPCLSetMarker(sl::PCLMarker::ePresentEnd, *warmupToken),
                "slPCLSetMarker(PresentEnd warmup)");
        }

        options.mode = sl::DLSSGMode::eOn;
        requireSl(
            slDLSSGSetOptions(viewport, options),
            "slDLSSGSetOptions(enable)");
        snapshot.optionsAccepted = true;

        sl::Resource depth(
            sl::ResourceType::eTex2d, context->depth(),
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
        sl::Resource motion(
            sl::ResourceType::eTex2d,
            context->motionVectors(),
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
        const sl::Extent renderExtent{
            0, 0, kRenderWidth, kRenderHeight};

        for (std::uint32_t frameIndex = 0; frameIndex < 8; ++frameIndex) {
            sl::FrameToken* frameToken = nullptr;
            std::uint32_t requestedIndex = frameIndex;
            requireSl(
                slGetNewFrameToken(frameToken, &requestedIndex),
                "slGetNewFrameToken");
            if (frameToken == nullptr) {
                throw std::runtime_error("Streamline returned a null frame token.");
            }
            requireSl(
                slSetConstants(
                    frameConstants(frameIndex == 0), *frameToken, viewport),
                "slSetConstants");
            sl::ResourceTag tags[] = {
                {&depth, sl::kBufferTypeDepth,
                 sl::ResourceLifecycle::eValidUntilPresent, &renderExtent},
                {&motion, sl::kBufferTypeMotionVectors,
                 sl::ResourceLifecycle::eValidUntilPresent, &renderExtent}};
            requireSl(
                slSetTagForFrame(
                    *frameToken, viewport, tags,
                    static_cast<std::uint32_t>(std::size(tags)), nullptr),
                "slSetTagForFrame");
            requireSl(
                slPCLSetMarker(sl::PCLMarker::eRenderSubmitStart, *frameToken),
                "slPCLSetMarker(RenderSubmitStart)");
            requireSl(
                slPCLSetMarker(sl::PCLMarker::eRenderSubmitEnd, *frameToken),
                "slPCLSetMarker(RenderSubmitEnd)");
            requireSl(
                slPCLSetMarker(sl::PCLMarker::ePresentStart, *frameToken),
                "slPCLSetMarker(PresentStart)");
            (void)swapChain.get()->GetCurrentBackBufferIndex();
            requireHr(swapChain.get()->Present(0, 0), "IDXGISwapChain::Present");
            requireSl(
                slPCLSetMarker(sl::PCLMarker::ePresentEnd, *frameToken),
                "slPCLSetMarker(PresentEnd)");
            ++snapshot.applicationFramesPresented;

            sl::DLSSGState presentedState{};
            requireSl(
                slDLSSGGetState(viewport, presentedState, nullptr),
                "slDLSSGGetState(after present)");
            snapshot.framesActuallyPresented +=
                presentedState.numFramesActuallyPresented;
        }

        sl::DLSSGState state{};
        requireSl(
            slDLSSGGetState(sl::ViewportHandle(0U), state, nullptr),
            "slDLSSGGetState");
        snapshot.maxFramesToGenerate = state.numFramesToGenerateMax;
        if (snapshot.maxFramesToGenerate == 0) {
            throw std::runtime_error(
                "DLSS-G reported zero generated frames for this adapter.");
        }
        snapshot.lastResult = "eOk";
        snapshot.message = "DLSS frame generation capability is available.";
    } catch (const std::exception& error) {
        snapshot.lastResult = "hostError";
        snapshot.message = error.what();
        if (snapshot.message.find("eErrorFeatureNotSupported") !=
            std::string::npos) {
            snapshot.message =
                "DLSS-G requires an NVIDIA-issued NGX Application ID; "
                "the Streamline temporary ID is not eligible for frame generation.";
        }
    } catch (...) {
        snapshot.lastResult = "hostError";
        snapshot.message = "Unknown DLSS frame generation probe failure.";
    }

    if (initialized) {
        snapshot.streamlineShutdownSucceeded = slShutdown() == sl::Result::eOk;
    }
    context.reset();
    return snapshot;
}

}
