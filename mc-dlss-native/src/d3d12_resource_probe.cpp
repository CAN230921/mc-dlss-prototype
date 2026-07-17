#include "d3d12_resource_probe.h"

#include "d3d12_probe_context.h"

#include <exception>

namespace mc_dlss {

ProbeSnapshot runD3D12ResourceProbe(
    std::uint32_t inputWidth,
    std::uint32_t inputHeight,
    std::uint32_t outputWidth,
    std::uint32_t outputHeight) noexcept {
    ProbeSnapshot snapshot{};
    snapshot.inputWidth = inputWidth;
    snapshot.inputHeight = inputHeight;
    snapshot.outputWidth = outputWidth;
    snapshot.outputHeight = outputHeight;

    try {
        auto context = D3D12ProbeContext::create(
            inputWidth, inputHeight, outputWidth, outputHeight);
        context->submitAndWait();
        snapshot = context->snapshot();
        snapshot.message = validateProbeSnapshot(snapshot)
            ? "D3D12 DLSS resource contract is available."
            : "D3D12 resource validation did not satisfy the complete contract.";
    } catch (const std::exception& error) {
        snapshot.message = error.what();
    } catch (...) {
        snapshot.message = "Unknown D3D12 resource probe failure.";
    }

    return snapshot;
}

}
