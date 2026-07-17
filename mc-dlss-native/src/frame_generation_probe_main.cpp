#include "frame_generation_probe.h"

#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

int main(int argc, char** argv) {
    std::filesystem::path pluginPath;
    std::filesystem::path logPath;
    std::filesystem::path reportPath;
    for (int index = 1; index < argc; ++index) {
        const std::string option = argv[index];
        if (++index >= argc) throw std::invalid_argument("Missing option value.");
        if (option == "--plugin-path") pluginPath = argv[index];
        else if (option == "--log-path") logPath = argv[index];
        else if (option == "--report") reportPath = argv[index];
        else throw std::invalid_argument("Unknown option: " + option);
    }
    if (pluginPath.empty() || logPath.empty()) {
        std::cerr << "--plugin-path and --log-path are required.\n";
        return 2;
    }
    std::filesystem::create_directories(logPath);
    const auto snapshot = mc_dlss::runFrameGenerationProbe(
        pluginPath.wstring(), logPath.wstring());
    const std::string json = mc_dlss::toJson(snapshot);
    std::cout << json << '\n';
    if (!reportPath.empty()) {
        std::filesystem::create_directories(reportPath.parent_path());
        std::ofstream(reportPath, std::ios::binary | std::ios::trunc) << json << '\n';
    }
    return mc_dlss::validateFrameGenerationProbeSnapshot(snapshot) ? 0 : 3;
}
