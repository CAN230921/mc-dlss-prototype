#pragma once

#include "fsr3_dxgi_presenter.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <d3d12.h>

#include <array>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>

#include "live_streamline_runtime.h"
#include "fsr3_capability_report.h"

namespace mc_dlss {

enum class LiveUpscalerExecutionMode : std::uint32_t {
    validating = 0,
    fast = 1,
};

struct LiveDlssSlotInfo {
    std::uintptr_t colorTextureHandle = 0;
    std::uintptr_t depthTextureHandle = 0;
    std::uintptr_t motionTextureHandle = 0;
    std::uintptr_t outputTextureHandle = 0;
    std::uintptr_t fenceHandle = 0;
    std::uintptr_t generatedTextureHandle = 0;

    bool available() const noexcept {
        return colorTextureHandle != 0 && depthTextureHandle != 0
            && motionTextureHandle != 0 && outputTextureHandle != 0
            && fenceHandle != 0;
    }
};

struct LiveDlssFrameInspection {
    bool available = false;
    bool diagnosticReadbackRequested = false;
    LiveStreamlineStage stage = LiveStreamlineStage::none;
    std::uint64_t outputHash = 0;
    bool outputNonUniform = false;
    std::uint32_t outputNonBlackPixelCount = 0;
    std::uint64_t completedFenceValue = 0;
    std::uint64_t lastSubmittedSignal = 0;
    double evaluationMilliseconds = 0.0;
    std::string message;
};

class LiveDlssSession {
public:
    static std::shared_ptr<LiveDlssSession> create(
        std::uint32_t renderWidth, std::uint32_t renderHeight,
        std::uint32_t outputWidth, std::uint32_t outputHeight);

    ~LiveDlssSession();
    LiveDlssSession(const LiveDlssSession&) = delete;
    LiveDlssSession& operator=(const LiveDlssSession&) = delete;

    LiveDlssSlotInfo slotInfo(std::size_t index) const noexcept;
    ID3D12Device* device() const noexcept;
    LUID adapterLuid() const noexcept;
    ID3D12Resource* colorTexture(std::size_t index) const noexcept;
    ID3D12Resource* depthTexture(std::size_t index) const noexcept;
    ID3D12Resource* motionTexture(std::size_t index) const noexcept;
    ID3D12Resource* outputTexture(std::size_t index) const noexcept;
    ID3D12Fence* fence(std::size_t index) const noexcept;
    bool slotReady(std::size_t index) const noexcept;
    LiveStreamlineStatus initializeStreamline(
        const std::wstring& pluginPath, const std::wstring& logPath,
        LiveDlssQualityMode qualityMode = LiveDlssQualityMode::quality) noexcept;
    Fsr3CapabilitySnapshot initializeFsr3(
        const std::wstring& loaderPath) noexcept;
    Fsr3CapabilitySnapshot fsr3Capability() const noexcept;
    LiveDlssOptimalSettingsNative optimalSettings() const noexcept;
    bool submitEvaluation(
        std::size_t slotIndex,
        std::uint64_t waitValue,
        std::uint64_t signalValue,
        const LiveDlssConstantsData& constants,
        LiveUpscalerExecutionMode mode =
            LiveUpscalerExecutionMode::validating) noexcept;
    bool submitFsr3Upscale(
        std::size_t slotIndex,
        std::uint64_t waitValue,
        std::uint64_t signalValue,
        const LiveDlssConstantsData& constants,
        float frameTimeMilliseconds,
        bool generateFrame) noexcept;
    DxgiPresentationResult presentFsr3DxgiFrame(
        std::uintptr_t windowHandle, std::size_t slotIndex,
        std::uint64_t signalValue,
        std::uint32_t width, std::uint32_t height) noexcept;
    LiveDlssFrameInspection inspectEvaluation(
        std::size_t slotIndex, std::uint64_t signalValue) noexcept;
    LiveStreamlineStatus freeStreamlineResources() noexcept;

private:
    struct Impl;
    LiveDlssSession(
        std::uint32_t renderWidth, std::uint32_t renderHeight,
        std::uint32_t outputWidth, std::uint32_t outputHeight);
    std::unique_ptr<Impl> impl_;
};

}
