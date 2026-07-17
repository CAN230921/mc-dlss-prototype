#pragma once

namespace mc_dlss {

enum class Fsr3FrameValidation {
    Ready,
    InvalidDimensions,
    MissingColor,
    MissingDepth,
    MissingMotion,
    MissingOutput,
    MissingHudless,
    MissingUi,
    InvalidTiming,
    InvalidCamera
};

struct Fsr3FrameInputs {
    unsigned int renderWidth = 0;
    unsigned int renderHeight = 0;
    unsigned int displayWidth = 0;
    unsigned int displayHeight = 0;
    bool colorAvailable = false;
    bool depthAvailable = false;
    bool motionAvailable = false;
    bool outputAvailable = false;
    bool hudlessAvailable = false;
    bool uiAvailable = false;
    float frameTimeMilliseconds = 0.0F;
    float cameraNear = 0.0F;
    float cameraFar = 0.0F;
    float verticalFovRadians = 0.0F;
    bool reset = false;
};

Fsr3FrameValidation validateFsr3Frame(const Fsr3FrameInputs& inputs) noexcept;

}
