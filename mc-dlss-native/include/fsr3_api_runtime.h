#pragma once

#include "fsr3_capability_report.h"

#include <memory>
#include <string>

struct ID3D12Device;
struct ID3D12Resource;
struct ID3D12GraphicsCommandList;

namespace mc_dlss {

class Fsr3ApiRuntime;

struct Fsr3ApiLoadResult {
    std::unique_ptr<Fsr3ApiRuntime> runtime;
    std::string message;
};

struct Fsr3UpscaleDispatchInputs {
    ID3D12GraphicsCommandList* commandList = nullptr;
    ID3D12Resource* color = nullptr;
    ID3D12Resource* depth = nullptr;
    ID3D12Resource* motionVectors = nullptr;
    ID3D12Resource* output = nullptr;
    unsigned int renderWidth = 0;
    unsigned int renderHeight = 0;
    unsigned int displayWidth = 0;
    unsigned int displayHeight = 0;
    float jitterX = 0.0F;
    float jitterY = 0.0F;
    float motionScaleX = 0.0F;
    float motionScaleY = 0.0F;
    float frameTimeMilliseconds = 0.0F;
    float cameraNear = 0.0F;
    float cameraFar = 0.0F;
    float verticalFovRadians = 0.0F;
    bool reset = false;
};

struct Fsr3FrameGenerationDispatchInputs {
    ID3D12GraphicsCommandList* commandList = nullptr;
    ID3D12Resource* depth = nullptr;
    ID3D12Resource* motionVectors = nullptr;
    ID3D12Resource* presentColor = nullptr;
    ID3D12Resource* hudlessColor = nullptr;
    ID3D12Resource* generatedOutput = nullptr;
    unsigned int renderWidth = 0;
    unsigned int renderHeight = 0;
    unsigned int displayWidth = 0;
    unsigned int displayHeight = 0;
    float jitterX = 0.0F;
    float jitterY = 0.0F;
    float motionScaleX = 0.0F;
    float motionScaleY = 0.0F;
    float frameTimeMilliseconds = 0.0F;
    float cameraNear = 0.0F;
    float cameraFar = 0.0F;
    float verticalFovRadians = 0.0F;
    float cameraPosition[3]{};
    float cameraUp[3]{};
    float cameraRight[3]{};
    float cameraForward[3]{};
    unsigned long long frameId = 0;
    bool reset = false;
};

class Fsr3ApiRuntime {
public:
    static Fsr3ApiLoadResult load(const std::wstring& loaderPath);

    ~Fsr3ApiRuntime();
    Fsr3ApiRuntime(Fsr3ApiRuntime&&) noexcept;
    Fsr3ApiRuntime& operator=(Fsr3ApiRuntime&&) noexcept;
    Fsr3ApiRuntime(const Fsr3ApiRuntime&) = delete;
    Fsr3ApiRuntime& operator=(const Fsr3ApiRuntime&) = delete;

    Fsr3CapabilitySnapshot initializeContexts(
        ID3D12Device* device,
        unsigned int renderWidth,
        unsigned int renderHeight,
        unsigned int displayWidth,
        unsigned int displayHeight);
    unsigned int dispatchUpscale(
        const Fsr3UpscaleDispatchInputs& inputs) noexcept;
    unsigned int dispatchFrameGeneration(
        const Fsr3FrameGenerationDispatchInputs& inputs) noexcept;

private:
    struct Impl;
    explicit Fsr3ApiRuntime(std::unique_ptr<Impl> impl);
    std::unique_ptr<Impl> impl_;
};

std::string describeFfxReturnCode(unsigned int code);

}
