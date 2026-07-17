# Dual DLSS and FSR3 Backend Design

## Goal

Add official AMD FidelityFX SDK 2.3.0 support while preserving the stable DLSS
super-resolution path. The primary combination is DLSS SR plus FSR3 Frame
Generation; FSR3 SR plus FSR3 Frame Generation is an optional cross-vendor
combination.

## Runtime Modes

- Native rendering with no upscaler and no frame generation.
- DLSS SR with frame generation disabled.
- DLSS SR with FSR3 Frame Generation.
- FSR3 SR with frame generation disabled.
- FSR3 SR with FSR3 Frame Generation.

FSR3 failures must return a diagnostic status and fall back without terminating
Minecraft. Experimental builds remain separate from the stable SR artifact.

## Architecture

The native module gains an FSR loader layer that dynamically loads the official,
AMD-signed `amd_fidelityfx_loader_dx12.dll`. It queries the installed FSR3
upscaler and frame-generation providers before creating any effect context.

An upscaler backend selector routes each frame to native rendering, the existing
DLSS session, or a new FSR3 upscaler session. Frame generation is a separate
stage and consumes the final upscaled world color, depth, motion vectors,
camera data, frame timing, and reset state. HUD-less capture is supplied to FSR3
while Minecraft UI is composed as a separate surface.

The current OpenGL-to-D3D12 interop bridge remains the ownership boundary for
shared textures and synchronization. FSR code does not access Minecraft OpenGL
objects directly.

## Distribution

The experimental JAR embeds these official signed runtime files:

- `amd_fidelityfx_loader_dx12.dll`
- `amd_fidelityfx_upscaler_dx12.dll`
- `amd_fidelityfx_framegeneration_dx12.dll`

The native manifest records their lengths and SHA-256 hashes. No community
patched or unsigned runtime is loaded.

## Failure Handling

Each stage reports loader availability, provider version, context state, and the
last FidelityFX return code. Missing DLLs, unsupported adapters, invalid resource
formats, context creation failures, and dispatch failures disable only the
affected FSR feature. Repeated dispatch failures latch the feature off until the
render pipeline is recreated.

## Verification

1. Pure report-model tests cover provider and fallback decisions.
2. A standalone DX12 probe loads the signed runtime, enumerates FSR3 SR and FG
   versions, and creates/destroys contexts on the selected adapter.
3. Native smoke tests validate resource dimensions, formats, motion-vector scale,
   reset behavior, and UI composition selection.
4. A separate experimental NeoForge JAR is tested in Minecraft; the stable SR JAR
   is not overwritten.

## Initial Delivery Boundary

The first implementation milestone ends with a passing standalone probe and a
runtime-selectable native backend skeleton. Real-time FSR3 dispatch is enabled
only after the probe confirms the machine and official runtime support it.
