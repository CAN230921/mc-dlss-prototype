#pragma once

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <d3d12.h>
#include <dxgi1_6.h>

#include "d3d12_probe_report.h"

#include <cstdint>
#include <memory>

namespace mc_dlss {

class D3D12ProbeContext {
public:
    static std::unique_ptr<D3D12ProbeContext> create(
        std::uint32_t inputWidth,
        std::uint32_t inputHeight,
        std::uint32_t outputWidth,
        std::uint32_t outputHeight);

    ~D3D12ProbeContext();

    D3D12ProbeContext(const D3D12ProbeContext&) = delete;
    D3D12ProbeContext& operator=(const D3D12ProbeContext&) = delete;

    ID3D12Device* device() const noexcept;
    IDXGIFactory6* factory() const noexcept;
    ID3D12CommandQueue* commandQueue() const noexcept;
    ID3D12GraphicsCommandList* commandList() const noexcept;
    ID3D12Resource* inputColor() const noexcept;
    ID3D12Resource* depth() const noexcept;
    ID3D12Resource* motionVectors() const noexcept;
    ID3D12Resource* outputColor() const noexcept;
    LUID adapterLuid() const noexcept;

    std::uint32_t inputColorState() const noexcept;
    std::uint32_t depthState() const noexcept;
    std::uint32_t motionVectorsState() const noexcept;
    std::uint32_t outputColorState() const noexcept;

    ProbeSnapshot snapshot() const;
    bool submitAndWait();

private:
    struct Impl;

    D3D12ProbeContext(
        std::uint32_t inputWidth,
        std::uint32_t inputHeight,
        std::uint32_t outputWidth,
        std::uint32_t outputHeight);

    std::unique_ptr<Impl> impl_;
};

}
