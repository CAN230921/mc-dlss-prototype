#include "d3d12_probe_context.h"
#include "live_fsr3_session.h"

#include <d3d12.h>
#include <d3d12sdklayers.h>
#include <iostream>
#include <vector>

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) return 2;
    auto probe = mc_dlss::D3D12ProbeContext::create(1920, 1080, 3840, 2160);
    auto session = mc_dlss::LiveFsr3Session::create(
        probe->device(), argv[1], 1920, 1080, 3840, 2160);
    if (!session || !session->superResolutionReady()) return 1;
    std::cerr << "phase=context-ready\n" << std::flush;

    ID3D12Resource* resources[] = {
        probe->inputColor(), probe->depth(), probe->motionVectors(), probe->outputColor()};
    const D3D12_RESOURCE_STATES beforeStates[] = {
        static_cast<D3D12_RESOURCE_STATES>(probe->inputColorState()),
        static_cast<D3D12_RESOURCE_STATES>(probe->depthState()),
        static_cast<D3D12_RESOURCE_STATES>(probe->motionVectorsState()),
        static_cast<D3D12_RESOURCE_STATES>(probe->outputColorState())};
    const D3D12_RESOURCE_STATES dispatchStates[] = {
        D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
        D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
        D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE,
        D3D12_RESOURCE_STATE_UNORDERED_ACCESS};
    D3D12_RESOURCE_BARRIER barriers[3]{};
    for (int index = 0; index < 3; ++index) {
        barriers[index].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        barriers[index].Transition.pResource = resources[index];
        barriers[index].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        barriers[index].Transition.StateBefore = beforeStates[index];
        barriers[index].Transition.StateAfter = dispatchStates[index];
    }
    probe->commandList()->ResourceBarrier(3, barriers);

    mc_dlss::Fsr3UpscaleDispatchInputs inputs{};
    inputs.commandList = probe->commandList();
    inputs.color = resources[0];
    inputs.depth = resources[1];
    inputs.motionVectors = resources[2];
    inputs.output = resources[3];
    inputs.renderWidth = 1920;
    inputs.renderHeight = 1080;
    inputs.displayWidth = 3840;
    inputs.displayHeight = 2160;
    inputs.motionScaleX = 1920.0F;
    inputs.motionScaleY = 1080.0F;
    inputs.frameTimeMilliseconds = 16.67F;
    inputs.cameraNear = 0.05F;
    inputs.cameraFar = 1000.0F;
    inputs.verticalFovRadians = 1.2F;
    inputs.reset = true;
    const unsigned int code = session->dispatchUpscale(inputs);
    std::cerr << "phase=dispatch-return code=" << code << "\n" << std::flush;
    ID3D12InfoQueue* infoQueue = nullptr;
    if (SUCCEEDED(probe->device()->QueryInterface(IID_PPV_ARGS(&infoQueue)))) {
        const UINT64 count = infoQueue->GetNumStoredMessagesAllowedByRetrievalFilter();
        for (UINT64 index = 0; index < count; ++index) {
            SIZE_T size = 0;
            infoQueue->GetMessage(index, nullptr, &size);
            std::vector<unsigned char> storage(size);
            auto* message = reinterpret_cast<D3D12_MESSAGE*>(storage.data());
            if (SUCCEEDED(infoQueue->GetMessage(index, message, &size))) {
                std::cerr << "d3d12=" << message->pDescription << '\n';
            }
        }
        infoQueue->Release();
        std::cerr << std::flush;
    }
    if (code != 0) {
        std::cerr << "FSR3 upscale dispatch returned " << code << '\n';
        return 1;
    }
    std::cerr << "phase=before-close\n" << std::flush;
    const HRESULT closeResult = probe->commandList()->Close();
    std::cerr << "phase=after-close hr=" << closeResult << "\n" << std::flush;
    if (FAILED(closeResult)) {
        ID3D12InfoQueue* closeQueue = nullptr;
        if (SUCCEEDED(probe->device()->QueryInterface(IID_PPV_ARGS(&closeQueue)))) {
            const UINT64 count = closeQueue->GetNumStoredMessagesAllowedByRetrievalFilter();
            for (UINT64 index = 0; index < count; ++index) {
                SIZE_T size = 0;
                closeQueue->GetMessage(index, nullptr, &size);
                std::vector<unsigned char> storage(size);
                auto* message = reinterpret_cast<D3D12_MESSAGE*>(storage.data());
                if (SUCCEEDED(closeQueue->GetMessage(index, message, &size))) {
                    std::cerr << "close-d3d12=" << message->pDescription << '\n';
                }
            }
            closeQueue->Release();
        }
        return 1;
    }
    ID3D12CommandList* lists[] = {probe->commandList()};
    std::cerr << "phase=before-execute\n" << std::flush;
    probe->commandQueue()->ExecuteCommandLists(1, lists);
    std::cerr << "phase=after-execute\n" << std::flush;
    ID3D12Fence* fence = nullptr;
    if (FAILED(probe->device()->CreateFence(0, D3D12_FENCE_FLAG_NONE,
            IID_PPV_ARGS(&fence)))) return 1;
    const HRESULT signalResult = probe->commandQueue()->Signal(fence, 1);
    std::cerr << "phase=after-signal hr=" << signalResult << "\n" << std::flush;
    if (FAILED(signalResult)) { fence->Release(); return 1; }
    HANDLE event = CreateEventW(nullptr, FALSE, FALSE, nullptr);
    fence->SetEventOnCompletion(1, event);
    const DWORD waitResult = WaitForSingleObject(event, 10000);
    CloseHandle(event);
    fence->Release();
    if (waitResult != WAIT_OBJECT_0) return 1;
    std::cerr << "phase=gpu-complete\n" << std::flush;
    std::cout << "live_fsr3_upscale_real_test passed\n";
    return 0;
}
