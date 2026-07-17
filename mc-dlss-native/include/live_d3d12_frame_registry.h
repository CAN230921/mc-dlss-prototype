#pragma once

#include <cstdint>

#include "live_d3d12_frame_session.h"

namespace mc_dlss {

struct LiveD3D12FrameSessionInfo {
    std::uint64_t sessionId = 0;
    std::uintptr_t colorTextureHandle = 0;
    std::uintptr_t depthTextureHandle = 0;
    std::uintptr_t motionTextureHandle = 0;
    std::uintptr_t fenceHandle = 0;

    bool available() const noexcept {
        return sessionId != 0 && colorTextureHandle != 0
            && depthTextureHandle != 0 && motionTextureHandle != 0
            && fenceHandle != 0;
    }
};

LiveD3D12FrameSessionInfo openPersistentD3D12FrameSession(
    std::uint32_t width,
    std::uint32_t height) noexcept;
bool submitPersistentD3D12FrameReadback(
    std::uint64_t sessionId,
    std::uint64_t waitValue,
    std::uint64_t signalValue) noexcept;
FrameReadbackFingerprint inspectPersistentD3D12FrameReadback(
    std::uint64_t sessionId,
    std::uint64_t signalValue) noexcept;
void closePersistentD3D12FrameSession(std::uint64_t sessionId) noexcept;
bool hasPersistentD3D12FrameSession(std::uint64_t sessionId) noexcept;

}
