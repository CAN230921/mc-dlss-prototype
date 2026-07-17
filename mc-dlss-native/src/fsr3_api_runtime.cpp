#include "fsr3_api_runtime.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <array>
#include <algorithm>
#include <cstdio>
#include <sstream>
#include <utility>
#include <vector>

#if defined(MC_DLSS_HAS_FIDELITYFX)
#include <ffx_api.h>
#include <ffx_api_loader.h>
#include <ffx_api_types.h>
#include <dx12/ffx_api_dx12.h>
#include <ffx_upscale.h>
#include <ffx_framegeneration.h>
#endif

namespace mc_dlss {

struct Fsr3ApiRuntime::Impl {
    HMODULE module = nullptr;
    std::array<FARPROC, 5> functions{};
    void* upscalerContext = nullptr;
    void* frameGenerationContext = nullptr;
    Fsr3CapabilitySnapshot snapshot{};

    ~Impl() {
        using DestroyFunction = unsigned int (*)(void**, const void*);
        if (functions[1] != nullptr) {
            const auto destroy = reinterpret_cast<DestroyFunction>(functions[1]);
            if (frameGenerationContext != nullptr) {
                destroy(&frameGenerationContext, nullptr);
            }
            if (upscalerContext != nullptr) destroy(&upscalerContext, nullptr);
        }
        if (module != nullptr) FreeLibrary(module);
    }
};

Fsr3ApiRuntime::Fsr3ApiRuntime(std::unique_ptr<Impl> impl)
    : impl_(std::move(impl)) {}

Fsr3ApiRuntime::~Fsr3ApiRuntime() = default;
Fsr3ApiRuntime::Fsr3ApiRuntime(Fsr3ApiRuntime&&) noexcept = default;
Fsr3ApiRuntime& Fsr3ApiRuntime::operator=(Fsr3ApiRuntime&&) noexcept = default;

#if defined(MC_DLSS_HAS_FIDELITYFX)
namespace {

ffxFunctions apiFunctions(const std::array<FARPROC, 5>& entries) {
    ffxFunctions functions{};
    functions.CreateContext = reinterpret_cast<PfnFfxCreateContext>(entries[0]);
    functions.DestroyContext = reinterpret_cast<PfnFfxDestroyContext>(entries[1]);
    functions.Configure = reinterpret_cast<PfnFfxConfigure>(entries[2]);
    functions.Query = reinterpret_cast<PfnFfxQuery>(entries[3]);
    functions.Dispatch = reinterpret_cast<PfnFfxDispatch>(entries[4]);
    return functions;
}

std::pair<std::uint64_t, std::string> firstProvider(
    const ffxFunctions& functions,
    ffxStructType_t createType,
    ID3D12Device* device,
    ffxReturnCode_t& result) {
    std::uint64_t count = 0;
    ffxQueryDescGetVersions query{};
    query.header.type = FFX_API_QUERY_DESC_TYPE_GET_VERSIONS;
    query.createDescType = createType;
    query.device = device;
    query.outputCount = &count;
    result = functions.Query(nullptr, &query.header);
    if (result != FFX_API_RETURN_OK || count == 0) return {};

    std::vector<std::uint64_t> ids(static_cast<std::size_t>(count));
    std::vector<const char*> names(static_cast<std::size_t>(count));
    query.versionIds = ids.data();
    query.versionNames = names.data();
    result = functions.Query(nullptr, &query.header);
    if (result != FFX_API_RETURN_OK || names[0] == nullptr) return {};
    return {ids[0], names[0]};
}

std::uint64_t toMiB(std::uint64_t bytes) {
    return (bytes + 1024 * 1024 - 1) / (1024 * 1024);
}

void ffxMessageCallback(std::uint32_t type, const wchar_t* message) {
    std::fwprintf(stderr, L"FidelityFX[%u]: %ls\n", type,
        message == nullptr ? L"" : message);
    std::fflush(stderr);
}

}
#endif

Fsr3ApiLoadResult Fsr3ApiRuntime::load(const std::wstring& loaderPath) {
    HMODULE module = LoadLibraryExW(
        loaderPath.c_str(), nullptr,
        LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    if (module == nullptr) {
        return {nullptr, "LoadLibraryExW failed with Windows error "
            + std::to_string(GetLastError())};
    }

    auto impl = std::make_unique<Impl>();
    impl->module = module;
    constexpr std::array<const char*, 5> names{
        "ffxCreateContext", "ffxDestroyContext", "ffxConfigure",
        "ffxQuery", "ffxDispatch"};
    for (std::size_t index = 0; index < names.size(); ++index) {
        impl->functions[index] = GetProcAddress(module, names[index]);
        if (impl->functions[index] == nullptr) {
            return {nullptr, std::string("FidelityFX exports missing: ") + names[index]};
        }
    }
    return {std::unique_ptr<Fsr3ApiRuntime>(new Fsr3ApiRuntime(std::move(impl))), {}};
}

Fsr3CapabilitySnapshot Fsr3ApiRuntime::initializeContexts(
    ID3D12Device* device,
    unsigned int renderWidth,
    unsigned int renderHeight,
    unsigned int displayWidth,
    unsigned int displayHeight) {
    if (impl_->upscalerContext != nullptr || impl_->frameGenerationContext != nullptr) {
        return impl_->snapshot;
    }
    Fsr3CapabilitySnapshot snapshot{};
    snapshot.loaderAvailable = true;
#if !defined(MC_DLSS_HAS_FIDELITYFX)
    snapshot.message = "FidelityFX headers were not enabled for this target";
    return snapshot;
#else
    if (device == nullptr || renderWidth == 0 || renderHeight == 0 ||
        displayWidth == 0 || displayHeight == 0) {
        snapshot.message = "Invalid D3D12 device or dimensions";
        return snapshot;
    }

    const ffxFunctions functions = apiFunctions(impl_->functions);
    ffxConfigureDescGlobalDebug globalDebug{};
    globalDebug.header.type = FFX_API_CONFIGURE_DESC_TYPE_GLOBALDEBUG;
    globalDebug.effectId = FFX_API_EFFECT_ID_FRAMEGENERATION;
    globalDebug.fpMessage = ffxMessageCallback;
    globalDebug.debugLevel = FFX_API_CONFIGURE_GLOBALDEBUG_LEVEL_WARNINGS;
    functions.Configure(nullptr, &globalDebug.header);
    ffxReturnCode_t srQueryCode = FFX_API_RETURN_OK;
    const auto srProvider = firstProvider(
        functions, FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE, device, srQueryCode);
    if (!srProvider.second.empty()) {
        snapshot.upscaler.state = Fsr3FeatureState::ProviderAvailable;
        snapshot.upscaler.providerName = srProvider.second;

        FfxApiEffectMemoryUsage memory{};
        ffxQueryDescUpscaleGetGPUMemoryUsageV2 memoryQuery{};
        memoryQuery.header.type = FFX_API_QUERY_DESC_TYPE_UPSCALE_GPU_MEMORY_USAGE_V2;
        memoryQuery.device = device;
        memoryQuery.maxRenderSize = {renderWidth, renderHeight};
        memoryQuery.maxUpscaleSize = {displayWidth, displayHeight};
        memoryQuery.flags = FFX_UPSCALE_ENABLE_AUTO_EXPOSURE |
            FFX_UPSCALE_ENABLE_DEBUG_CHECKING;
        memoryQuery.gpuMemoryUsageUpscaler = &memory;
        ffxOverrideVersion overrideVersion{};
        overrideVersion.header.type = FFX_API_DESC_TYPE_OVERRIDE_VERSION;
        overrideVersion.versionId = srProvider.first;
        memoryQuery.header.pNext = &overrideVersion.header;
        const ffxReturnCode_t memoryCode = functions.Query(nullptr, &memoryQuery.header);

        ffxCreateContextDescUpscale create{};
        create.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE;
        create.flags = memoryQuery.flags;
        create.maxRenderSize = memoryQuery.maxRenderSize;
        create.maxUpscaleSize = memoryQuery.maxUpscaleSize;
        create.fpMessage = ffxMessageCallback;
        ffxCreateBackendDX12Desc backend{};
        backend.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_DX12;
        backend.device = device;
        ffxCreateContextDescUpscaleVersion version{};
        version.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE_VERSION;
        version.version = FFX_UPSCALER_VERSION;
        create.header.pNext = &backend.header;
        backend.header.pNext = &version.header;
        version.header.pNext = &overrideVersion.header;
        ffxContext context = nullptr;
        const ffxReturnCode_t createCode = functions.CreateContext(
            &context, &create.header, nullptr);
        snapshot.upscaler.returnCode = createCode;
        if (createCode == FFX_API_RETURN_OK) {
            snapshot.upscaler.state = Fsr3FeatureState::ContextReady;
            if (memoryCode == FFX_API_RETURN_OK) {
                snapshot.upscaler.memoryMiB = toMiB(memory.totalUsageInBytes);
            }
            impl_->upscalerContext = context;
        } else {
            snapshot.upscaler.state = Fsr3FeatureState::ContextFailed;
        }
    } else {
        snapshot.upscaler.returnCode = srQueryCode;
    }

    ffxReturnCode_t fgQueryCode = FFX_API_RETURN_OK;
    const auto fgProvider = firstProvider(
        functions, FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION,
        device, fgQueryCode);
    if (!fgProvider.second.empty()) {
        snapshot.frameGeneration.state = Fsr3FeatureState::ProviderAvailable;
        snapshot.frameGeneration.providerName = fgProvider.second;

        constexpr std::uint32_t format = FFX_API_SURFACE_FORMAT_R16G16B16A16_FLOAT;
        FfxApiEffectMemoryUsage memory{};
        ffxQueryDescFrameGenerationGetGPUMemoryUsageV2 memoryQuery{};
        memoryQuery.header.type = FFX_API_QUERY_DESC_TYPE_FRAMEGENERATION_GPU_MEMORY_USAGE_V2;
        memoryQuery.device = device;
        memoryQuery.maxRenderSize = {renderWidth, renderHeight};
        memoryQuery.displaySize = {displayWidth, displayHeight};
        memoryQuery.backBufferFormat = format;
        memoryQuery.hudlessBackBufferFormat = format;
        memoryQuery.gpuMemoryUsageFrameGeneration = &memory;
        ffxOverrideVersion overrideVersion{};
        overrideVersion.header.type = FFX_API_DESC_TYPE_OVERRIDE_VERSION;
        overrideVersion.versionId = fgProvider.first;
        memoryQuery.header.pNext = &overrideVersion.header;
        const ffxReturnCode_t memoryCode = functions.Query(nullptr, &memoryQuery.header);

        ffxCreateContextDescFrameGeneration create{};
        create.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION;
        create.displaySize = {displayWidth, displayHeight};
        create.maxRenderSize = {renderWidth, renderHeight};
        create.backBufferFormat = format;
        ffxCreateBackendDX12Desc backend{};
        backend.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_BACKEND_DX12;
        backend.device = device;
        ffxCreateContextDescFrameGenerationVersion version{};
        version.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION_VERSION;
        version.version = FFX_FRAMEGENERATION_VERSION;
        ffxCreateContextDescFrameGenerationHudless hudless{};
        hudless.header.type = FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION_HUDLESS;
        hudless.hudlessBackBufferFormat = format;
        create.header.pNext = &backend.header;
        backend.header.pNext = &version.header;
        version.header.pNext = &hudless.header;
        hudless.header.pNext = &overrideVersion.header;
        ffxContext context = nullptr;
        const ffxReturnCode_t createCode = functions.CreateContext(
            &context, &create.header, nullptr);
        snapshot.frameGeneration.returnCode = createCode;
        if (createCode == FFX_API_RETURN_OK) {
            snapshot.frameGeneration.state = Fsr3FeatureState::ContextReady;
            if (memoryCode == FFX_API_RETURN_OK) {
                snapshot.frameGeneration.memoryMiB = toMiB(memory.totalUsageInBytes);
            }
            impl_->frameGenerationContext = context;
        } else {
            snapshot.frameGeneration.state = Fsr3FeatureState::ContextFailed;
        }
    } else {
        snapshot.frameGeneration.returnCode = fgQueryCode;
    }
    impl_->snapshot = snapshot;
    return impl_->snapshot;
#endif
}

unsigned int Fsr3ApiRuntime::dispatchUpscale(
    const Fsr3UpscaleDispatchInputs& inputs) noexcept {
#if !defined(MC_DLSS_HAS_FIDELITYFX)
    (void)inputs;
    return 4;
#else
    if (impl_->upscalerContext == nullptr || inputs.commandList == nullptr ||
        inputs.color == nullptr || inputs.depth == nullptr ||
        inputs.motionVectors == nullptr || inputs.output == nullptr) return 6;
    const ffxFunctions functions = apiFunctions(impl_->functions);
    ffxDispatchDescUpscale dispatch{};
    dispatch.header.type = FFX_API_DISPATCH_DESC_TYPE_UPSCALE;
    dispatch.commandList = inputs.commandList;
    dispatch.color = ffxApiGetResourceDX12(
        inputs.color, FFX_API_RESOURCE_STATE_COMPUTE_READ);
    dispatch.depth = ffxApiGetResourceDX12(
        inputs.depth, FFX_API_RESOURCE_STATE_COMPUTE_READ);
    dispatch.motionVectors = ffxApiGetResourceDX12(
        inputs.motionVectors, FFX_API_RESOURCE_STATE_COMPUTE_READ);
    dispatch.output = ffxApiGetResourceDX12(
        inputs.output, FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
    dispatch.jitterOffset = {inputs.jitterX, inputs.jitterY};
    dispatch.motionVectorScale = {inputs.motionScaleX, inputs.motionScaleY};
    dispatch.renderSize = {inputs.renderWidth, inputs.renderHeight};
    dispatch.upscaleSize = {inputs.displayWidth, inputs.displayHeight};
    dispatch.frameTimeDelta = inputs.frameTimeMilliseconds;
    dispatch.preExposure = 1.0F;
    dispatch.reset = inputs.reset;
    dispatch.cameraNear = inputs.cameraNear;
    dispatch.cameraFar = inputs.cameraFar;
    dispatch.cameraFovAngleVertical = inputs.verticalFovRadians;
    dispatch.viewSpaceToMetersFactor = 1.0F;
    return functions.Dispatch(
        &impl_->upscalerContext, &dispatch.header);
#endif
}

unsigned int Fsr3ApiRuntime::dispatchFrameGeneration(
    const Fsr3FrameGenerationDispatchInputs& inputs) noexcept {
#if !defined(MC_DLSS_HAS_FIDELITYFX)
    (void)inputs;
    return 4;
#else
    if (impl_->frameGenerationContext == nullptr || inputs.commandList == nullptr ||
        inputs.depth == nullptr || inputs.motionVectors == nullptr ||
        inputs.presentColor == nullptr || inputs.hudlessColor == nullptr ||
        inputs.generatedOutput == nullptr || inputs.frameId == 0) return 6;
    const ffxFunctions functions = apiFunctions(impl_->functions);

    ffxDispatchDescFrameGenerationPrepareV2 prepare{};
    prepare.header.type = FFX_API_DISPATCH_DESC_TYPE_FRAMEGENERATION_PREPARE_V2;
    prepare.frameID = inputs.frameId;
    prepare.commandList = inputs.commandList;
    prepare.renderSize = {inputs.renderWidth, inputs.renderHeight};
    prepare.jitterOffset = {-inputs.jitterX, -inputs.jitterY};
    prepare.motionVectorScale = {inputs.motionScaleX, inputs.motionScaleY};
    prepare.frameTimeDelta = inputs.frameTimeMilliseconds;
    prepare.reset = inputs.reset;
    prepare.cameraNear = inputs.cameraNear;
    prepare.cameraFar = inputs.cameraFar;
    prepare.cameraFovAngleVertical = inputs.verticalFovRadians;
    prepare.viewSpaceToMetersFactor = 1.0F;
    prepare.depth = ffxApiGetResourceDX12(
        inputs.depth, FFX_API_RESOURCE_STATE_COMPUTE_READ);
    prepare.motionVectors = ffxApiGetResourceDX12(
        inputs.motionVectors, FFX_API_RESOURCE_STATE_COMPUTE_READ);
    std::copy_n(inputs.cameraPosition, 3, prepare.cameraPosition);
    std::copy_n(inputs.cameraUp, 3, prepare.cameraUp);
    std::copy_n(inputs.cameraRight, 3, prepare.cameraRight);
    std::copy_n(inputs.cameraForward, 3, prepare.cameraForward);
    ffxReturnCode_t code = functions.Dispatch(
        &impl_->frameGenerationContext, &prepare.header);
    if (code != FFX_API_RETURN_OK) {
        std::fprintf(stderr, "FidelityFX FG prepare failed: %u\n", code);
        return code;
    }

    ffxDispatchDescFrameGeneration dispatch{};
    dispatch.header.type = FFX_API_DISPATCH_DESC_TYPE_FRAMEGENERATION;
    dispatch.commandList = inputs.commandList;
    dispatch.presentColor = ffxApiGetResourceDX12(
        inputs.presentColor, FFX_API_RESOURCE_STATE_COMPUTE_READ);
    dispatch.outputs[0] = ffxApiGetResourceDX12(
        inputs.generatedOutput, FFX_API_RESOURCE_STATE_UNORDERED_ACCESS);
    dispatch.numGeneratedFrames = 1;
    dispatch.reset = inputs.reset;
    dispatch.backbufferTransferFunction =
        FFX_API_BACKBUFFER_TRANSFER_FUNCTION_SCRGB;
    dispatch.minMaxLuminance[0] = 0.0F;
    dispatch.minMaxLuminance[1] = 1000.0F;
    dispatch.generationRect = {
        0, 0, static_cast<std::int32_t>(inputs.displayWidth),
        static_cast<std::int32_t>(inputs.displayHeight)};
    dispatch.frameID = inputs.frameId;
    code = functions.Dispatch(&impl_->frameGenerationContext, &dispatch.header);
    if (code != FFX_API_RETURN_OK) {
        std::fprintf(stderr, "FidelityFX FG generate failed: %u\n", code);
    }
    return code;
#endif
}

std::string describeFfxReturnCode(unsigned int code) {
    switch (code) {
        case 0: return "OK";
        case 1: return "ERROR";
        case 2: return "UNKNOWN_DESCTYPE";
        case 3: return "RUNTIME_ERROR";
        case 4: return "NO_PROVIDER";
        case 5: return "MEMORY";
        case 6: return "PARAMETER";
        case 7: return "PROVIDER_NO_SUPPORT_NEW_DESCTYPE";
        default: return "UNKNOWN_" + std::to_string(code);
    }
}

}
