# FSR3 DXGI Presentation Design

## Goal

Replace the experimental pair of back-to-back OpenGL `SwapBuffers` calls with the
official FidelityFX frame-interpolation DXGI swapchain. Minecraft, Iris, and
Sodium continue rendering through OpenGL, while D3D12 owns final presentation,
frame pacing, and generated-frame scheduling whenever FSR3 frame generation is
active.

The result must improve displayed smoothness rather than merely generating an
unused texture. It must also preserve normal Minecraft rendering when DXGI
presentation is unavailable or fails.

## Current Failure

The current implementation renders one generated frame and one real frame to the
OpenGL default framebuffer, calling `glfwSwapBuffers` for both in immediate
succession. On a windowed 160 Hz display with Minecraft rendering near 190 FPS,
this produces roughly 380 present attempts per second. Windows DWM discards most
intermediate frames, and the bursty swaps cause visibly worse frame pacing.

FSR3 generation itself is functional. The broken boundary is presentation.

## Architecture

### OpenGL rendering

Minecraft and Iris keep their existing OpenGL context. World color, depth,
motion vectors, and transparent UI are rendered to offscreen textures. OpenGL
does not own final presentation after the DXGI presenter has proven that it can
display a frame.

### Shared resources

The existing two-slot GL/D3D12 shared-resource model remains the synchronization
boundary. Each slot provides world color, depth, motion, upscaled output,
generated output, and a shared fence. A dedicated shared UI resource is added so
the official swapchain can composite identical UI over real and generated world
frames without interpolating HUD text.

### Native presenter

A new `Fsr3DxgiPresenter` is owned by `LiveFsr3Session`. It receives the GLFW
window's native `HWND`, the session D3D12 device and queue, and output dimensions.
It creates the official FidelityFX frame-interpolation swapchain with
`ffxCreateFrameinterpolationSwapchainForHwndDX12` and configures the FSR3 frame
generation context to use it.

The FidelityFX presenter thread owns generated/real frame ordering, DXGI present
timing, and frame-latency waits. Java never performs an extra swap for a generated
frame.

### Presentation takeover

Takeover has explicit states:

1. `OPENGL_ONLY`: Minecraft presents normally.
2. `DXGI_WARMING`: the DXGI swapchain exists, but OpenGL still presents.
3. `DXGI_ACTIVE`: at least one native real frame has completed and the original
   OpenGL swap is suppressed.
4. `FALLBACK`: the presenter is released and OpenGL presentation resumes.

The original swap must never be cancelled in `DXGI_WARMING`. This prevents a
failed initialization from producing a black window.

## Feasibility Gate

Before Minecraft integration, a native automated probe creates a hidden GLFW
OpenGL window, obtains its `HWND`, stops swapping through OpenGL, and presents a
known color pattern through a D3D12 DXGI swapchain on that same window. It then
resizes the window and presents a second pattern.

If same-window coexistence fails, implementation stops before changing the game.
The fallback design is a borderless child presentation window attached to the
Minecraft client area, but that path requires separate focus, resize, DPI, and
Alt-Tab validation and is not silently substituted.

## Frame Flow

1. Iris finishes the offscreen world pass.
2. OpenGL populates the selected shared input slot and signals its fence.
3. D3D12 dispatches FSR3 upscaling and frame-generation preparation.
4. The transparent UI texture is registered with the FidelityFX swapchain.
5. The official swapchain schedules generated and real frames and composites UI.
6. Once a successful native present is reported, the Window mixin cancels only
   Minecraft's final OpenGL buffer swap.

Menus, loading overlays, hidden HUD, and an unavailable UI capture disable frame
generation for that frame while preserving a real-frame present.

## Window Lifecycle

Resize, DPI changes, fullscreen transitions, monitor changes, minimize/restore,
shader-pack reloads, and output-format changes invalidate the presenter. The
controller first returns to `OPENGL_ONLY`, waits for dimensions to stabilize,
drains pending FidelityFX presents with `ffxWaitForPresents`, destroys the old
swapchain, and then warms a new one.

Client shutdown waits for outstanding presents before releasing D3D12 resources.
No presenter or worker thread may survive the Minecraft process.

## Failure Handling

Any failed swapchain creation, fence timeout, device removal, failed present, or
unexpected window state transitions atomically to `FALLBACK`. OpenGL swapping is
restored on the next `Window.updateDisplay` call. FSR3 SR may continue if healthy;
only FG/DXGI presentation is disabled.

The visible status remains concise: backend, quality, source/output resolution,
source FPS, and one of `FG preparing`, `FG active`, or `FG fallback`. Detailed
HRESULTs, fence values, and presenter timing remain in diagnostic logs.

## Testing

### Native automated tests

- Same-`HWND` OpenGL/DXGI presentation and resize probe.
- Presenter state-machine transitions and fallback behavior.
- Official swapchain creation, configuration, present, wait, resize, and destroy.
- UI resource registration and real/generated frame identification.
- Device removal and fence-timeout recovery.

### Java tests

- The mixin cancels OpenGL swapping only in `DXGI_ACTIVE`.
- Menu, overlay, hidden HUD, and resize states request a real frame or fallback.
- Session reopen and config changes cannot leave presentation suppressed.

### Manual acceptance

- Windowed and fullscreen tests at 60, 120, and 160 Hz where available.
- FSR3 FG on/off at the same camera position with a shader pack.
- Stable frame pacing measured from displayed presents, not Minecraft's render
  counter.
- UI text, crosshair, particles, entities, block outline, and breaking animation.
- Resize, Alt-Tab, minimize/restore, monitor move, shader reload, and clean exit.

## Delivery Stages

1. Native same-window feasibility probe.
2. DXGI real-frame presentation with guarded OpenGL fallback.
3. Official FidelityFX frame-interpolation swapchain integration.
4. Shared UI composition and lifecycle handling.
5. Launcher package, clean-machine probe, and manual acceptance.

Each stage must pass independently. Frame generation is not exposed as active in
the user build until stages 1 through 4 pass.
