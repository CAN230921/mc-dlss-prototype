#include "live_dlss_session.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <d3d12.h>
#include <wrl/client.h>

#include <array>
#include <cstring>
#include <filesystem>
#include <functional>
#include <iostream>
#include <vector>

namespace {

using Microsoft::WRL::ComPtr;

void require(HRESULT result, const char* label) {
    if (FAILED(result)) {
        std::cerr << label << " failed\n";
        std::exit(1);
    }
}

struct Upload {
    ComPtr<ID3D12Resource> buffer;
    D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint{};
};

Upload prepareUpload(
    ID3D12Device* device, ID3D12Resource* texture,
    std::uint32_t logicalRowBytes, std::uint32_t height,
    const std::function<void(std::uint8_t*, std::uint32_t)>& fillRow) {
    const auto description = texture->GetDesc();
    UINT rows = 0;
    UINT64 rowSize = 0;
    UINT64 total = 0;
    Upload upload{};
    device->GetCopyableFootprints(
        &description, 0, 1, 0, &upload.footprint, &rows, &rowSize, &total);
    if (rows != height || rowSize != logicalRowBytes) std::exit(1);
    D3D12_HEAP_PROPERTIES heap{};
    heap.Type = D3D12_HEAP_TYPE_UPLOAD;
    D3D12_RESOURCE_DESC buffer{};
    buffer.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
    buffer.Width = total;
    buffer.Height = 1;
    buffer.DepthOrArraySize = 1;
    buffer.MipLevels = 1;
    buffer.SampleDesc.Count = 1;
    buffer.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;
    require(device->CreateCommittedResource(
        &heap, D3D12_HEAP_FLAG_NONE, &buffer, D3D12_RESOURCE_STATE_GENERIC_READ,
        nullptr, IID_PPV_ARGS(&upload.buffer)), "Create upload");
    std::uint8_t* mapped = nullptr;
    require(upload.buffer->Map(0, nullptr, reinterpret_cast<void**>(&mapped)), "Map upload");
    mapped += upload.footprint.Offset;
    for (std::uint32_t y = 0; y < height; ++y) {
        fillRow(mapped + static_cast<std::size_t>(y)
            * upload.footprint.Footprint.RowPitch, y);
    }
    upload.buffer->Unmap(0, nullptr);
    return upload;
}

std::array<float, 16> identity() {
    return {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
}

}

int wmain() {
    constexpr std::uint32_t renderWidth = 569;
    constexpr std::uint32_t renderHeight = 320;
    constexpr std::uint32_t outputWidth = 854;
    constexpr std::uint32_t outputHeight = 480;
    auto session = mc_dlss::LiveDlssSession::create(
        renderWidth, renderHeight, outputWidth, outputHeight);
    ID3D12Device* device = session->device();

    auto color = prepareUpload(device, session->colorTexture(0),
        renderWidth * 8U, renderHeight,
        [=](std::uint8_t* row, std::uint32_t y) {
            for (std::uint32_t x = 0; x < renderWidth; ++x) {
                const std::uint16_t rgba[] = {
                    (x / 32U + y / 32U) % 2U == 0 ? std::uint16_t{0x3400} : std::uint16_t{0x3800},
                    0x3800, 0x3400, 0x3c00};
                std::memcpy(row + static_cast<std::size_t>(x) * 8U, rgba, sizeof(rgba));
            }
        });
    auto depth = prepareUpload(device, session->depthTexture(0),
        renderWidth * 4U, renderHeight,
        [=](std::uint8_t* row, std::uint32_t) {
            for (std::uint32_t x = 0; x < renderWidth; ++x) {
                const float value = x < renderWidth - 8 ? 0.5F : 1.0F;
                std::memcpy(row + static_cast<std::size_t>(x) * 4U, &value, sizeof(value));
            }
        });
    auto motion = prepareUpload(device, session->motionTexture(0),
        renderWidth * 4U, renderHeight,
        [=](std::uint8_t* row, std::uint32_t) {
            std::memset(row, 0, renderWidth * 4U);
        });

    D3D12_COMMAND_QUEUE_DESC queueDescription{};
    ComPtr<ID3D12CommandQueue> queue;
    ComPtr<ID3D12CommandAllocator> allocator;
    ComPtr<ID3D12GraphicsCommandList> list;
    require(device->CreateCommandQueue(&queueDescription, IID_PPV_ARGS(&queue)), "Create queue");
    require(device->CreateCommandAllocator(D3D12_COMMAND_LIST_TYPE_DIRECT,
        IID_PPV_ARGS(&allocator)), "Create allocator");
    require(device->CreateCommandList(0, D3D12_COMMAND_LIST_TYPE_DIRECT,
        allocator.Get(), nullptr, IID_PPV_ARGS(&list)), "Create list");
    auto copy = [&](Upload& upload, ID3D12Resource* target) {
        D3D12_TEXTURE_COPY_LOCATION source{};
        source.pResource = upload.buffer.Get();
        source.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
        source.PlacedFootprint = upload.footprint;
        D3D12_TEXTURE_COPY_LOCATION destination{};
        destination.pResource = target;
        destination.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
        list->CopyTextureRegion(&destination, 0, 0, 0, &source, nullptr);
    };
    copy(color, session->colorTexture(0));
    copy(depth, session->depthTexture(0));
    copy(motion, session->motionTexture(0));
    require(list->Close(), "Close upload list");
    ID3D12CommandList* lists[] = {list.Get()};
    queue->ExecuteCommandLists(1, lists);
    ComPtr<ID3D12Fence> uploadFence;
    require(device->CreateFence(0, D3D12_FENCE_FLAG_NONE,
        IID_PPV_ARGS(&uploadFence)), "Create upload fence");
    require(queue->Signal(uploadFence.Get(), 1), "Signal upload");
    HANDLE event = CreateEventW(nullptr, FALSE, FALSE, nullptr);
    require(uploadFence->SetEventOnCompletion(1, event), "Wait upload");
    WaitForSingleObject(event, 5000);
    CloseHandle(event);

    wchar_t executable[MAX_PATH]{};
    GetModuleFileNameW(nullptr, executable, MAX_PATH);
    const auto directory = std::filesystem::path(executable).parent_path();
    const auto initialized = session->initializeStreamline(
        directory.wstring(), directory.wstring());
    if (!initialized.success) {
        std::cerr << initialized.message << '\n';
        return 1;
    }
    mc_dlss::LiveDlssConstantsData constants{};
    constants.cameraViewToClip = identity();
    constants.clipToCameraView = identity();
    constants.clipToPrevClip = identity();
    constants.prevClipToClip = identity();
    constants.cameraUp = {0, 1, 0};
    constants.cameraRight = {1, 0, 0};
    constants.cameraForward = {0, 0, 1};
    constants.cameraNear = 0.1F;
    constants.cameraFar = 1000.0F;
    constants.cameraFov = 1.0F;
    constants.cameraAspect = static_cast<float>(renderWidth) / renderHeight;
    constants.motionScaleX = 1.0F / renderWidth;
    constants.motionScaleY = 1.0F / renderHeight;
    constants.reset = true;
    require(session->fence(0)->Signal(1), "Signal GL stand-in");
    if (!session->submitEvaluation(0, 1, 2, constants)) {
        std::cerr << "live DLSS evaluation submission failed\n";
        return 1;
    }
    const auto inspection = session->inspectEvaluation(0, 2);
    if (!inspection.available || inspection.outputNonBlackPixelCount == 0
        || !inspection.outputNonUniform) {
        std::cerr << inspection.message << '\n';
        return 1;
    }
    constants.cameraPosition[0] = 1.0F;
    constants.reset = false;
    require(session->fence(0)->Signal(3), "Signal second GL stand-in");
    if (!session->submitEvaluation(0, 3, 4, constants,
            mc_dlss::LiveUpscalerExecutionMode::fast)) {
        std::cerr << "second live DLSS evaluation submission failed\n";
        return 1;
    }
    const auto secondInspection = session->inspectEvaluation(0, 4);
    if (secondInspection.available || secondInspection.diagnosticReadbackRequested
        || secondInspection.completedFenceValue < 4
        || secondInspection.lastSubmittedSignal != 4
        || secondInspection.stage != mc_dlss::LiveStreamlineStage::ready) {
        std::cerr << secondInspection.message << '\n';
        return 1;
    }
    const auto freed = session->freeStreamlineResources();
    if (!freed.success) {
        std::cerr << freed.message << '\n';
        return 1;
    }
    std::cout << "hash=" << std::hex << inspection.outputHash << std::dec
              << " nonBlack=" << inspection.outputNonBlackPixelCount << '\n';
    std::cout << "live_streamline_evaluation_real_test passed\n";
    return 0;
}
