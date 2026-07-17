#include "live_streamline_runtime.h"

#include <cmath>
#include <iterator>
#include <limits>
#include <mutex>
#include <stdexcept>

#ifdef MC_DLSS_HAS_STREAMLINE
#include <sl.h>
#include <sl_consts.h>
#include <sl_dlss.h>
#endif

namespace mc_dlss {

const char* liveStreamlineStageName(LiveStreamlineStage stage) noexcept {
    switch (stage) {
        case LiveStreamlineStage::initialize: return "INITIALIZE";
        case LiveStreamlineStage::device: return "DEVICE";
        case LiveStreamlineStage::support: return "SUPPORT";
        case LiveStreamlineStage::settings: return "SETTINGS";
        case LiveStreamlineStage::options: return "OPTIONS";
        case LiveStreamlineStage::token: return "TOKEN";
        case LiveStreamlineStage::constants: return "CONSTANTS";
        case LiveStreamlineStage::tags: return "TAGS";
        case LiveStreamlineStage::evaluate: return "EVALUATE";
        case LiveStreamlineStage::freeResources: return "FREE_RESOURCES";
        case LiveStreamlineStage::ready: return "READY";
        default: return "NONE";
    }
}

LiveStreamlineStatus runLiveStreamlineStageContract(
    LiveStreamlineStageBackend& backend) noexcept {
    const LiveStreamlineStage stages[] = {
        LiveStreamlineStage::initialize, LiveStreamlineStage::device,
        LiveStreamlineStage::support, LiveStreamlineStage::settings,
        LiveStreamlineStage::options, LiveStreamlineStage::token,
        LiveStreamlineStage::constants, LiveStreamlineStage::tags,
        LiveStreamlineStage::evaluate, LiveStreamlineStage::freeResources};
    for (const auto stage : stages) {
        if (!backend.invoke(stage)) {
            return {false, stage,
                std::string(liveStreamlineStageName(stage)) + " failed"};
        }
    }
    return {true, LiveStreamlineStage::ready, "Streamline stage contract ready"};
}

bool LiveStreamlineFrameSequence::next(std::uint32_t& frameIndex) noexcept {
    if (nextFrameIndex_ > (std::numeric_limits<std::uint32_t>::max)()) {
        return false;
    }
    frameIndex = static_cast<std::uint32_t>(nextFrameIndex_++);
    return true;
}

struct LiveStreamlineRuntime::Impl {
    bool available = false;
    bool processGlobalStateRetained = false;
    bool resourcesFreed = false;
    LiveDlssOptimalSettingsNative optimal{};
    LiveStreamlineStatus status{false, LiveStreamlineStage::none,
        "Streamline support is not compiled into this native build"};
    std::uint32_t outputWidth = 0;
    std::uint32_t outputHeight = 0;
    LiveStreamlineFrameSequence frames;
#ifdef MC_DLSS_HAS_STREAMLINE
    sl::ViewportHandle viewport{0U};
#endif
};

LiveStreamlineRuntime::LiveStreamlineRuntime() : impl_(std::make_unique<Impl>()) {}
LiveStreamlineRuntime::~LiveStreamlineRuntime() = default;

#ifdef MC_DLSS_HAS_STREAMLINE
namespace {

constexpr const char* kProjectId = "7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e";
constexpr const char* kEngineVersion = "mc-dlss-prototype/0.1.0";
std::mutex streamlineInitializationMutex;
bool streamlineInitialized = false;
bool streamlineDeviceSet = false;

std::string resultMessage(const char* operation, sl::Result result) {
    return std::string(operation) + " result(" +
        std::to_string(static_cast<int>(result)) + ")";
}

void requireResult(sl::Result result, LiveStreamlineStage stage, const char* operation) {
    if (result != sl::Result::eOk) {
        throw LiveStreamlineStatus{false, stage, resultMessage(operation, result)};
    }
}

sl::float4x4 rowMajorMatrix(const std::array<float, 16>& columnMajor) {
    sl::float4x4 result{};
    for (std::size_t row = 0; row < 4; ++row) {
        result.setRow(row, {
            columnMajor[row], columnMajor[4 + row],
            columnMajor[8 + row], columnMajor[12 + row]});
    }
    return result;
}

sl::Constants convertConstants(const LiveDlssConstantsData& source) {
    sl::Constants result{};
    result.cameraViewToClip = rowMajorMatrix(source.cameraViewToClip);
    result.clipToCameraView = rowMajorMatrix(source.clipToCameraView);
    result.clipToLensClip = rowMajorMatrix({
        1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    result.clipToPrevClip = rowMajorMatrix(source.clipToPrevClip);
    result.prevClipToClip = rowMajorMatrix(source.prevClipToClip);
    result.jitterOffset = {source.jitterX, source.jitterY};
    result.mvecScale = {source.motionScaleX, source.motionScaleY};
    result.cameraPinholeOffset = {0.0F, 0.0F};
    result.cameraPos = {source.cameraPosition[0], source.cameraPosition[1],
        source.cameraPosition[2]};
    result.cameraUp = {source.cameraUp[0], source.cameraUp[1], source.cameraUp[2]};
    result.cameraRight = {source.cameraRight[0], source.cameraRight[1],
        source.cameraRight[2]};
    result.cameraFwd = {source.cameraForward[0], source.cameraForward[1],
        source.cameraForward[2]};
    result.cameraNear = source.cameraNear;
    result.cameraFar = source.cameraFar;
    result.cameraFOV = source.cameraFov;
    result.cameraAspectRatio = source.cameraAspect;
    result.depthInverted = sl::Boolean::eFalse;
    result.cameraMotionIncluded = sl::Boolean::eTrue;
    result.motionVectors3D = sl::Boolean::eFalse;
    result.reset = source.reset ? sl::Boolean::eTrue : sl::Boolean::eFalse;
    result.orthographicProjection = sl::Boolean::eFalse;
    result.motionVectorsDilated = sl::Boolean::eFalse;
    result.motionVectorsJittered = sl::Boolean::eFalse;
    return result;
}

}
#endif

std::unique_ptr<LiveStreamlineRuntime> LiveStreamlineRuntime::create(
    ID3D12Device* device, LUID adapterLuid,
    std::uint32_t outputWidth, std::uint32_t outputHeight,
    const std::wstring& pluginPath, const std::wstring& logPath,
    LiveDlssQualityMode qualityMode) noexcept {
    auto runtime = std::unique_ptr<LiveStreamlineRuntime>(new LiveStreamlineRuntime());
    auto& state = *runtime->impl_;
    state.outputWidth = outputWidth;
    state.outputHeight = outputHeight;
#ifdef MC_DLSS_HAS_STREAMLINE
    try {
        if (device == nullptr || outputWidth == 0 || outputHeight == 0) {
            throw LiveStreamlineStatus{false, LiveStreamlineStage::device,
                "Live Streamline device or output dimensions are invalid"};
        }
        const sl::Feature features[] = {sl::kFeatureDLSS};
        const wchar_t* pluginPaths[] = {pluginPath.c_str()};
        sl::Preferences preferences{};
        preferences.showConsole = false;
        preferences.logLevel = sl::LogLevel::eDefault;
        preferences.pathsToPlugins = pluginPaths;
        preferences.numPathsToPlugins = 1;
        preferences.pathToLogsAndData = logPath.c_str();
        preferences.flags = sl::PreferenceFlags::eDisableCLStateTracking
            | sl::PreferenceFlags::eUseFrameBasedResourceTagging;
        preferences.featuresToLoad = features;
        preferences.numFeaturesToLoad = 1;
        preferences.applicationId = 0;
        preferences.engine = sl::EngineType::eCustom;
        preferences.engineVersion = kEngineVersion;
        preferences.projectId = kProjectId;
        preferences.renderAPI = sl::RenderAPI::eD3D12;
        {
            std::lock_guard<std::mutex> lock(streamlineInitializationMutex);
            if (!streamlineInitialized) {
                requireResult(slInit(preferences, sl::kSDKVersion),
                    LiveStreamlineStage::initialize, "slInit");
                streamlineInitialized = true;
            }
        }
        state.processGlobalStateRetained = true;
        {
            std::lock_guard<std::mutex> lock(streamlineInitializationMutex);
            if (!streamlineDeviceSet) {
                requireResult(slSetD3DDevice(device), LiveStreamlineStage::device,
                    "slSetD3DDevice");
                streamlineDeviceSet = true;
            }
        }
        sl::AdapterInfo adapterInfo{};
        adapterInfo.deviceLUID = reinterpret_cast<std::uint8_t*>(&adapterLuid);
        adapterInfo.deviceLUIDSizeInBytes = sizeof(adapterLuid);
        requireResult(slIsFeatureSupported(sl::kFeatureDLSS, adapterInfo),
            LiveStreamlineStage::support, "slIsFeatureSupported");
        sl::DLSSOptions options{};
        options.mode = [&]() {
            switch (qualityMode) {
                case LiveDlssQualityMode::balanced: return sl::DLSSMode::eBalanced;
                case LiveDlssQualityMode::performance: return sl::DLSSMode::eMaxPerformance;
                case LiveDlssQualityMode::ultraPerformance:
                    return sl::DLSSMode::eUltraPerformance;
                default: return sl::DLSSMode::eMaxQuality;
            }
        }();
        options.outputWidth = outputWidth;
        options.outputHeight = outputHeight;
    options.colorBuffersHDR = sl::Boolean::eTrue;
    options.useAutoExposure = sl::Boolean::eTrue;
        options.alphaUpscalingEnabled = sl::Boolean::eFalse;
        sl::DLSSOptimalSettings optimal{};
        requireResult(slDLSSGetOptimalSettings(options, optimal),
            LiveStreamlineStage::settings, "slDLSSGetOptimalSettings");
        state.optimal = {true, optimal.optimalRenderWidth,
            optimal.optimalRenderHeight, optimal.optimalSharpness};
        options.sharpness = optimal.optimalSharpness;
        requireResult(slDLSSSetOptions(state.viewport, options),
            LiveStreamlineStage::options, "slDLSSSetOptions");
        state.available = true;
        state.status = {true, LiveStreamlineStage::ready,
            "Live Streamline DLSS runtime ready"};
    } catch (const LiveStreamlineStatus& failure) {
        state.status = failure;
    } catch (const std::exception& error) {
        state.status = {false, LiveStreamlineStage::initialize, error.what()};
    } catch (...) {
        state.status = {false, LiveStreamlineStage::initialize,
            "Unknown live Streamline initialization failure"};
    }
#else
    (void)device; (void)adapterLuid; (void)pluginPath; (void)logPath;
    (void)qualityMode;
#endif
    return runtime;
}

bool LiveStreamlineRuntime::available() const noexcept { return impl_->available; }
LiveDlssOptimalSettingsNative LiveStreamlineRuntime::optimalSettings() const noexcept {
    return impl_->optimal;
}
LiveStreamlineStatus LiveStreamlineRuntime::status() const { return impl_->status; }

LiveStreamlineStatus LiveStreamlineRuntime::evaluate(
    const LiveDlssEvaluationResources& resources,
    const LiveDlssConstantsData& constants) noexcept {
#ifdef MC_DLSS_HAS_STREAMLINE
    auto& state = *impl_;
    try {
        if (!state.available || resources.color == nullptr || resources.depth == nullptr
            || resources.motion == nullptr || resources.output == nullptr
            || resources.commandList == nullptr
            || resources.renderWidth != state.optimal.renderWidth
            || resources.renderHeight != state.optimal.renderHeight
            || resources.outputWidth != state.outputWidth
            || resources.outputHeight != state.outputHeight) {
            throw LiveStreamlineStatus{false, LiveStreamlineStage::evaluate,
                "Live DLSS evaluation resources are invalid"};
        }
        std::uint32_t frameIndex = 0;
        if (!state.frames.next(frameIndex)) {
            throw LiveStreamlineStatus{false, LiveStreamlineStage::token,
                "Live Streamline frame index exhausted"};
        }
        sl::FrameToken* token = nullptr;
        requireResult(slGetNewFrameToken(token, &frameIndex),
            LiveStreamlineStage::token, "slGetNewFrameToken");
        if (token == nullptr) {
            throw LiveStreamlineStatus{false, LiveStreamlineStage::token,
                "slGetNewFrameToken returned null"};
        }
        const sl::Constants slConstants = convertConstants(constants);
        requireResult(slSetConstants(slConstants, *token, state.viewport),
            LiveStreamlineStage::constants, "slSetConstants");
        sl::Resource color(sl::ResourceType::eTex2d, resources.color,
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
        sl::Resource depth(sl::ResourceType::eTex2d, resources.depth,
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
        sl::Resource motion(sl::ResourceType::eTex2d, resources.motion,
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE);
        sl::Resource output(sl::ResourceType::eTex2d, resources.output,
            D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
        const sl::Extent renderExtent{0, 0, resources.renderWidth, resources.renderHeight};
        const sl::Extent outputExtent{0, 0, resources.outputWidth, resources.outputHeight};
        sl::ResourceTag tags[] = {
            {&color, sl::kBufferTypeScalingInputColor,
                sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent},
            {&output, sl::kBufferTypeScalingOutputColor,
                sl::ResourceLifecycle::eValidUntilEvaluate, &outputExtent},
            {&depth, sl::kBufferTypeDepth,
                sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent},
            {&motion, sl::kBufferTypeMotionVectors,
                sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent}};
        requireResult(slSetTagForFrame(*token, state.viewport, tags,
            static_cast<std::uint32_t>(std::size(tags)), resources.commandList),
            LiveStreamlineStage::tags, "slSetTagForFrame");
        const sl::BaseStructure* inputs[] = {&state.viewport};
        requireResult(slEvaluateFeature(sl::kFeatureDLSS, *token, inputs,
            static_cast<std::uint32_t>(std::size(inputs)), resources.commandList),
            LiveStreamlineStage::evaluate, "slEvaluateFeature");
        state.status = {true, LiveStreamlineStage::ready,
            "Live Streamline DLSS evaluation recorded"};
        return state.status;
    } catch (const LiveStreamlineStatus& failure) {
        impl_->status = failure;
        return failure;
    } catch (const std::exception& error) {
        impl_->status = {false, LiveStreamlineStage::evaluate, error.what()};
        return impl_->status;
    } catch (...) {
        impl_->status = {false, LiveStreamlineStage::evaluate,
            "Unknown live Streamline evaluation failure"};
        return impl_->status;
    }
#else
    (void)resources; (void)constants;
    return impl_->status;
#endif
}

LiveStreamlineStatus LiveStreamlineRuntime::freeResources() noexcept {
#ifdef MC_DLSS_HAS_STREAMLINE
    if (impl_->resourcesFreed) {
        return {true, LiveStreamlineStage::freeResources,
            "Live Streamline resources already freed"};
    }
    const sl::Result result = slFreeResources(sl::kFeatureDLSS, impl_->viewport);
    if (result != sl::Result::eOk) {
        return {false, LiveStreamlineStage::freeResources,
            resultMessage("slFreeResources", result)};
    }
    impl_->resourcesFreed = true;
    return {true, LiveStreamlineStage::freeResources,
        "Live Streamline viewport resources freed"};
#else
    return impl_->status;
#endif
}

bool LiveStreamlineRuntime::processGlobalStateRetained() const noexcept {
    return impl_->processGlobalStateRetained;
}

void shutdownLiveStreamlineProcess() noexcept {
#ifdef MC_DLSS_HAS_STREAMLINE
    std::lock_guard<std::mutex> lock(streamlineInitializationMutex);
    if (streamlineInitialized) {
        slShutdown();
        streamlineInitialized = false;
        streamlineDeviceSet = false;
    }
#endif
}

}
