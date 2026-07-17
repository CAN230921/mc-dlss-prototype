#include "dxgi_window_present_probe.h"

#include <cstdint>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

extern "C" {
__declspec(dllexport) unsigned long NvOptimusEnablement = 0x00000001;
__declspec(dllexport) int AmdPowerXpressRequestHighPerformance = 1;
}

namespace {

std::uint32_t parseDimension(const std::string& value) {
    if (value.empty() || value.find_first_not_of("0123456789") !=
            std::string::npos) {
        throw std::invalid_argument("Invalid dimension: " + value);
    }
    std::size_t consumed = 0;
    const unsigned long parsed = std::stoul(value, &consumed, 10);
    if (consumed != value.size() || parsed == 0 ||
        parsed > static_cast<unsigned long>(
            std::numeric_limits<std::int32_t>::max())) {
        throw std::invalid_argument("Invalid dimension: " + value);
    }
    return static_cast<std::uint32_t>(parsed);
}

}

int main(int argc, char** argv) {
    std::uint32_t width = 320;
    std::uint32_t height = 180;
    std::uint32_t resizedWidth = 640;
    std::uint32_t resizedHeight = 360;
    std::string reportPath;

    try {
        for (int index = 1; index < argc; ++index) {
            const std::string argument = argv[index];
            if (index + 1 >= argc) {
                throw std::invalid_argument("Missing value for " + argument);
            }
            const std::string value = argv[++index];
            if (argument == "--width") {
                width = parseDimension(value);
            } else if (argument == "--height") {
                height = parseDimension(value);
            } else if (argument == "--resized-width") {
                resizedWidth = parseDimension(value);
            } else if (argument == "--resized-height") {
                resizedHeight = parseDimension(value);
            } else if (argument == "--report") {
                reportPath = value;
            } else {
                throw std::invalid_argument("Unknown option: " + argument);
            }
        }
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 2;
    }

    const auto snapshot = mc_dlss::runDxgiWindowPresentProbe(
        width, height, resizedWidth, resizedHeight);
    const std::string json = mc_dlss::toJson(snapshot);
    std::cout << json << '\n';

    if (!reportPath.empty()) {
        std::ofstream report(reportPath, std::ios::binary | std::ios::trunc);
        if (!report) {
            std::cerr << "Could not open report path: " << reportPath << '\n';
            return 4;
        }
        report << json << '\n';
        if (!report) {
            std::cerr << "Could not write report path: " << reportPath << '\n';
            return 4;
        }
    }

    return mc_dlss::validateDxgiWindowPresentProbeSnapshot(snapshot) ? 0 : 3;
}
