#pragma once

#include <cstdint>
#include <memory>

#include "interop_test_pattern.h"

namespace mc_dlss {

bool validPersistentFencePair(
    std::uint64_t previousSignalValue,
    std::uint64_t waitValue,
    std::uint64_t signalValue) noexcept;

class LiveD3D12InteropSession {
public:
    static std::shared_ptr<LiveD3D12InteropSession> create(
        std::uint32_t width,
        std::uint32_t height);

    ~LiveD3D12InteropSession();

    LiveD3D12InteropSession(const LiveD3D12InteropSession&) = delete;
    LiveD3D12InteropSession& operator=(const LiveD3D12InteropSession&) = delete;

    std::uintptr_t textureHandle() const noexcept;
    std::uintptr_t fenceHandle() const noexcept;
    bool submitReadback();
    bool submitReadback(std::uint64_t waitValue, std::uint64_t signalValue);
    bool verifyReadback();
    InteropReadbackFingerprint inspectReadback();
    InteropReadbackFingerprint inspectReadback(std::uint64_t signalValue);

private:
    struct Impl;

    LiveD3D12InteropSession(std::uint32_t width, std::uint32_t height);

    std::unique_ptr<Impl> impl_;
};

}
