#include "gl_d3d12_interop_probe.h"

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
    std::size_t consumed = 0;
    const unsigned long parsed = std::stoul(value, &consumed, 10);
    if (consumed != value.size() || parsed == 0 ||
        parsed > std::numeric_limits<std::uint32_t>::max()) {
        throw std::invalid_argument("Invalid dimension: " + value);
    }
    return static_cast<std::uint32_t>(parsed);
}

}

int main(int argc, char** argv) {
    std::uint32_t width = 64;
    std::uint32_t height = 64;
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
            } else if (argument == "--report") {
                reportPath = value;
            } else {
                throw std::invalid_argument("Unknown option: " + argument);
            }
        }
        if (width != 64 || height != 64) {
            throw std::invalid_argument(
                "Milestone 4C requires a 64 x 64 probe texture.");
        }
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 2;
    }

    const auto snapshot = mc_dlss::runGlD3D12InteropProbe(width, height);
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

    return mc_dlss::validateGlD3D12InteropSnapshot(snapshot) ? 0 : 3;
}
