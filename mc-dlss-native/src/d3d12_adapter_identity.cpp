#include "d3d12_adapter_identity.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>

#include <cstring>
#include <iomanip>
#include <sstream>

namespace mc_dlss {

std::string formatAdapterLuid(const std::array<std::uint8_t, 8>& bytes) {
    std::ostringstream formatted;
    formatted << std::hex << std::nouppercase << std::setfill('0');
    for (const std::uint8_t byte : bytes) {
        formatted << std::setw(2) << static_cast<unsigned int>(byte);
    }
    return formatted.str();
}

std::string queryHighPerformanceD3D12AdapterLuid() {
    using Microsoft::WRL::ComPtr;

    ComPtr<IDXGIFactory6> factory;
    if (FAILED(CreateDXGIFactory2(0, IID_PPV_ARGS(&factory)))) {
        return {};
    }

    for (UINT index = 0;; ++index) {
        ComPtr<IDXGIAdapter1> adapter;
        const HRESULT result = factory->EnumAdapterByGpuPreference(
            index,
            DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
            IID_PPV_ARGS(&adapter));
        if (result == DXGI_ERROR_NOT_FOUND) {
            break;
        }
        if (FAILED(result)) {
            return {};
        }

        DXGI_ADAPTER_DESC1 description{};
        if (FAILED(adapter->GetDesc1(&description)) ||
            (description.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0) {
            continue;
        }

        ComPtr<ID3D12Device> device;
        if (FAILED(D3D12CreateDevice(
                adapter.Get(), D3D_FEATURE_LEVEL_12_0,
                IID_PPV_ARGS(&device)))) {
            continue;
        }

        static_assert(sizeof(description.AdapterLuid) == 8);
        std::array<std::uint8_t, 8> bytes{};
        std::memcpy(bytes.data(), &description.AdapterLuid, bytes.size());
        return formatAdapterLuid(bytes);
    }
    return {};
}

}
