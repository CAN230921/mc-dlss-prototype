#include "native_bootstrap.h"

#include <filesystem>
#include <iostream>

int main() {
    if (mc_dlss::prepareNativeBundleDirectory(L"relative").empty()) {
        std::cerr << "relative bootstrap directory was accepted\n";
        return 1;
    }
    const auto directory = std::filesystem::temp_directory_path()
        / "mc-dlss-bootstrap-test";
    std::filesystem::create_directories(directory);
    const auto first = mc_dlss::prepareNativeBundleDirectory(directory.wstring());
    const auto second = mc_dlss::prepareNativeBundleDirectory(directory.wstring());
    if (!first.empty() || !second.empty()) {
        std::cerr << "bootstrap directory setup was not idempotent: "
                  << first << ' ' << second << '\n';
        return 1;
    }
    std::cout << "native_bootstrap_test passed\n";
    return 0;
}
