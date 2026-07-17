#pragma once

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <d3d12.h>

#include <array>
#include <cstdint>
#include <memory>
#include <string>

namespace mc_dlss {

enum class LiveDlssQualityMode : std::uint32_t {
    quality = 0,
    balanced = 1,
    performance = 2,
    ultraPerformance = 3,
};

enum class LiveStreamlineStage : std::uint32_t {
    none = 0,
    initialize = 1,
    device = 2,
    support = 3,
    settings = 4,
    options = 5,
    token = 6,
    constants = 7,
    tags = 8,
    evaluate = 9,
    freeResources = 10,
    ready = 11,
};

const char* liveStreamlineStageName(LiveStreamlineStage stage) noexcept;

struct LiveStreamlineStatus {
    bool success = false;
    LiveStreamlineStage stage = LiveStreamlineStage::none;
    std::string message;
};

class LiveStreamlineStageBackend {
public:
    virtual ~LiveStreamlineStageBackend() = default;
    virtual bool invoke(LiveStreamlineStage stage) = 0;
};

LiveStreamlineStatus runLiveStreamlineStageContract(
    LiveStreamlineStageBackend& backend) noexcept;

class LiveStreamlineFrameSequence {
public:
    bool next(std::uint32_t& frameIndex) noexcept;

private:
    std::uint64_t nextFrameIndex_ = 0;
};

struct LiveDlssOptimalSettingsNative {
    bool available = false;
    std::uint32_t renderWidth = 0;
    std::uint32_t renderHeight = 0;
    float sharpness = 0.0f;
};

struct LiveDlssConstantsData {
    std::array<float, 16> cameraViewToClip{};
    std::array<float, 16> clipToCameraView{};
    std::array<float, 16> clipToPrevClip{};
    std::array<float, 16> prevClipToClip{};
    std::array<float, 3> cameraPosition{};
    std::array<float, 3> cameraUp{};
    std::array<float, 3> cameraRight{};
    std::array<float, 3> cameraForward{};
    float cameraNear = 0.0f;
    float cameraFar = 0.0f;
    float cameraFov = 0.0f;
    float cameraAspect = 0.0f;
    float jitterX = 0.0f;
    float jitterY = 0.0f;
    float motionScaleX = 0.0f;
    float motionScaleY = 0.0f;
    bool reset = true;
};

struct LiveDlssEvaluationResources {
    ID3D12Resource* color = nullptr;
    ID3D12Resource* depth = nullptr;
    ID3D12Resource* motion = nullptr;
    ID3D12Resource* output = nullptr;
    ID3D12GraphicsCommandList* commandList = nullptr;
    std::uint32_t renderWidth = 0;
    std::uint32_t renderHeight = 0;
    std::uint32_t outputWidth = 0;
    std::uint32_t outputHeight = 0;
};

class LiveStreamlineRuntime {
public:
    static std::unique_ptr<LiveStreamlineRuntime> create(
        ID3D12Device* device,
        LUID adapterLuid,
        std::uint32_t outputWidth,
        std::uint32_t outputHeight,
        const std::wstring& pluginPath,
        const std::wstring& logPath,
        LiveDlssQualityMode qualityMode = LiveDlssQualityMode::quality) noexcept;

    ~LiveStreamlineRuntime();
    LiveStreamlineRuntime(const LiveStreamlineRuntime&) = delete;
    LiveStreamlineRuntime& operator=(const LiveStreamlineRuntime&) = delete;

    bool available() const noexcept;
    LiveDlssOptimalSettingsNative optimalSettings() const noexcept;
    LiveStreamlineStatus status() const;
    LiveStreamlineStatus evaluate(
        const LiveDlssEvaluationResources& resources,
        const LiveDlssConstantsData& constants) noexcept;
    LiveStreamlineStatus freeResources() noexcept;
    bool processGlobalStateRetained() const noexcept;

private:
    struct Impl;
    LiveStreamlineRuntime();
    std::unique_ptr<Impl> impl_;
};

void shutdownLiveStreamlineProcess() noexcept;

}
