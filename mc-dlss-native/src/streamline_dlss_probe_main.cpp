#include "streamline_dlss_probe.h"

#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

int main(int argc, char** argv) {
    std::filesystem::path pluginPath;
    std::filesystem::path logPath;
    std::filesystem::path reportPath;

    try {
        for (int index = 1; index < argc; ++index) {
            const std::string argument = argv[index];
            if (index + 1 >= argc) {
                throw std::invalid_argument("Missing value for " + argument);
            }
            const std::filesystem::path value = argv[++index];
            if (argument == "--plugin-path") {
                pluginPath = value;
            } else if (argument == "--log-path") {
                logPath = value;
            } else if (argument == "--report") {
                reportPath = value;
            } else {
                throw std::invalid_argument("Unknown option: " + argument);
            }
        }

        if (pluginPath.empty() || logPath.empty()) {
            throw std::invalid_argument(
                "--plugin-path and --log-path are required.");
        }
        std::filesystem::create_directories(logPath);
        if (!reportPath.empty() && reportPath.has_parent_path()) {
            std::filesystem::create_directories(reportPath.parent_path());
        }
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 2;
    }

    const auto snapshot = mc_dlss::runStreamlineDlssProbe(
        pluginPath.wstring(), logPath.wstring());
    const std::string json = mc_dlss::toJson(snapshot);
    std::cout << json << '\n';

    if (!reportPath.empty()) {
        std::ofstream report(reportPath, std::ios::binary | std::ios::trunc);
        if (!report) {
            std::cerr << "Could not open report path: " << reportPath.string() << '\n';
            return 4;
        }
        report << json << '\n';
        if (!report) {
            std::cerr << "Could not write report path: " << reportPath.string() << '\n';
            return 4;
        }
    }

    return mc_dlss::validateStreamlineProbeSnapshot(snapshot) ? 0 : 3;
}
