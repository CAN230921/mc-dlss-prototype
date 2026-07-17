#pragma once

#include <cstdint>

#include "interop_test_pattern.h"

namespace mc_dlss {

struct LiveD3D12InteropSessionInfo {
    std::uint64_t sessionId = 0;
    std::uintptr_t textureHandle = 0;
    std::uintptr_t fenceHandle = 0;

    bool available() const noexcept {
        return sessionId != 0 && textureHandle != 0 && fenceHandle != 0;
    }
};

LiveD3D12InteropSessionInfo openLiveD3D12InteropSession(
    std::uint32_t width,
    std::uint32_t height) noexcept;

LiveD3D12InteropSessionInfo openPersistentD3D12InteropSession(
    std::uint32_t width,
    std::uint32_t height) noexcept;

bool submitLiveD3D12InteropReadback(std::uint64_t sessionId) noexcept;
bool verifyLiveD3D12InteropReadback(std::uint64_t sessionId) noexcept;
InteropReadbackFingerprint inspectLiveD3D12InteropReadback(
    std::uint64_t sessionId) noexcept;
bool submitPersistentD3D12InteropReadback(
    std::uint64_t sessionId,
    std::uint64_t waitValue,
    std::uint64_t signalValue) noexcept;
InteropReadbackFingerprint inspectPersistentD3D12InteropReadback(
    std::uint64_t sessionId,
    std::uint64_t signalValue) noexcept;
void closeLiveD3D12InteropSession(std::uint64_t sessionId) noexcept;
bool hasLiveD3D12InteropSession(std::uint64_t sessionId) noexcept;

}
