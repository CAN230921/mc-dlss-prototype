# DLSS Production Render Path Design

## Goal

Turn the validated DLSS prototype into a production-oriented render path with correct color, no per-frame CPU/GPU validation stall, and real performance benefit from rendering the Iris world pipeline below output resolution.

## Stage 1: Fast LDR Presentation

Normal gameplay uses `NativeUpscalerExecutionMode.FAST`. Validation readback is disabled unless a separate diagnostic flag is enabled. OpenGL waits on the imported D3D12 fence through `glWaitSemaphoreEXT`; the render thread must not call `glFinish()` every frame.

The selected Iris color target is treated as post-tonemap LDR content. Streamline options use `colorBuffersHDR=false` and `useAutoExposure=false`. Output composition explicitly preserves the expected linear/sRGB conversion instead of relying on an unqualified framebuffer blit.

The settings page adds `诊断验证模式`, disabled by default. Enabling it restores validating submissions and readback for debugging only, with a Chinese warning that it reduces frame rate.

## Stage 2: True Internal Resolution

When DLSS is enabled, the world and Iris shader pipeline render at the input dimensions selected by the DLSS quality mode. The window, main presentation target, GUI, HUD, mouse coordinates, and menu rendering remain at native output dimensions.

The integration must change only world-render target sizing and associated viewports. It must not globally resize the Minecraft window or main GUI target. Iris target allocation, Sodium world viewport, camera projection aspect, depth, motion vectors, and DLSS constants all use the same internal dimensions.

The DLSS result is composited before GUI rendering. Disabling DLSS or leaving a world restores the original Iris/world dimensions and closes shared resources. Resolution or quality changes trigger one controlled pipeline rebuild and one temporal reset.

## Integration Strategy

Use an independent NeoForge mod with narrowly version-locked Mixins/accessors against Iris `1.8.14-beta.1` and Sodium `0.8.12-beta.2`. Do not distribute a modified Iris JAR unless required private allocation points cannot be intercepted reliably.

The initial hook targets Iris render-target dimension selection and the world-render viewport. If runtime verification shows that Iris caches dimensions in additional owned buffers, add accessors only for those exact allocations. GUI and window dimensions remain untouched.

## Safety And Fallback

- Any internal-resolution hook failure disables DLSS and continues normal Iris rendering.
- Session failure enters the existing cooldown and cannot retry every frame.
- A generation, output-size, quality, or diagnostic-mode change rebuilds once.
- Shader-pack disabled state never activates internal resolution.
- Overlay reports output size, internal size, execution mode, color mode, frame count, and reset count in Chinese.

## Verification

1. Core tests cover production/diagnostic execution selection and internal-size policy.
2. Native tests verify LDR/no-auto-exposure Streamline options and FAST mode without readback.
3. NeoForge compile verifies exact Iris/Sodium access points.
4. Formal launcher acceptance compares Iris-only and DLSS runs in the same scene:
   - no visible color shift;
   - DLSS frame count rises without validation readback;
   - GPU occupancy is not suppressed by render-thread waits;
   - Iris target dimensions equal DLSS input dimensions;
   - GUI, crosshair, block outline, and breaking overlay remain aligned;
   - disabling DLSS restores native Iris rendering without restart.
