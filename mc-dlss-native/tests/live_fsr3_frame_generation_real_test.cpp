#include "d3d12_probe_context.h"
#include "live_fsr3_session.h"

#include <d3d12.h>
#include <wrl/client.h>
#include <iostream>

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) return 2;
    auto probe = mc_dlss::D3D12ProbeContext::create(1920, 1080, 3840, 2160);
    auto session = mc_dlss::LiveFsr3Session::create(
        probe->device(), argv[1], 1920, 1080, 3840, 2160);
    if (!session || !session->frameGenerationReady()) return 1;

    D3D12_HEAP_PROPERTIES heap{};
    heap.Type = D3D12_HEAP_TYPE_DEFAULT;
    D3D12_RESOURCE_DESC desc{};
    desc.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
    desc.Width = 3840;
    desc.Height = 2160;
    desc.DepthOrArraySize = 1;
    desc.MipLevels = 1;
    desc.Format = DXGI_FORMAT_R16G16B16A16_FLOAT;
    desc.SampleDesc.Count = 1;
    desc.Flags = D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS;
    Microsoft::WRL::ComPtr<ID3D12Resource> generated;
    if (FAILED(probe->device()->CreateCommittedResource(
            &heap, D3D12_HEAP_FLAG_NONE, &desc,
            D3D12_RESOURCE_STATE_UNORDERED_ACCESS, nullptr,
            IID_PPV_ARGS(&generated)))) return 1;

    ID3D12Resource* transitionResources[] = {
        probe->depth(), probe->motionVectors(), probe->outputColor()};
    const D3D12_RESOURCE_STATES before[] = {
        D3D12_RESOURCE_STATE_DEPTH_WRITE,
        D3D12_RESOURCE_STATE_RENDER_TARGET,
        D3D12_RESOURCE_STATE_UNORDERED_ACCESS};
    D3D12_RESOURCE_BARRIER barriers[3]{};
    for (int index = 0; index < 3; ++index) {
        barriers[index].Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        barriers[index].Transition.pResource = transitionResources[index];
        barriers[index].Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        barriers[index].Transition.StateBefore = before[index];
        barriers[index].Transition.StateAfter =
            D3D12_RESOURCE_STATE_NON_PIXEL_SHADER_RESOURCE;
    }
    probe->commandList()->ResourceBarrier(3, barriers);

    mc_dlss::Fsr3FrameGenerationDispatchInputs inputs{};
    inputs.commandList = probe->commandList();
    inputs.depth = probe->depth();
    inputs.motionVectors = probe->motionVectors();
    inputs.presentColor = probe->outputColor();
    inputs.hudlessColor = probe->outputColor();
    inputs.generatedOutput = generated.Get();
    inputs.renderWidth = 1920;
    inputs.renderHeight = 1080;
    inputs.displayWidth = 3840;
    inputs.displayHeight = 2160;
    inputs.motionScaleX = 1920.0F;
    inputs.motionScaleY = 1080.0F;
    inputs.frameTimeMilliseconds = 16.67F;
    inputs.cameraNear = 0.1F;
    inputs.cameraFar = 1000.0F;
    inputs.verticalFovRadians = 1.2F;
    inputs.cameraUp[1] = 1.0F;
    inputs.cameraRight[0] = 1.0F;
    inputs.cameraForward[2] = -1.0F;
    inputs.frameId = 1;
    inputs.reset = true;
    const unsigned int code = session->dispatchFrameGeneration(inputs);
    if (code != 0) {
        std::cerr << "FSR3 FG dispatch returned " << code << '\n';
        return 1;
    }
    if (!probe->submitAndWait()) return 1;
    std::cout << "live_fsr3_frame_generation_real_test passed\n";
    return 0;
}
