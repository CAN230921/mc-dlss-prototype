#include "streamline_dlss_probe.h"

#include "d3d12_probe_context.h"

#include <sl.h>
#include <sl_consts.h>
#include <sl_dlss.h>

#include <cmath>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>

namespace mc_dlss {
namespace {

constexpr std::uint32_t kExpectedRenderWidth = 1280;
constexpr std::uint32_t kExpectedRenderHeight = 720;
constexpr std::uint32_t kOutputWidth = 1920;
constexpr std::uint32_t kOutputHeight = 1080;

std::string resultName(sl::Result result) {
    if (result == sl::Result::eOk) {
        return "eOk";
    }
    return "result(" + std::to_string(static_cast<int>(result)) + ")";
}

void requireSl(
    sl::Result result,
    const char* operation,
    StreamlineProbeSnapshot& snapshot,
    bool& stage) {
    snapshot.lastResult = resultName(result);
    if (result != sl::Result::eOk) {
        throw std::runtime_error(
            std::string(operation) + " failed with " + snapshot.lastResult + '.');
    }
    stage = true;
}

sl::float4x4 identityMatrix() {
    sl::float4x4 matrix{};
    matrix.setRow(0, {1.0F, 0.0F, 0.0F, 0.0F});
    matrix.setRow(1, {0.0F, 1.0F, 0.0F, 0.0F});
    matrix.setRow(2, {0.0F, 0.0F, 1.0F, 0.0F});
    matrix.setRow(3, {0.0F, 0.0F, 0.0F, 1.0F});
    return matrix;
}

sl::Constants fixedConstants(
    std::uint32_t renderWidth,
    std::uint32_t renderHeight) {
    sl::Constants constants{};
    constants.cameraViewToClip = identityMatrix();
    constants.clipToCameraView = identityMatrix();
    constants.clipToLensClip = identityMatrix();
    constants.clipToPrevClip = identityMatrix();
    constants.prevClipToClip = identityMatrix();
    constants.jitterOffset = {0.0F, 0.0F};
    constants.mvecScale = {
        1.0F / static_cast<float>(renderWidth),
        1.0F / static_cast<float>(renderHeight)};
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
    constants.depthInverted = sl::Boolean::eFalse;
    constants.cameraMotionIncluded = sl::Boolean::eTrue;
    constants.motionVectors3D = sl::Boolean::eFalse;
    constants.reset = sl::Boolean::eTrue;
    constants.orthographicProjection = sl::Boolean::eFalse;
    constants.motionVectorsDilated = sl::Boolean::eFalse;
    constants.motionVectorsJittered = sl::Boolean::eFalse;
    return constants;
}

}

StreamlineProbeSnapshot runStreamlineDlssProbe(
    const std::wstring& pluginPath,
    const std::wstring& logPath) noexcept {
    StreamlineProbeSnapshot snapshot{};
    snapshot.streamlineVersion = "2.12.0";
    snapshot.projectId = kStreamlineProbeProjectId;
    snapshot.outputWidth = kOutputWidth;
    snapshot.outputHeight = kOutputHeight;

    bool initialized = false;
    std::unique_ptr<D3D12ProbeContext> context;
    std::string failureMessage;

    try {
        const sl::Feature features[] = {sl::kFeatureDLSS};
        const wchar_t* pluginPaths[] = {pluginPath.c_str()};

        sl::Preferences preferences{};
        preferences.showConsole = false;
        preferences.logLevel = sl::LogLevel::eDefault;
        preferences.pathsToPlugins = pluginPaths;
        preferences.numPathsToPlugins = 1;
        preferences.pathToLogsAndData = logPath.c_str();
        preferences.flags =
            sl::PreferenceFlags::eDisableCLStateTracking |
            sl::PreferenceFlags::eUseFrameBasedResourceTagging;
        preferences.featuresToLoad = features;
        preferences.numFeaturesToLoad = 1;
        preferences.applicationId = 0;
        preferences.engine = sl::EngineType::eCustom;
        preferences.engineVersion = kStreamlineProbeEngineVersion;
        preferences.projectId = kStreamlineProbeProjectId;
        preferences.renderAPI = sl::RenderAPI::eD3D12;

        requireSl(
            slInit(preferences, sl::kSDKVersion),
            "slInit", snapshot, snapshot.streamlineInitialized);
        initialized = true;

        context = D3D12ProbeContext::create(
            kExpectedRenderWidth,
            kExpectedRenderHeight,
            kOutputWidth,
            kOutputHeight);
        const ProbeSnapshot d3dSnapshot = context->snapshot();
        snapshot.adapterName = d3dSnapshot.adapterName;

        requireSl(
            slSetD3DDevice(context->device()),
            "slSetD3DDevice", snapshot, snapshot.d3dDeviceAccepted);

        LUID adapterLuid = context->adapterLuid();
        sl::AdapterInfo adapterInfo{};
        adapterInfo.deviceLUID = reinterpret_cast<std::uint8_t*>(&adapterLuid);
        adapterInfo.deviceLUIDSizeInBytes = sizeof(adapterLuid);
        requireSl(
            slIsFeatureSupported(sl::kFeatureDLSS, adapterInfo),
            "slIsFeatureSupported", snapshot, snapshot.dlssSupported);

        sl::DLSSOptions options{};
        options.mode = sl::DLSSMode::eMaxQuality;
        options.outputWidth = kOutputWidth;
        options.outputHeight = kOutputHeight;
        options.colorBuffersHDR = sl::Boolean::eTrue;
        options.useAutoExposure = sl::Boolean::eTrue;
        options.alphaUpscalingEnabled = sl::Boolean::eFalse;

        sl::DLSSOptimalSettings optimalSettings{};
        bool settingsAvailable = false;
        requireSl(
            slDLSSGetOptimalSettings(options, optimalSettings),
            "slDLSSGetOptimalSettings", snapshot, settingsAvailable);
        snapshot.optimalRenderWidth = optimalSettings.optimalRenderWidth;
        snapshot.optimalRenderHeight = optimalSettings.optimalRenderHeight;
        if (snapshot.optimalRenderWidth != kExpectedRenderWidth ||
            snapshot.optimalRenderHeight != kExpectedRenderHeight) {
            throw std::runtime_error(
                "DLSS optimal render size does not match the probe resources.");
        }

        options.sharpness = optimalSettings.optimalSharpness;
        const sl::ViewportHandle viewport(0U);
        requireSl(
            slDLSSSetOptions(viewport, options),
            "slDLSSSetOptions", snapshot, snapshot.optionsAccepted);

        std::uint32_t frameIndex = 0;
        sl::FrameToken* frameToken = nullptr;
        bool frameTokenAvailable = false;
        requireSl(
            slGetNewFrameToken(frameToken, &frameIndex),
            "slGetNewFrameToken", snapshot, frameTokenAvailable);
        if (frameToken == nullptr) {
            throw std::runtime_error("slGetNewFrameToken returned a null token.");
        }

        const sl::Constants constants = fixedConstants(
            snapshot.optimalRenderWidth, snapshot.optimalRenderHeight);
        requireSl(
            slSetConstants(constants, *frameToken, viewport),
            "slSetConstants", snapshot, snapshot.constantsAccepted);

        sl::Resource inputColor(
            sl::ResourceType::eTex2d,
            context->inputColor(),
            context->inputColorState());
        sl::Resource depth(
            sl::ResourceType::eTex2d,
            context->depth(),
            context->depthState());
        sl::Resource motionVectors(
            sl::ResourceType::eTex2d,
            context->motionVectors(),
            context->motionVectorsState());
        sl::Resource outputColor(
            sl::ResourceType::eTex2d,
            context->outputColor(),
            context->outputColorState());

        const sl::Extent renderExtent{
            0, 0, snapshot.optimalRenderWidth, snapshot.optimalRenderHeight};
        const sl::Extent outputExtent{0, 0, kOutputWidth, kOutputHeight};
        sl::ResourceTag tags[] = {
            {&inputColor, sl::kBufferTypeScalingInputColor,
             sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent},
            {&outputColor, sl::kBufferTypeScalingOutputColor,
             sl::ResourceLifecycle::eValidUntilEvaluate, &outputExtent},
            {&depth, sl::kBufferTypeDepth,
             sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent},
            {&motionVectors, sl::kBufferTypeMotionVectors,
             sl::ResourceLifecycle::eValidUntilEvaluate, &renderExtent}
        };
        requireSl(
            slSetTagForFrame(
                *frameToken,
                viewport,
                tags,
                static_cast<std::uint32_t>(std::size(tags)),
                context->commandList()),
            "slSetTagForFrame", snapshot, snapshot.resourcesTagged);

        const sl::BaseStructure* inputs[] = {&viewport};
        snapshot.evaluationCalled = true;
        requireSl(
            slEvaluateFeature(
                sl::kFeatureDLSS,
                *frameToken,
                inputs,
                static_cast<std::uint32_t>(std::size(inputs)),
                context->commandList()),
            "slEvaluateFeature",
            snapshot,
            snapshot.streamlineDlssEvaluationSucceeded);

        snapshot.commandSubmissionCompleted = context->submitAndWait();
        if (!snapshot.commandSubmissionCompleted) {
            throw std::runtime_error("D3D12 command submission did not complete.");
        }

        requireSl(
            slFreeResources(sl::kFeatureDLSS, viewport),
            "slFreeResources", snapshot, snapshot.resourcesFreed);
    } catch (const std::exception& error) {
        failureMessage = error.what();
        if (snapshot.lastResult.empty() || snapshot.lastResult == "eOk") {
            snapshot.lastResult = "hostError";
        }
    } catch (...) {
        failureMessage = "Unknown Streamline DLSS probe failure.";
        snapshot.lastResult = "hostError";
    }

    if (initialized) {
        const sl::Result shutdownResult = slShutdown();
        snapshot.streamlineShutdownSucceeded = shutdownResult == sl::Result::eOk;
        if (!snapshot.streamlineShutdownSucceeded) {
            snapshot.lastResult = resultName(shutdownResult);
            if (failureMessage.empty()) {
                failureMessage = "slShutdown failed with " + snapshot.lastResult + '.';
            }
        }
    }

    context.reset();
    if (failureMessage.empty() && validateStreamlineProbeSnapshot(snapshot)) {
        snapshot.message = "Streamline DLSS evaluation completed.";
    } else {
        snapshot.message = failureMessage.empty()
            ? "Streamline DLSS evaluation did not satisfy the complete contract."
            : failureMessage;
    }
    return snapshot;
}

}
