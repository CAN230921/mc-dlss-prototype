#include "fsr3_frame_validation.h"

#include <iostream>

int main() {
    using namespace mc_dlss;
    Fsr3FrameInputs valid{};
    valid.renderWidth = 1920;
    valid.renderHeight = 1080;
    valid.displayWidth = 3840;
    valid.displayHeight = 2160;
    valid.colorAvailable = true;
    valid.depthAvailable = true;
    valid.motionAvailable = true;
    valid.outputAvailable = true;
    valid.hudlessAvailable = true;
    valid.uiAvailable = true;
    valid.frameTimeMilliseconds = 16.67F;
    valid.cameraNear = 0.05F;
    valid.cameraFar = 1000.0F;
    valid.verticalFovRadians = 1.2F;
    if (validateFsr3Frame(valid) != Fsr3FrameValidation::Ready) return 1;

    auto missingMotion = valid;
    missingMotion.motionAvailable = false;
    if (validateFsr3Frame(missingMotion) != Fsr3FrameValidation::MissingMotion) return 1;
    auto missingDepth = valid;
    missingDepth.depthAvailable = false;
    if (validateFsr3Frame(missingDepth) != Fsr3FrameValidation::MissingDepth) return 1;
    auto badSize = valid;
    badSize.displayWidth = badSize.renderWidth;
    if (validateFsr3Frame(badSize) != Fsr3FrameValidation::InvalidDimensions) return 1;
    auto badTime = valid;
    badTime.frameTimeMilliseconds = 0.0F;
    if (validateFsr3Frame(badTime) != Fsr3FrameValidation::InvalidTiming) return 1;
    auto noUi = valid;
    noUi.uiAvailable = false;
    if (validateFsr3Frame(noUi) != Fsr3FrameValidation::MissingUi) return 1;

    std::cout << "fsr3_frame_validation_test passed\n";
    return 0;
}
