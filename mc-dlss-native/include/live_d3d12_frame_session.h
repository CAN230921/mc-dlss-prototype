#pragma once

#include <cstdint>
#include <memory>

#include "depth_readback_fingerprint.h"
#include "interop_test_pattern.h"
#include "motion_readback_fingerprint.h"

namespace mc_dlss {

struct FrameReadbackFingerprint {
    InteropReadbackStatus status = InteropReadbackStatus::unavailable;
    InteropReadbackFingerprint color;
    DepthReadbackFingerprint depth;
    MotionReadbackFingerprint motion;
    std::uint64_t completedFenceValue = 0;
    std::uint64_t lastSubmittedSignal = 0;
};

class LiveD3D12FrameSession {
public:
    static std::shared_ptr<LiveD3D12FrameSession> create(
        std::uint32_t width,
        std::uint32_t height);

    ~LiveD3D12FrameSession();
    LiveD3D12FrameSession(const LiveD3D12FrameSession&) = delete;
    LiveD3D12FrameSession& operator=(const LiveD3D12FrameSession&) = delete;

    std::uintptr_t colorTextureHandle() const noexcept;
    std::uintptr_t depthTextureHandle() const noexcept;
    std::uintptr_t motionTextureHandle() const noexcept;
    std::uintptr_t fenceHandle() const noexcept;
    bool submitReadback(std::uint64_t waitValue, std::uint64_t signalValue);
    FrameReadbackFingerprint inspectReadback(std::uint64_t signalValue);

private:
    struct Impl;
    LiveD3D12FrameSession(std::uint32_t width, std::uint32_t height);
    std::unique_ptr<Impl> impl_;
};

}
