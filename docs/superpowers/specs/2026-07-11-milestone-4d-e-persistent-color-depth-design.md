# Milestone 4D-E Persistent Color And Depth Design

Date: 2026-07-11

## Goal

Prove that Minecraft can sustain a resize-aware, full-resolution, double-buffered OpenGL-D3D12 frame bridge containing both pre-HUD world color and hardware depth for 120 consecutive frames, retain the resources for 30 additional frames, and release them cleanly.

## Selected Approach

Extend each persistent frame slot to contain a shared `RGBA8` color texture and a shared `R32_FLOAT` depth-data texture under one shared D3D12 fence. Minecraft OpenGL copies world color with a framebuffer blit and extracts the main framebuffer's hardware depth through a small full-screen shader into `R32_FLOAT`. One semaphore handoff covers both textures; D3D12 copies both resources to readback buffers and returns ownership with one signal.

The depth texture contains the original normalized hardware depth values sampled from Minecraft's depth attachment. It is not linearized in this milestone. Streamline accepts one-channel `R32_FLOAT` hardware depth when tagged as depth, while the format remains straightforward to sample, share, read back, fingerprint, and validate.

## Alternatives Rejected

- A shared `D32_FLOAT` depth-stencil resource would resemble a conventional depth target but adds external-memory depth attachment, resource-flag, clear-value, and cross-API layout constraints before basic data transfer is proven.
- A CPU `glReadPixels` depth copy followed by D3D12 upload would not prove the required GPU-shared resource path.
- A one-frame depth probe would be simpler but would leave persistent lifetime, paired color/depth synchronization, resize behavior, and frame-to-frame depth changes unproven.
- A separate fence and command queue per depth texture would work, but it would not model one coherent frame handoff and would double synchronization operations.

## Scope

- Fabric client, Windows x64, Minecraft 1.21.1, and the existing NVIDIA OpenGL-D3D12 interop route.
- Two equal full-resolution frame slots matching `Framebuffer.textureWidth` and `textureHeight`.
- Each slot owns one shared `DXGI_FORMAT_R8G8B8A8_UNORM` color texture, one shared `DXGI_FORMAT_R32_FLOAT` depth-data texture, one shared fence, one D3D12 queue/list/allocator, and separate color/depth readback buffers.
- Exactly 120 successfully verified frames followed by exactly 30 retained idle world frames.
- Resize rebuilds both complete slots as transactional frame units.
- No motion vectors, jitter, camera matrices, depth linearization, render/output resolution separation, Streamline evaluation, upscaling, or post-upscale composition.

## Native Frame-Session Contract

The existing 4D-B, 4D-C, and color-only 4D-D interfaces remain available for regression coverage. Add a new frame-session contract rather than changing the primitive-array meaning of deployed color-only methods.

`NativePersistentFrameSessionInfo` reports availability, session ID, color texture handle, depth texture handle, fence handle, dimensions, and message.

`NativeBridge` adds:

- `openD3D12PersistentFrameSession(int width, int height)`
- `submitD3D12PersistentFrameReadback(long sessionId, long waitValue, long signalValue)`
- `inspectD3D12PersistentFrameReadback(long sessionId, long signalValue)`
- `closeD3D12PersistentFrameSession(long sessionId)`

Inspection returns a `NativeFrameReadbackFingerprint` containing color and depth fingerprints, depth minimum/maximum bit patterns converted to floats, finite/in-range/sample counts, status, fence diagnostics, and message.

JNI arrays have fixed documented field counts. Missing symbols, malformed arrays, invalid fence requests, timeouts, missing sessions, and native exceptions become structured unavailable results rather than zero-valued successful fingerprints.

## D3D12 Resources

For each slot:

- Color texture: `DXGI_FORMAT_R8G8B8A8_UNORM`, shared committed default-heap texture, initial/common state `COMMON`.
- Depth-data texture: `DXGI_FORMAT_R32_FLOAT`, shared committed default-heap texture, initial/common state `COMMON`.
- One shared D3D12 fence and Win32 handle.
- One readback buffer and `D3D12_PLACED_SUBRESOURCE_FOOTPRINT` per texture.
- Read ranges end after the logical bytes of the final row, excluding nonexistent trailing row padding.

One command list waits for the OpenGL odd fence value, transitions both textures from `COMMON` to `COPY_SOURCE`, copies both to their readbacks, transitions both back to `COMMON`, executes, and signals the even value.

## OpenGL Resources

Each Fabric slot imports both D3D12 texture handles through separate memory objects and imports the shared fence once.

- Color uses `GL_RGBA8` and a color framebuffer attachment.
- Depth data uses `GL_R32F` and a color framebuffer attachment used only by the depth extraction shader.
- The extraction shader samples `Framebuffer.getDepthAttachment()` as `sampler2D`, uses integer pixel coordinates, and writes the sampled normalized depth value to the red channel without filtering or linearization.
- The shader renders one full-screen triangle at exact framebuffer dimensions with depth testing, blending, scissor, and culling disabled for the extraction pass.

All modified GL program, vertex-array, active texture, texture, framebuffer, viewport, depth-test, blend, scissor, cull, and color-mask state is saved and restored in `finally`.

## Per-Frame Flow

For the selected A/B frame slot:

1. Validate the Minecraft main color framebuffer, depth attachment, dimensions, and render thread.
2. Save all GL state touched by the color and depth passes.
3. Blit full-resolution pre-HUD color into the shared color texture.
4. Render the depth extraction pass from Minecraft's depth texture into shared `R32_FLOAT`.
5. Read both shared textures in OpenGL and compute color/depth fingerprints and depth statistics.
6. Require color to be non-uniform and non-black.
7. Require every sampled depth value to be finite and in `[0,1]`, depth to be non-uniform, at least one value below the far-plane threshold, and at least one far/background value.
8. Signal the slot's odd fence value for both textures and flush.
9. Submit one D3D12 command list that copies color and depth, then signals the even value.
10. Wait in OpenGL for the even value covering both textures and finish.
11. Inspect D3D12 readbacks for the exact signal value.
12. Require exact color-byte and depth-float-bit FNV-1a hash matches plus identical depth statistics.
13. Restore all GL state in `finally`.

## Depth Validation

Depth fingerprinting hashes the raw little-endian IEEE-754 bytes for every logical `R32_FLOAT` pixel in row order. OpenGL and native implementations use the same FNV-1a constants and exclude row padding.

The snapshot records:

- depth hash
- minimum and maximum depth
- finite sample count
- in-range sample count
- scene sample count below `0.9999`
- far/background sample count at or above `0.9999`
- non-uniform flag
- consecutive depth-hash changes

Success requires all samples finite and in range, both scene and far/background samples present, non-uniform depth, exact GL/D3D hash agreement, and at least one depth-hash change across the 120-frame run. The acceptance run keeps the existing moving world/hand behavior; explicit camera input is used only if hashes remain static.

## State Machine And Lifetime

Reuse the `WAITING`, `CAPTURING`, `RETAINING`, `COMPLETE`, and `FAILED` lifecycle pattern from 4D-D.

- `WAITING`: prerequisites are incomplete or no world is loaded.
- `CAPTURING`: alternate complete color/depth slots and verify each frame.
- `RETAINING`: after frame 120, keep all color/depth/GL/D3D resources alive for 30 world frames without capture.
- `COMPLETE`: release both complete slots and publish final metrics.
- `FAILED`: release every partially created resource once and cache the failure.

Per-slot fence pairs remain `1/2`, `3/4`, and so on. Resize releases and recreates both frame slots. Successful frame count may continue after resize, while `resizeCount` increments. World unload releases resources and records failure.

## Metrics And HUD

`MinecraftPersistentFrameCaptureSnapshot` reports:

- lifecycle state and success
- dimensions
- successful frames out of 120
- retained frames out of 30
- slot A/B use counts
- resize count
- color and depth hash-change counts
- latest GL/D3D color hashes
- latest GL/D3D depth hashes
- depth min/max and sample counts
- average and maximum synchronous frame-bridge time
- resources released
- message

Log lifecycle transitions and the final summary under `[mc_dlss/fabric/persistent-frame]`. The existing color-only line remains unchanged for historical evidence.

## Error Handling

- Native waits remain bounded to 5,000 ms.
- Pair creation is transactional across both textures and both slots.
- All Java byte-size arithmetic and native footprint arithmetic are checked for overflow.
- Shader compile/link failures include the OpenGL info log.
- Missing or zero Minecraft depth attachment fails with a direct message.
- Every frame restores GL state even when shader rendering, synchronization, readback, or validation fails.
- Native close remains idempotent; OpenGL objects are deleted before their native frame session.
- Failure does not retry automatically in the same process.

## Testing

- Pure Java tests cover frame-session array decoding, depth fingerprint known values, NaN/infinity/out-of-range rejection, thresholds, snapshot success gating, state thresholds, and overlay formatting.
- Pure native tests cover two-resource session dimensions, both texture handles, fence validation, padded `R32_FLOAT` footprint hashing, status diagnostics, and idempotent close.
- Shader source construction is isolated and tested for required version, integer texel fetch, red-channel output, and no linearization.
- Gradle compilation verifies Minecraft/LWJGL symbols including `Framebuffer.getDepthAttachment()`.
- Runtime acceptance requires 120/120 verified frames, A/B 60/60 uses, color and depth hash changes, 30/30 retained frames, exact cross-API hashes, valid depth statistics, final release, and continued Minecraft rendering.
- Existing Java tests, eight native CTests, complete Gradle build, and previous live color diagnostics remain green. The optional Streamline NGX shutdown regression is not rerun.

## Acceptance Criteria

- Two full-resolution frame slots are created; each exposes imported color and depth textures and is used 60 times.
- All 120 frames complete one paired color/depth OpenGL-to-D3D12 and D3D12-to-OpenGL exchange.
- Every accepted frame has matching GL/D3D color hashes and matching GL/D3D depth hashes.
- Every depth sample is finite and within `[0,1]`; depth is non-uniform and contains both scene and far/background samples.
- At least one color hash change and one depth hash change are observed.
- Both slots remain alive for 30 idle frames and all resources release afterward.
- Minecraft continues rendering after cleanup.
- Backend remains `OPENGL`, `BLOCKED_UNSUPPORTED_BACKEND`, and `dlss-ready=false`.

## Non-Claim

Passing 4D-E proves persistent full-resolution color and hardware-depth data sharing with one coherent per-slot fence. It does not prove correct DLSS camera constants, depth linearization requirements, motion vectors, jitter/reset behavior, asynchronous overlap, production frame time, render/output resolution separation, Streamline evaluation inside Minecraft, or final upscaled composition.
