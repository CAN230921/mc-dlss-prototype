# Milestone 4D-G Live DLSS Presentation Design

Date: 2026-07-11

## Goal

Render the Minecraft world at DLSS Quality's recommended input resolution, evaluate NVIDIA Streamline DLSS every frame on the live D3D12 bridge, return the full-resolution result to OpenGL, and present it before the hand and HUD render at native window resolution. Any failure must restore vanilla full-resolution rendering without leaving a black frame or broken framebuffer.

## Selected Approach

Keep Minecraft's main framebuffer at full window resolution. Around `GameRenderer.renderWorld`, temporarily redirect `MinecraftClient`'s active framebuffer reference to a persistent low-resolution world framebuffer. At `WorldRenderEvents.END`, capture that low-resolution world color and depth, generate camera-only motion, submit one synchronous Streamline DLSS evaluation, restore the original main framebuffer, and composite the shared full-resolution DLSS output into it. `renderWorld` then returns and vanilla draws the hand and HUD over the upscaled world at native resolution.

The first implementation uses DLSS Quality mode and synchronous render-thread evaluation. Correctness, presentation order, recovery, and resource lifetime are acceptance gates; asynchronous overlap and production frame time are later optimizations.

## Alternatives Rejected

- Resizing the main framebuffer down and back up every frame would repeatedly recreate attachments, invalidate temporal resources, and make failure recovery fragile.
- A single-frame DLSS probe would prove evaluation but not persistent temporal history, output presentation, resize handling, or fallback behavior.
- Upscaling only after HUD rendering would blur text and UI and would not meet the native-resolution HUD requirement.
- A separate helper process would isolate NGX shutdown but require cross-process resource ownership and synchronization before the in-process path is proven.
- Replacing the complete renderer backend remains unnecessary while the proven Win32 shared-resource route can supply D3D12 resources and command context.

## Scope

- Fabric client, Minecraft 1.21.1, Windows x64, NVIDIA RTX, Streamline 2.12.0, and the existing OpenGL-D3D12 interop route.
- DLSS Quality mode only for the first acceptance run.
- One persistent low-resolution OpenGL world framebuffer and two persistent D3D12/GL frame slots.
- Shared input color `R16G16B16A16_FLOAT`, depth `R32_FLOAT`, motion `R16G16_FLOAT`, and output color `R16G16B16A16_FLOAT`.
- Camera-only 2D motion vectors, zero jitter, and explicit reset state.
- Full-resolution hand and HUD rendered after DLSS presentation.
- Thirty-frame initial gate followed by a 120-frame final acceptance and 30-frame resource-retention check.
- No per-object motion, reactive mask, exposure texture, sharpening UI, quality-mode selector, asynchronous queue overlap, or NeoForge runtime integration.

## Render Redirection

A narrowly scoped mixin/accessor controls the private Minecraft main-framebuffer reference. At the head of `GameRenderer.renderWorld`:

1. Validate that DLSS presentation is enabled and a world is loaded.
2. Save the original full-resolution main framebuffer.
3. Ensure the persistent low-resolution world framebuffer matches the current DLSS optimal render dimensions.
4. Replace the active framebuffer reference with the low-resolution framebuffer.
5. Bind it and set the render viewport to its dimensions.

At `WorldRenderEvents.END`, while the low-resolution framebuffer is still active, the DLSS frame is captured and evaluated. Before the callback returns, the original framebuffer reference is restored, bound for writing, and filled with the DLSS output. A return/exception injection also restores the original framebuffer if the END callback does not complete, making framebuffer restoration idempotent.

The original framebuffer is never resized by the feature. Window-resize handling rebuilds output/session resources transactionally at the next frame boundary.

## Resolution Contract

The output resolution equals the current main framebuffer dimensions. Native code queries `slDLSSGetOptimalSettings` for Quality mode and returns the selected render width/height to Java. The low-resolution world framebuffer and every input texture use those exact dimensions; the shared output uses output dimensions.

The session is rejected if optimal settings are unavailable, dimensions are zero, render dimensions are not smaller than output dimensions, or aspect ratios differ by more than one source pixel of rounding. Resize forces a complete two-slot rebuild and temporal reset.

## Shared Resources

Each A/B slot owns:

- shared D3D12 input color at render resolution, `DXGI_FORMAT_R16G16B16A16_FLOAT`
- shared D3D12 depth data at render resolution, `DXGI_FORMAT_R32_FLOAT`
- shared D3D12 motion at render resolution, `DXGI_FORMAT_R16G16_FLOAT`
- shared D3D12 output color at output resolution, `DXGI_FORMAT_R16G16B16A16_FLOAT`
- one shared D3D12 fence and Win32 handle
- command allocator/list and readbacks needed for diagnostic fingerprints

OpenGL imports all four resources. Color is blitted from the low-resolution world framebuffer into the shared FP16 input. Depth and motion are generated with the existing integer-fetch shaders at render resolution. The output is attached to an OpenGL read framebuffer and blitted into the full-resolution main framebuffer after the return fence wait.

## Streamline Lifecycle

The native live-DLSS runtime initializes Streamline once, selects the same D3D12 adapter already matched by LUID, sets the D3D12 device, verifies `kFeatureDLSS`, queries optimal settings, and sets Quality options for viewport 0.

Each accepted frame obtains a new Streamline frame token, submits the exact Java temporal constants, tags color/depth/motion/output resources with explicit extents and states, and calls `slEvaluateFeature` on the session command list. The queue signals the shared fence only after DLSS evaluation and diagnostic output transitions complete.

On session close, call `slFreeResources` for the viewport and release project-owned D3D12/Win32 resources. Because the local 2.12 runtime has reproducibly stalled inside `slShutdown` after successful evaluation, the Minecraft process does not call `slShutdown`; Streamline process-global state is intentionally left loaded until normal process termination. This limitation is logged and does not block resource-release acceptance.

## Temporal Constants

Use the 4D-F matrix convention and values for the low-resolution frame:

- `cameraViewToClip`, `clipToCameraView`
- `clipToPrevClip`, `prevClipToClip`
- current camera position and normalized up/right/forward basis
- projection-derived near/far/FOV and render aspect ratio
- `jitterOffset={0,0}`
- `mvecScale={1/renderWidth,1/renderHeight}`
- `depthInverted=false`
- `cameraMotionIncluded=true`
- `motionVectors3D=false`
- `motionVectorsJittered=false`
- `motionVectorsDilated=false`
- reset on startup, resize, world change, discontinuity, re-enable, or failed previous frame

Only a completely evaluated and presented frame becomes temporal history.

## Per-Frame Flow

1. Redirect world rendering to the persistent low-resolution framebuffer.
2. Render the vanilla world at the DLSS optimal input resolution.
3. At `WorldRenderEvents.END`, freeze current matrices and build constants.
4. Populate shared FP16 color, R32F depth, and RG16F motion for the selected A/B slot.
5. Signal the odd OpenGL fence value and flush.
6. D3D12 waits, transitions resources, sets constants/tags, evaluates DLSS, fingerprints the output, restores shared states, and signals the even value.
7. OpenGL waits for the even value.
8. Restore and bind the original full-resolution main framebuffer.
9. Blit the full-resolution shared DLSS output into the main framebuffer.
10. Validate OpenGL output fingerprint against the native output fingerprint.
11. Publish success metrics and retain temporal history.
12. Vanilla renders hand and HUD at native resolution.

## Presentation And Color

The first version uses a nearest full-frame blit only for exact output-size copy; no additional scaling occurs after DLSS. The output alpha is ignored when writing the opaque world target. Framebuffer sRGB enable state, draw/read bindings, viewport, color mask, blend, depth, scissor, cull, active texture, program, and VAO are saved and restored.

Acceptance requires the output to be finite where represented as FP16, non-black, non-uniform, and changing across frames. It does not require pixel equality with the input because DLSS intentionally changes the image.

## Failure Bypass

The feature begins disabled until native capability/session creation succeeds. A runtime failure performs these actions in order:

1. Restore the original main framebuffer reference and full-resolution viewport.
2. Render or preserve a valid full-resolution fallback frame; never present an uninitialized DLSS output.
3. Mark temporal history reset.
4. Disable further DLSS attempts for the current process unless the development retry command explicitly resets the probe.
5. Release imported GL objects before native shared handles and D3D12 resources.
6. Publish a structured failure stage and message on the HUD/log.

If failure occurs after low-resolution world rendering but before valid DLSS output, blit the low-resolution world color to the main framebuffer with linear filtering for that frame. Subsequent frames use vanilla full-resolution rendering.

## Controls And Diagnostics

The development build exposes one binary enable property and a HUD status; no user-facing settings screen is added. The HUD/log reports:

- lifecycle and fallback state
- Quality mode
- render and output dimensions
- completed evaluations out of 120
- A/B slot counts and reset count
- input and output hash-change counts
- latest GL/D3D12 output hashes
- output non-black/non-uniform status
- average and maximum synchronous evaluation/presentation time
- resources released and Streamline process-global state retained
- exact failure stage/message

The shared debug snapshot may report `dlss-ready=true` only after the final continuous acceptance reaches COMPLETE. Before that it remains false even if an individual evaluation succeeds.

## Testing

- Pure Java tests cover optimal-resolution validation, aspect rounding, render redirection state, idempotent restoration, fallback transitions, temporal reset, lifecycle gating, and overlay formatting.
- Shader/source tests retain depth/motion contracts and add output-composite state requirements.
- Pure native tests cover four-resource construction, format/extents, resource-state transitions, constants-array decoding, Streamline result mapping, frame-token sequencing, output fingerprinting, and idempotent close.
- A native fake-Streamline boundary tests failure at initialize, support, settings, options, constants, tagging, evaluation, and free-resource stages without loading NGX.
- Fabric compilation verifies framebuffer accessor/mixin targets against Minecraft 1.21.1 mappings.
- Runtime testing first requires 30 continuous presented frames, then 120/120 frames, A/B 60/60, output hash changes, matching GL/D3D12 output hashes, 30 retained frames, clean project-resource release, native-resolution HUD, and continued rendering.
- Existing 4D-A through 4D-F evidence and all Java/native/Gradle regressions remain green.

## Acceptance Criteria

- DLSS Quality optimal settings produce a lower render resolution with the output aspect preserved.
- Minecraft world rendering uses the low-resolution framebuffer for every accepted frame.
- Thirty initial and 120 final consecutive Streamline evaluations complete and present valid output.
- The final frame reports matching OpenGL and D3D12 output hashes, non-black/non-uniform output, and at least one output hash change.
- Both slots are used 60 times, resources survive 30 retained frames, and all project-owned GL/D3D12/Win32 resources release.
- The hand and HUD remain sharp at native output resolution and are not included in the DLSS input.
- Resize and injected evaluation failure both restore vanilla rendering without a black screen.
- Minecraft continues rendering after completion/fallback.
- The runtime snapshot reaches `dlss-ready=true` only after full acceptance.

## Non-Claims

Passing 4D-G proves a synchronous, persistent, camera-motion-only DLSS Quality presentation path inside Minecraft. It does not prove production frame time, asynchronous overlap, per-object motion, jittered sampling, reactive/disocclusion masks, exposure integration, other quality modes, NeoForge parity, HDR correctness, or release-grade Streamline shutdown behavior.
