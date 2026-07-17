#include "live_d3d12_frame_registry.h"

#include <limits>
#include <memory>
#include <mutex>
#include <unordered_map>

namespace mc_dlss {
namespace {

std::mutex frameRegistryMutex;
std::unordered_map<std::uint64_t, std::shared_ptr<LiveD3D12FrameSession>> frameSessions;
std::uint64_t nextFrameSessionId = 1;

std::shared_ptr<LiveD3D12FrameSession> findFrameSession(std::uint64_t sessionId) {
    std::lock_guard<std::mutex> lock(frameRegistryMutex);
    const auto found = frameSessions.find(sessionId);
    return found == frameSessions.end() ? nullptr : found->second;
}

}

LiveD3D12FrameSessionInfo openPersistentD3D12FrameSession(
    std::uint32_t width,
    std::uint32_t height) noexcept {
    try {
        if (width == 0 || height == 0 || width > 8192 || height > 8192) {
            return {};
        }
        auto session = LiveD3D12FrameSession::create(width, height);
        std::lock_guard<std::mutex> lock(frameRegistryMutex);
        if (nextFrameSessionId == 0
            || nextFrameSessionId == std::numeric_limits<std::uint64_t>::max()) {
            return {};
        }
        const std::uint64_t sessionId = nextFrameSessionId++;
        LiveD3D12FrameSessionInfo info{
            sessionId,
            session->colorTextureHandle(),
            session->depthTextureHandle(),
            session->motionTextureHandle(),
            session->fenceHandle()};
        frameSessions.emplace(sessionId, std::move(session));
        return info;
    } catch (...) {
        return {};
    }
}

bool submitPersistentD3D12FrameReadback(
    std::uint64_t sessionId,
    std::uint64_t waitValue,
    std::uint64_t signalValue) noexcept {
    try {
        const auto session = findFrameSession(sessionId);
        return session != nullptr && session->submitReadback(waitValue, signalValue);
    } catch (...) {
        return false;
    }
}

FrameReadbackFingerprint inspectPersistentD3D12FrameReadback(
    std::uint64_t sessionId,
    std::uint64_t signalValue) noexcept {
    try {
        const auto session = findFrameSession(sessionId);
        if (session == nullptr) {
            FrameReadbackFingerprint result{};
            result.status = InteropReadbackStatus::missingSession;
            return result;
        }
        return session->inspectReadback(signalValue);
    } catch (...) {
        FrameReadbackFingerprint result{};
        result.status = InteropReadbackStatus::exception;
        return result;
    }
}

void closePersistentD3D12FrameSession(std::uint64_t sessionId) noexcept {
    std::shared_ptr<LiveD3D12FrameSession> removed;
    std::lock_guard<std::mutex> lock(frameRegistryMutex);
    const auto found = frameSessions.find(sessionId);
    if (found != frameSessions.end()) {
        removed = std::move(found->second);
        frameSessions.erase(found);
    }
}

bool hasPersistentD3D12FrameSession(std::uint64_t sessionId) noexcept {
    return findFrameSession(sessionId) != nullptr;
}

}
