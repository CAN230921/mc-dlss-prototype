#pragma once

#include "live_dlss_session.h"

#include <array>
#include <cstdint>
#include <memory>

namespace mc_dlss {

std::array<std::uint32_t, 2> liveDlssRenderDimensions(
    std::uint32_t outputWidth, std::uint32_t outputHeight,
    LiveDlssQualityMode qualityMode) noexcept;

struct LiveDlssSessionInfo {
    std::uint64_t sessionId = 0;
    std::uint32_t renderWidth = 0;
    std::uint32_t renderHeight = 0;
    std::uint32_t outputWidth = 0;
    std::uint32_t outputHeight = 0;
    std::array<LiveDlssSlotInfo, 2> slots{};

    bool available() const noexcept {
        return sessionId != 0 && renderWidth != 0 && renderHeight != 0
            && outputWidth > renderWidth && outputHeight > renderHeight
            && slots[0].available() && slots[1].available();
    }
};

LiveDlssSessionInfo openLiveDlssSession(
    std::uint32_t renderWidth, std::uint32_t renderHeight,
    std::uint32_t outputWidth, std::uint32_t outputHeight) noexcept;
LiveDlssSessionInfo openLiveDlssQualitySession(
    std::uint32_t outputWidth, std::uint32_t outputHeight,
    const std::wstring& pluginPath, const std::wstring& logPath,
    LiveDlssQualityMode qualityMode = LiveDlssQualityMode::quality) noexcept;
LiveDlssSessionInfo openLiveFsr3Session(
    std::uint32_t outputWidth, std::uint32_t outputHeight,
    const std::wstring& loaderPath,
    LiveDlssQualityMode qualityMode = LiveDlssQualityMode::quality) noexcept;
bool submitLiveDlssEvaluation(
    std::uint64_t sessionId, std::size_t slotIndex,
    std::uint64_t waitValue, std::uint64_t signalValue,
    const LiveDlssConstantsData& constants,
    LiveUpscalerExecutionMode mode =
        LiveUpscalerExecutionMode::validating) noexcept;
bool submitLiveFsr3Upscale(
    std::uint64_t sessionId, std::size_t slotIndex,
    std::uint64_t waitValue, std::uint64_t signalValue,
    const LiveDlssConstantsData& constants,
    float frameTimeMilliseconds,
    bool generateFrame) noexcept;
DxgiPresentationResult presentLiveFsr3DxgiFrame(
    std::uint64_t sessionId, std::uintptr_t windowHandle,
    std::size_t slotIndex, std::uint64_t signalValue,
    std::uint32_t width, std::uint32_t height) noexcept;
bool isLiveUpscalerSlotReady(
    std::uint64_t sessionId, std::size_t slotIndex) noexcept;
LiveDlssFrameInspection inspectLiveDlssEvaluation(
    std::uint64_t sessionId, std::size_t slotIndex,
    std::uint64_t signalValue) noexcept;
std::shared_ptr<LiveDlssSession> findLiveDlssSession(std::uint64_t sessionId) noexcept;
void closeLiveDlssSession(std::uint64_t sessionId) noexcept;
bool hasLiveDlssSession(std::uint64_t sessionId) noexcept;
void shutdownLiveDlssProcess() noexcept;

}
