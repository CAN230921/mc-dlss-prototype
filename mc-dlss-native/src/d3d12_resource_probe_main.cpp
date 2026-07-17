#include "d3d12_resource_probe.h"

#include <cstdint>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

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
    std::uint32_t inputWidth = 1280;
    std::uint32_t inputHeight = 720;
    std::uint32_t outputWidth = 1920;
    std::uint32_t outputHeight = 1080;
    std::string reportPath;

    try {
        for (int index = 1; index < argc; ++index) {
            const std::string argument = argv[index];
            if (index + 1 >= argc) {
                throw std::invalid_argument("Missing value for " + argument);
            }
            const std::string value = argv[++index];
            if (argument == "--input-width") {
                inputWidth = parseDimension(value);
            } else if (argument == "--input-height") {
                inputHeight = parseDimension(value);
            } else if (argument == "--output-width") {
                outputWidth = parseDimension(value);
            } else if (argument == "--output-height") {
                outputHeight = parseDimension(value);
            } else if (argument == "--report") {
                reportPath = value;
            } else {
                throw std::invalid_argument("Unknown option: " + argument);
            }
        }

        if (inputWidth == outputWidth && inputHeight == outputHeight) {
            throw std::invalid_argument("Input and output resolutions must differ.");
        }
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 2;
    }

    const auto snapshot = mc_dlss::runD3D12ResourceProbe(
        inputWidth, inputHeight, outputWidth, outputHeight);
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

    return mc_dlss::validateProbeSnapshot(snapshot) ? 0 : 3;
}
