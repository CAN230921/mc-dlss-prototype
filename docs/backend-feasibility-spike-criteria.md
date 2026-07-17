# Backend Feasibility Spike Criteria

Date: 2026-07-10

## Current Diagnostic Contract

`mc-dlss-core` now has a small backend diagnostic surface:

- `RenderBackendKind`: `UNKNOWN`, `OPENGL`, `D3D12`, `VULKAN`.
- `DlssResourcePathStatus`: ready/blocked states for unsupported backend, missing native resources, missing command context, and missing motion vectors.
- `DlssBackendDiagnostic`: one dependency-free record that reports backend kind, native resource availability, command context availability, motion-vector availability, UI-after-upscale feasibility, and a debug message.

The canonical current result is `DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked()`. It reports:

- Backend: `OPENGL`
- Status: `BLOCKED_UNSUPPORTED_BACKEND`
- DLSS evaluation ready: `false`
- Native D3D/Vulkan device: missing
- Native command context: missing
- Native color/depth resources: missing
- Motion-vector texture: missing
- UI-after-upscale timing: conceptually possible, but not enough without a compatible backend

## Why This Exists

Milestone 2 found that the current Iris/Sodium Minecraft 1.21.1 path exposes OpenGL texture/framebuffer IDs, while Streamline DLSS SR requires D3D/Vulkan native resources and a compatible command buffer/list.

The diagnostic gives loader adapters and future debug UI a truthful status to display while renderer-backend research continues. It avoids presenting the native JNI bridge as sufficient for DLSS evaluation.

## Next Spike Acceptance Criteria

A D3D12 or Vulkan renderer-backend spike should not be considered successful until it can prove all of the following:

- A real D3D12 or Vulkan device is owned or reachable at the Minecraft frame boundary.
- A compatible command list/buffer is available at the DLSS evaluation point.
- Low-resolution scene color exists as a native D3D/Vulkan resource.
- Output-resolution scene color exists as a native D3D/Vulkan resource.
- Depth exists as a native D3D/Vulkan resource with known state/layout.
- Motion vectors exist as a native D3D/Vulkan texture, even if the first version is camera-only and explicitly limited.
- Render resolution and output resolution can differ cleanly.
- UI/HUD composition happens after upscale at output resolution.
- Jitter, reset, and previous/current camera constants can be produced per frame.
- Streamline resource tagging and evaluation can be attempted without relying on OpenGL texture IDs.

## Recommended Next Implementation Step

## Milestone 4A Result

The standalone D3D12 resource-path portion of the spike is now proven on the RTX 4070 Laptop:

- A hardware D3D12 adapter and device are available.
- A direct command queue, allocator, list, fence, and completion event are available.
- Low-resolution color, depth, and motion-vector resources are valid D3D12 textures.
- A separate output-resolution color resource is valid.
- The input is 1280 x 720 and the output is 1920 x 1080.
- A command list that clears the render-target and depth resources completes through a fence.
- `outputs/d3d12-resource-probe-2026-07-10.json` records the machine-readable evidence.

This result does not change the current Minecraft diagnostic. Iris/Sodium still owns OpenGL resources and does not expose this D3D12 device or these textures at the Minecraft frame boundary.

## Recommended Next Implementation Step

## Milestone 4B Result

The optional Streamline 2.12 probe now performs one controlled DLSS SR evaluation over the Milestone 4A D3D12 resources:

- DLSS SR support is confirmed for the selected RTX 4070 Laptop adapter.
- Quality mode selects 1280 x 720 input for 1920 x 1080 output.
- The D3D12 device, options, constants, and four required resource tags are accepted.
- `slEvaluateFeature` returns `eOk` and the evaluated command list completes on the GPU.
- DLSS resources are freed and Streamline shuts down successfully.
- `outputs/streamline-dlss-probe-2026-07-11.json` and `outputs/streamline-logs/sl.log` contain the evidence.

This proves that project-owned D3D12 resources can enter Streamline DLSS SR. It does not alter or unblock the current Minecraft OpenGL resources.

## Milestone 4C Result

The standalone OpenGL-D3D12 interop probe now proves the target NVIDIA driver can bridge one color texture without a CPU texture copy:

- OpenGL 4.6 on the RTX 4070 Laptop exposes the four required Win32 memory-object and semaphore extensions.
- OpenGL imports a D3D12 shared RGBA8 texture and D3D12 shared fence.
- OpenGL writes a deterministic pattern and transfers ownership at fence value 1.
- D3D12 waits, copies the shared texture to readback, returns the resource to common state, and transfers ownership back at value 2.
- OpenGL completes the return wait, and D3D12 readback matches every logical pixel.
- `outputs/gl-d3d12-interop-probe-2026-07-11.json` records the machine-readable evidence.

This result changes the preferred feasibility route from an immediate full renderer replacement to a live Minecraft-context interop spike. It still does not change the current loader diagnostic because the test owns a hidden WGL context rather than Minecraft's context, and it proves only color sharing rather than depth, motion vectors, or a DLSS-ready frame contract.

## Milestone 4D-A Result

The Fabric dev client now verifies the same prerequisite inside Minecraft's live OpenGL context:

- The probe runs once from the HUD callback on the render thread.
- Minecraft's NVIDIA OpenGL 3.2 context exposes all four required Win32 memory-object and semaphore extensions.
- `GL_DEVICE_LUID_EXT` returns `e139010000000000`.
- The JNI D3D12 high-performance adapter query returns `e139010000000000`.
- The result records `adapterLuidMatched=true` and `interopPrerequisitesReady=true`.
- The HUD screenshot and client log preserve the evidence.

This proves that the standalone 4C bridge and Minecraft use the same physical adapter and that Minecraft's context advertises the required entry points. It still does not prove that a shared resource can be imported and synchronized inside Minecraft.

## Recommended Next Implementation Step

Create one project-owned D3D12 RGBA8 texture and shared fence, import both into the proven live LWJGL context, and exercise the same fence-value-1/fence-value-2 exchange without attaching the texture to Minecraft's world framebuffer. Preserve the current loader diagnostic until live world color, depth, motion vectors, camera constants, render/output resolution separation, and post-upscale UI composition are all available.

## Milestone 4D-B Result

The Fabric dev client now completes that controlled exchange inside Minecraft's live OpenGL context:

- Native code owns one 64 x 64 D3D12 RGBA8 texture, one shared fence, command objects, readback storage, Win32 handles, and a mutex-protected positive-ID session.
- Minecraft OpenGL imports both shared objects, uploads the established deterministic pattern, and signals fence value 1.
- D3D12 waits, copies and verifies the texture, returns it to common state, and signals fence value 2.
- Minecraft OpenGL completes the return wait before native code compares every logical pixel with row pitch respected.
- OpenGL objects are deleted before the native session closes; the live result records `resourcesReleased=true` and Minecraft continues rendering.
- `outputs/fabric-live-shared-resource-2026-07-11.log` and `outputs/fabric-live-shared-resource-overlay-2026-07-11.png` preserve the evidence.

This proves one ephemeral project-owned color resource can cross the live Minecraft OpenGL/D3D12 boundary without a CPU texture copy. It does not prove world framebuffer capture, persistent per-frame synchronization, resize/device-loss handling, depth or motion-vector sharing, or DLSS evaluation inside Minecraft.

## Recommended Next Implementation Step

Build a 4D-C world-color capture spike that copies or renders scene color, excluding HUD/UI, into a shared resource at a defined frame boundary. Add explicit resize and per-frame lifetime rules before extending the bridge to depth, motion vectors, camera constants, render/output resolution separation, and post-upscale composition.

## Milestone 4D-C Result

The Fabric client now captures one real Minecraft world-color frame from `WorldRenderEvents.END` before hand and GUI rendering:

- The 854 x 480 main framebuffer is scaled into a temporary 64 x 64 framebuffer backed by the imported D3D12 texture.
- OpenGL validates that the captured pixels are non-uniform and non-black.
- The existing value-1/value-2 semaphore exchange transfers the captured texture to D3D12 and back.
- D3D12 fingerprints the row-pitched readback with the same 64-bit FNV-1a algorithm as Java.
- OpenGL and D3D12 hashes match exactly at `dddc9070aef55428`.
- All capture resources are released and Minecraft continues rendering.
- Evidence is at `outputs/fabric-world-color-capture-2026-07-11.log` and `outputs/fabric-world-color-capture-overlay-2026-07-11.png`.

This proves a real pre-HUD world-color frame can cross the live interop boundary once. It does not prove full-resolution quality, persistent frame pacing, resize handling, depth, motion vectors, camera constants, or DLSS evaluation.

## Recommended Next Implementation Step

Promote the ephemeral color capture to a resize-aware persistent session with bounded per-frame synchronization and timing evidence. Keep the current backend diagnostic blocked until depth, motion vectors, constants, resolution separation, and post-upscale composition are also available.

## Milestone 4D-D Result

The Fabric client now keeps a full-resolution double-buffered color bridge alive across frames:

- Two 854 x 480 shared RGBA8 texture/fence sessions alternate for 120 frames.
- Each slot completes 60 uses, with matching OpenGL and D3D12 hashes on every accepted frame.
- The final run observes 119 frame-hash changes, averages `9.005 ms`, and peaks at `21.939 ms` for the synchronous round trip.
- Both slots remain allocated for 30 additional frames, then release successfully.
- D3D12 readback ranges now exclude nonexistent trailing padding on the final row; this fixes the full-resolution `ID3D12Resource::Map` `E_INVALIDARG` failure that did not occur at 64 x 64.
- Machine-readable runtime evidence is at `outputs/fabric-persistent-full-resolution-color-2026-07-11.log`; the completion overlay was visually verified in the live client.

This proves persistent full-resolution color sharing and explicit lifetime management. It does not yet provide shared depth, motion vectors, camera constants, render/output resolution separation, post-upscale composition, or a DLSS-ready Minecraft frame contract.

## Recommended Next Implementation Step

Add a depth-sharing spike at the same pre-HUD boundary, first validating format, orientation, clear/background behavior, and exact cross-API sampling. Keep `dlss-ready=false` until depth and the remaining frame contract are proven.

## Milestone 4D-E Result

The Fabric client now transfers persistent full-resolution color and hardware-depth data as one frame unit:

- Each A/B slot contains shared `RGBA8` color, shared `R32_FLOAT` depth data, and one shared fence.
- A GLSL 150 integer-fetch pass copies Minecraft's normalized hardware depth without filtering or linearization.
- D3D12 copies both textures in one command list and returns ownership with one signal.
- The 854 x 480 acceptance run reaches 120 verified frames, A/B 60/60 uses, 30 retained frames, and final release.
- Final GL/D3D hashes match for color (`e1487ddb0dd684d7`) and depth (`b1c1aa812bce0d25`).
- All 409,920 depth samples are finite and in range; scene and far/background populations are both nonzero.
- Evidence is at `outputs/fabric-persistent-full-resolution-color-depth-2026-07-11.log`.

This proves the persistent native color/depth resource portion of the frame contract. It does not yet prove motion vectors, current/previous camera transforms, jitter/reset constants, render/output resolution separation, or Streamline evaluation inside Minecraft.

## Recommended Next Implementation Step

Add camera-only motion vectors first, generated from current/previous view-projection transforms and paired with explicit jitter, reset, and motion-vector scaling constants. Validate motion data independently before attempting render/output resolution separation or DLSS evaluation.

## Milestone 4D-F Result

The Fabric client now transfers camera-derived motion with color and depth as one persistent frame unit:

- Each A/B slot contains shared `RGBA8`, `R32_FLOAT`, and `RG16_FLOAT` textures plus one shared fence.
- Motion is current-to-previous displacement in render-pixel units and uses `mvecScale={1/854,1/480}` for Streamline normalization.
- Startup produces a zero-motion reset frame; a controlled stationary phase remains zero and a five-degree camera turn produces bounded nonzero motion.
- The acceptance run reaches 120 verified frames, A/B 60/60 uses, 30 retained frames, and complete release.
- Final motion hashes match at `6ebdde4d158cd056`; all 409,920 vectors are finite and no vectors exceed the render bounds.
- Explicit temporal constants carry current/inverse projection, clip-to-previous and previous-to-current transforms, camera basis/position, near/far/FOV, zero jitter, and reset state.
- Evidence is at `outputs/fabric-persistent-camera-motion-2026-07-11.log`.

This proves camera-only temporal inputs across the live bridge. It does not prove entity-local motion, jittered rendering, reactive/disocclusion masks, render/output resolution separation, Streamline evaluation inside Minecraft, or upscaled presentation.

## Recommended Next Implementation Step

Introduce a lower render resolution and full output resolution while preserving HUD at native resolution. Then tag the live color, depth, motion, and constants for one Streamline DLSS evaluation and return the output texture through the existing shared-resource route.

## Milestone 4D-G Result

The Fabric client now presents continuously evaluated DLSS output in the live Minecraft frame:

- `GameRenderer.renderWorld` is redirected to 569 x 320 while the original 854 x 480 main framebuffer remains unchanged for hand and HUD rendering.
- A/B slots carry FP16 input color, R32F depth, RG16F camera motion, FP16 output, and one shared fence each.
- Streamline Quality evaluation, D3D12 state transitions, exact output readback, OpenGL wait, framebuffer restoration, and full-resolution composite complete every accepted frame.
- The successful run reaches the 30-frame gate, 120 validated frames, A/B 60/60 uses, and 30 retained frames. Final GL/D3D12 hashes match at `6308c60e71394a01`.
- A controlled evaluation failure restores the original framebuffer, linearly presents the low-resolution world for that frame, releases feature/session resources, and leaves subsequent frames on the vanilla full-resolution path.
- Evidence is at `outputs/fabric-live-dlss-presentation-2026-07-11.log` and `outputs/fabric-live-dlss-fallback-2026-07-11.log`.

This establishes feasibility for the complete camera-motion DLSS presentation route. It does not establish entity-local motion quality, reactive/disocclusion masks, jittered rendering, asynchronous frame overlap, or production performance.

## Recommended Next Implementation Step

Add per-object motion and disocclusion/reactive-mask inputs, then remove synchronous diagnostic readbacks and pipeline the A/B slots so OpenGL and D3D12 can overlap.

## Milestone 4D-H Result

The A/B presentation path now separates correctness validation from steady-state delivery:

- 30 frames use synchronous native and OpenGL output fingerprints.
- 300 frames use nonblocking slot selection, no output readback, and a GPU-queued semaphore wait before the native-resolution blit.
- One periodic validation frame rechecks the output after the fast interval.
- The live run reports `COMPLETE`, `ready=true`, A/B `166/165`, matching final hashes, and a `3.650 ms` measured average after the fast interval.
- Evidence is at `outputs/fabric-live-dlss-fast-pipeline-2026-07-11.log`.

The scheduling and backend identifiers are vendor-neutral enough to host future XeSS or FSR 3 upscaling adapters. Only Streamline DLSS is implemented and verified. Resize-triggered session reopen is not yet reliable and remains the next robustness task.
