#include "live_d3d12_interop_registry.h"

#include "live_d3d12_interop_session.h"

#include <limits>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <cstdio>
#include <exception>

namespace mc_dlss {
namespace {

std::mutex registryMutex;
std::unordered_map<std::uint64_t, std::shared_ptr<LiveD3D12InteropSession>> sessions;
std::uint64_t nextSessionId = 1;

std::shared_ptr<LiveD3D12InteropSession> findSession(std::uint64_t sessionId) {
    std::lock_guard<std::mutex> lock(registryMutex);
    const auto found = sessions.find(sessionId);
    return found == sessions.end() ? nullptr : found->second;
}

LiveD3D12InteropSessionInfo registerSession(
    std::shared_ptr<LiveD3D12InteropSession> session) {
    std::lock_guard<std::mutex> lock(registryMutex);
    if (nextSessionId == 0 || nextSessionId == std::numeric_limits<std::uint64_t>::max()) {
        return {};
    }
    const std::uint64_t sessionId = nextSessionId++;
    const LiveD3D12InteropSessionInfo info{
        sessionId, session->textureHandle(), session->fenceHandle()};
    sessions.emplace(sessionId, std::move(session));
    return info;
}

}

LiveD3D12InteropSessionInfo openLiveD3D12InteropSession(
    std::uint32_t width,
    std::uint32_t height) noexcept {
    try {
        if (width != 64 || height != 64) {
            return {};
        }
        return registerSession(LiveD3D12InteropSession::create(width, height));
    } catch (...) {
        return {};
    }
}

LiveD3D12InteropSessionInfo openPersistentD3D12InteropSession(
    std::uint32_t width,
    std::uint32_t height) noexcept {
    try {
        if (width == 0 || height == 0 || width > 8192 || height > 8192) {
            return {};
        }
        return registerSession(LiveD3D12InteropSession::create(width, height));
    } catch (...) {
        return {};
    }
}

bool submitLiveD3D12InteropReadback(std::uint64_t sessionId) noexcept {
    try {
        const auto session = findSession(sessionId);
        return session != nullptr && session->submitReadback();
    } catch (...) {
        return false;
    }
}

bool verifyLiveD3D12InteropReadback(std::uint64_t sessionId) noexcept {
    try {
        const auto session = findSession(sessionId);
        return session != nullptr && session->verifyReadback();
    } catch (...) {
        return false;
    }
}

InteropReadbackFingerprint inspectLiveD3D12InteropReadback(
    std::uint64_t sessionId) noexcept {
    try {
        const auto session = findSession(sessionId);
        return session == nullptr ? InteropReadbackFingerprint{} :
            session->inspectReadback();
    } catch (...) {
        return {};
    }
}

bool submitPersistentD3D12InteropReadback(
    std::uint64_t sessionId,
    std::uint64_t waitValue,
    std::uint64_t signalValue) noexcept {
    try {
        const auto session = findSession(sessionId);
        return session != nullptr && session->submitReadback(waitValue, signalValue);
    } catch (...) {
        return false;
    }
}

InteropReadbackFingerprint inspectPersistentD3D12InteropReadback(
    std::uint64_t sessionId,
    std::uint64_t signalValue) noexcept {
    try {
        const auto session = findSession(sessionId);
        if (session == nullptr) {
            InteropReadbackFingerprint result{};
            result.status = InteropReadbackStatus::missingSession;
            return result;
        }
        return session->inspectReadback(signalValue);
    } catch (const std::exception& error) {
        std::fprintf(
            stderr,
            "[mc_dlss/native/persistent] inspect exception: %s\n",
            error.what());
        InteropReadbackFingerprint result{};
        result.status = InteropReadbackStatus::exception;
        return result;
    } catch (...) {
        std::fprintf(stderr, "[mc_dlss/native/persistent] inspect unknown exception\n");
        InteropReadbackFingerprint result{};
        result.status = InteropReadbackStatus::exception;
        return result;
    }
}

void closeLiveD3D12InteropSession(std::uint64_t sessionId) noexcept {
    std::shared_ptr<LiveD3D12InteropSession> removed;
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        const auto found = sessions.find(sessionId);
        if (found == sessions.end()) {
            return;
        }
        removed = std::move(found->second);
        sessions.erase(found);
    }
}

bool hasLiveD3D12InteropSession(std::uint64_t sessionId) noexcept {
    return findSession(sessionId) != nullptr;
}

}
