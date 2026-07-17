#include "fsr3_frame_validation.h"

#include <cmath>

namespace mc_dlss {

Fsr3FrameValidation validateFsr3Frame(const Fsr3FrameInputs& inputs) noexcept {
    if (inputs.renderWidth == 0 || inputs.renderHeight == 0 ||
        inputs.displayWidth <= inputs.renderWidth ||
        inputs.displayHeight <= inputs.renderHeight ||
        inputs.displayWidth > 16384 || inputs.displayHeight > 16384) {
        return Fsr3FrameValidation::InvalidDimensions;
    }
    if (!inputs.colorAvailable) return Fsr3FrameValidation::MissingColor;
    if (!inputs.depthAvailable) return Fsr3FrameValidation::MissingDepth;
    if (!inputs.motionAvailable) return Fsr3FrameValidation::MissingMotion;
    if (!inputs.outputAvailable) return Fsr3FrameValidation::MissingOutput;
    if (!inputs.hudlessAvailable) return Fsr3FrameValidation::MissingHudless;
    if (!inputs.uiAvailable) return Fsr3FrameValidation::MissingUi;
    if (!std::isfinite(inputs.frameTimeMilliseconds) ||
        inputs.frameTimeMilliseconds <= 0.0F ||
        inputs.frameTimeMilliseconds > 1000.0F) {
        return Fsr3FrameValidation::InvalidTiming;
    }
    if (!std::isfinite(inputs.cameraNear) || !std::isfinite(inputs.cameraFar) ||
        !std::isfinite(inputs.verticalFovRadians) || inputs.cameraNear <= 0.0F ||
        inputs.cameraFar <= inputs.cameraNear || inputs.verticalFovRadians <= 0.0F ||
        inputs.verticalFovRadians >= 3.1415927F) {
        return Fsr3FrameValidation::InvalidCamera;
    }
    return Fsr3FrameValidation::Ready;
}

}
