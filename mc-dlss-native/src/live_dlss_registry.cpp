#include "live_dlss_registry.h"
#include "live_streamline_runtime.h"

#include <cmath>
#include <limits>
#include <mutex>
#include <unordered_map>

namespace mc_dlss {
namespace {

std::mutex registryMutex;
std::unordered_map<std::uint64_t, std::shared_ptr<LiveDlssSession>> sessions;
std::uint64_t nextSessionId = 1;

}

std::array<std::uint32_t, 2> liveDlssRenderDimensions(
    std::uint32_t outputWidth, std::uint32_t outputHeight,
    LiveDlssQualityMode qualityMode) noexcept {
    double scale = 2.0 / 3.0;
    switch (qualityMode) {
        case LiveDlssQualityMode::balanced: scale = 0.58; break;
        case LiveDlssQualityMode::performance: scale = 0.5; break;
        case LiveDlssQualityMode::ultraPerformance: scale = 1.0 / 3.0; break;
        default: break;
    }
    return {
        static_cast<std::uint32_t>(std::lround(outputWidth * scale)),
        static_cast<std::uint32_t>(std::lround(outputHeight * scale))};
}

LiveDlssSessionInfo openLiveDlssSession(
    std::uint32_t renderWidth, std::uint32_t renderHeight,
    std::uint32_t outputWidth, std::uint32_t outputHeight) noexcept {
    try {
        auto session = LiveDlssSession::create(
            renderWidth, renderHeight, outputWidth, outputHeight);
        std::lock_guard<std::mutex> lock(registryMutex);
        if (nextSessionId == 0
            || nextSessionId == (std::numeric_limits<std::uint64_t>::max)()) {
            return {};
        }
        const std::uint64_t id = nextSessionId++;
        LiveDlssSessionInfo info{};
        info.sessionId = id;
        info.renderWidth = renderWidth;
        info.renderHeight = renderHeight;
        info.outputWidth = outputWidth;
        info.outputHeight = outputHeight;
        info.slots[0] = session->slotInfo(0);
        info.slots[1] = session->slotInfo(1);
        sessions.emplace(id, std::move(session));
        return info;
    } catch (...) {
        return {};
    }
}

LiveDlssSessionInfo openLiveDlssQualitySession(
    std::uint32_t outputWidth, std::uint32_t outputHeight,
    const std::wstring& pluginPath, const std::wstring& logPath,
    LiveDlssQualityMode qualityMode) noexcept {
    if (outputWidth < 2U || outputHeight < 2U
        || outputWidth > 16384U || outputHeight > 16384U) {
        return {};
    }
    const auto renderSize = liveDlssRenderDimensions(
        outputWidth, outputHeight, qualityMode);
    const std::uint32_t renderWidth = renderSize[0];
    const std::uint32_t renderHeight = renderSize[1];
    LiveDlssSessionInfo info = openLiveDlssSession(
        renderWidth, renderHeight, outputWidth, outputHeight);
    if (!info.available()) return {};
    const auto session = findLiveDlssSession(info.sessionId);
    const auto status = session->initializeStreamline(
        pluginPath, logPath, qualityMode);
    const auto optimal = session->optimalSettings();
    if (!status.success || !optimal.available
        || optimal.renderWidth != renderWidth || optimal.renderHeight != renderHeight) {
        closeLiveDlssSession(info.sessionId);
        return {};
    }
    return info;
}

LiveDlssSessionInfo openLiveFsr3Session(
    std::uint32_t outputWidth, std::uint32_t outputHeight,
    const std::wstring& loaderPath,
    LiveDlssQualityMode qualityMode) noexcept {
    if (outputWidth < 2U || outputHeight < 2U ||
        outputWidth > 16384U || outputHeight > 16384U) return {};
    const auto renderSize = liveDlssRenderDimensions(
        outputWidth, outputHeight, qualityMode);
    LiveDlssSessionInfo info = openLiveDlssSession(
        renderSize[0], renderSize[1], outputWidth, outputHeight);
    if (!info.available()) return {};
    const auto session = findLiveDlssSession(info.sessionId);
    const auto capability = session->initializeFsr3(loaderPath);
    if (capability.upscaler.state != Fsr3FeatureState::ContextReady) {
        closeLiveDlssSession(info.sessionId);
        return {};
    }
    return info;
}

bool submitLiveDlssEvaluation(
    std::uint64_t sessionId, std::size_t slotIndex,
    std::uint64_t waitValue, std::uint64_t signalValue,
    const LiveDlssConstantsData& constants,
    LiveUpscalerExecutionMode mode) noexcept {
    const auto session = findLiveDlssSession(sessionId);
    return session != nullptr && session->submitEvaluation(
        slotIndex, waitValue, signalValue, constants, mode);
}

bool submitLiveFsr3Upscale(
    std::uint64_t sessionId, std::size_t slotIndex,
    std::uint64_t waitValue, std::uint64_t signalValue,
    const LiveDlssConstantsData& constants,
    float frameTimeMilliseconds,
    bool generateFrame) noexcept {
    const auto session = findLiveDlssSession(sessionId);
    return session != nullptr && session->submitFsr3Upscale(
        slotIndex, waitValue, signalValue, constants,
        frameTimeMilliseconds, generateFrame);
}

DxgiPresentationResult presentLiveFsr3DxgiFrame(
    std::uint64_t sessionId, std::uintptr_t windowHandle,
    std::size_t slotIndex, std::uint64_t signalValue,
    std::uint32_t width, std::uint32_t height) noexcept {
    const auto session = findLiveDlssSession(sessionId);
    return session == nullptr
        ? DxgiPresentationResult{}
        : session->presentFsr3DxgiFrame(
            windowHandle, slotIndex, signalValue, width, height);
}

bool isLiveUpscalerSlotReady(
    std::uint64_t sessionId, std::size_t slotIndex) noexcept {
    const auto session = findLiveDlssSession(sessionId);
    return session != nullptr && session->slotReady(slotIndex);
}

LiveDlssFrameInspection inspectLiveDlssEvaluation(
    std::uint64_t sessionId, std::size_t slotIndex,
    std::uint64_t signalValue) noexcept {
    const auto session = findLiveDlssSession(sessionId);
    if (session == nullptr) {
        LiveDlssFrameInspection result{};
        result.message = "Missing live DLSS session";
        return result;
    }
    return session->inspectEvaluation(slotIndex, signalValue);
}

std::shared_ptr<LiveDlssSession> findLiveDlssSession(std::uint64_t id) noexcept {
    std::lock_guard<std::mutex> lock(registryMutex);
    const auto found = sessions.find(id);
    return found == sessions.end() ? nullptr : found->second;
}

void closeLiveDlssSession(std::uint64_t id) noexcept {
    std::shared_ptr<LiveDlssSession> removed;
    std::lock_guard<std::mutex> lock(registryMutex);
    const auto found = sessions.find(id);
    if (found != sessions.end()) {
        removed = std::move(found->second);
        sessions.erase(found);
    }
}

bool hasLiveDlssSession(std::uint64_t id) noexcept {
    return findLiveDlssSession(id) != nullptr;
}

void shutdownLiveDlssProcess() noexcept {
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        sessions.clear();
    }
    shutdownLiveStreamlineProcess();
}

}
