#include "live_dlss_session.h"
#include "live_streamline_runtime.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <filesystem>
#include <iostream>

int wmain() {
    wchar_t executable[MAX_PATH]{};
    if (GetModuleFileNameW(nullptr, executable, MAX_PATH) == 0) {
        std::cerr << "cannot locate live Streamline test executable\n";
        return 1;
    }
    const std::filesystem::path runtimeDirectory =
        std::filesystem::path(executable).parent_path();
    auto session = mc_dlss::LiveDlssSession::create(569, 320, 854, 480);
    auto runtime = mc_dlss::LiveStreamlineRuntime::create(
        session->device(), session->adapterLuid(), 854, 480,
        runtimeDirectory.wstring(), runtimeDirectory.wstring());
    if (!runtime->available()) {
        const auto status = runtime->status();
        std::cerr << mc_dlss::liveStreamlineStageName(status.stage)
                  << ": " << status.message << '\n';
        return 1;
    }
    const auto settings = runtime->optimalSettings();
    std::cout << "optimal=" << settings.renderWidth << 'x'
              << settings.renderHeight << '\n';
    if (!settings.available || settings.renderWidth != 569
        || settings.renderHeight != 320) {
        std::cerr << "unexpected Quality optimal settings\n";
        return 1;
    }
    const auto freed = runtime->freeResources();
    if ((!freed.success
            && freed.stage != mc_dlss::LiveStreamlineStage::freeResources)
        || !runtime->processGlobalStateRetained()) {
        std::cerr << "live Streamline free/retention contract mismatch\n";
        return 1;
    }
    std::cout << "live_streamline_runtime_real_test passed\n";

    ID3D12Device* initialDevice = session->device();
    runtime.reset();
    session.reset();
    auto reopenedSession = mc_dlss::LiveDlssSession::create(640, 360, 960, 540);
    if (reopenedSession->device() != initialDevice) {
        std::cerr << "reopened session did not retain the Streamline D3D12 device\n";
        return 1;
    }
    auto reopenedRuntime = mc_dlss::LiveStreamlineRuntime::create(
        reopenedSession->device(), reopenedSession->adapterLuid(), 960, 540,
        runtimeDirectory.wstring(), runtimeDirectory.wstring());
    if (!reopenedRuntime->available()) {
        const auto status = reopenedRuntime->status();
        std::cerr << "reopen " << mc_dlss::liveStreamlineStageName(status.stage)
                  << ": " << status.message << '\n';
        return 1;
    }
    const auto reopenedSettings = reopenedRuntime->optimalSettings();
    if (!reopenedSettings.available || reopenedSettings.renderWidth != 640
        || reopenedSettings.renderHeight != 360) {
        std::cerr << "unexpected reopened Quality optimal settings\n";
        return 1;
    }
    std::cout << "live_streamline_runtime_reopen passed\n";

    runtime.reset();
    session.reset();
    reopenedRuntime.reset();
    reopenedSession.reset();
    auto balancedSession = mc_dlss::LiveDlssSession::create(990, 590, 1707, 1017);
    auto balancedRuntime = mc_dlss::LiveStreamlineRuntime::create(
        balancedSession->device(), balancedSession->adapterLuid(), 1707, 1017,
        runtimeDirectory.wstring(), runtimeDirectory.wstring(),
        mc_dlss::LiveDlssQualityMode::balanced);
    if (!balancedRuntime->available()) {
        const auto status = balancedRuntime->status();
        std::cerr << "balanced " << mc_dlss::liveStreamlineStageName(status.stage)
                  << ": " << status.message << '\n';
        return 1;
    }
    const auto balanced = balancedRuntime->optimalSettings();
    std::cout << "balanced current-window optimal=" << balanced.renderWidth
              << 'x' << balanced.renderHeight << " allocated=990x590\n";
    if (balanced.renderWidth != 990 || balanced.renderHeight != 590) {
        std::cerr << "balanced current-window allocation mismatch\n";
        return 1;
    }
    return 0;
}
