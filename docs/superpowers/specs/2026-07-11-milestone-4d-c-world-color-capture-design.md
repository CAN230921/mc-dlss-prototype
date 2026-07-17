# Milestone 4D-C World Color Capture Design

Date: 2026-07-11

## Goal

Prove that one real Minecraft world-color frame, excluding hand and GUI/HUD rendering, can be scaled into the existing 64 x 64 OpenGL-D3D12 shared-resource path and observed identically by both APIs.

## Selected Approach

Run one capture from Fabric `WorldRenderEvents.END` on the frame after Milestone 4D-B succeeds. Fabric documents this event as occurring after world rendering and before hand/held-item and GUI rendering. The probe blits the current world framebuffer into a second ephemeral 64 x 64 shared RGBA8 texture, fingerprints that texture in OpenGL and D3D12, compares the fingerprints, and releases everything.

This is the fastest useful gate because it reuses the proven 4D-B session, imports, fence values, and cleanup order. Full-resolution capture and persistent per-frame resources remain out of scope.

## Alternatives Rejected

- Full-resolution one-shot capture adds dynamic native dimensions and larger readback cost without proving more about the frame boundary.
- Persistent per-frame capture adds resize, world transition, device-loss, and synchronization lifecycle before real scene color has crossed once.
- Capturing from the HUD callback would include UI and occur too late for the intended DLSS composition order.

## Scope

- Fabric client and Windows x64 only.
- One 64 x 64 `DXGI_FORMAT_R8G8B8A8_UNORM` capture.
- One execution per Minecraft process, after 4D-B success and only while a world is loaded.
- Source is the framebuffer bound at `WorldRenderEvents.END`.
- Destination is a temporary framebuffer whose color attachment is the imported shared texture.
- OpenGL performs a linear color blit from the full framebuffer dimensions to 64 x 64.
- Fence value 1 transfers the captured texture to D3D12; value 2 transfers completion back to OpenGL.
- No hand, HUD, menus, depth, motion vectors, Streamline call, render scaling, world framebuffer replacement, or persistent resources.

## Fingerprint Contract

Both sides compute 64-bit FNV-1a over all 64 x 64 x 4 bytes in row order. They also report whether at least two different RGBA pixels exist and count non-black RGB pixels.

`NativeReadbackFingerprint` contains:

- `available`
- `hash`
- `nonUniform`
- `nonBlackPixelCount`
- `message`

A successful world capture requires:

- OpenGL framebuffer completeness.
- OpenGL blit and readback without an error.
- OpenGL output is non-uniform and has at least one non-black pixel.
- D3D12 submission and both fence directions complete.
- Native readback is available and non-uniform.
- Native and OpenGL unsigned 64-bit hashes match exactly.
- Every OpenGL and native resource is released.

The existing deterministic 4D-B verification remains unchanged.

## Native Changes

`LiveD3D12InteropSession` gains a readback fingerprint operation that uses the same bounded value-2 wait and row-pitch-aware mapping as deterministic verification. It returns hash, non-uniform state, and non-black count without comparing against the test pattern.

The registry and JNI expose `inspectD3D12InteropReadback(long sessionId)`. Invalid IDs, missing symbols, timeouts, and native exceptions produce an unavailable fingerprint. Session IDs remain registry-owned positive integers; native pointers remain private.

## Fabric Changes

`MinecraftWorldColorCaptureProbe` performs the one-shot render-thread operation:

1. Open a second 64 x 64 native session.
2. Import its texture and fence exactly as in 4D-B.
3. Create a temporary draw framebuffer and attach the shared texture.
4. Save the current read/draw framebuffer bindings and texture binding.
5. Blit the current world framebuffer to the shared texture at 64 x 64.
6. Read the destination framebuffer in OpenGL and compute its fingerprint.
7. Signal value 1, submit the existing D3D12 copy, wait for value 2, and inspect native readback.
8. Compare the fingerprints and content checks.
9. Restore all saved OpenGL bindings.
10. Delete framebuffer, semaphore, texture, and memory object before closing the native session.

`MinecraftWorldColorCaptureSnapshot` records framebuffer attachment, blit, OpenGL content validation, D3D12 submission, return wait, fingerprint match, and release stages. The cached result is logged once with prefix `[mc_dlss/fabric/world-color]` and appended to the HUD.

## Error Handling

- The probe runs only on Minecraft's render thread and only from `WorldRenderEvents.END`.
- A failed probe is cached and is not retried automatically.
- OpenGL errors are isolated and checked after framebuffer setup, blit, readback, signal, wait, and cleanup.
- Original framebuffer and texture bindings are restored in `finally`.
- Native waits remain bounded to 5,000 milliseconds.
- Cleanup runs for partial initialization and preserves world rendering even when capture fails.

## Testing

- Pure Java tests cover FNV-1a output, non-uniform/non-black analysis, native fingerprint validation, snapshot success gating, and overlay formatting.
- Pure native tests cover row-pitched fingerprint calculation and mismatch sensitivity.
- Existing live-session CTest continues to cover session creation and cleanup.
- Gradle compilation verifies Fabric event and LWJGL framebuffer calls.
- Runtime acceptance enters a world, requires every world-color stage true in HUD/log, captures an F2 screenshot, and confirms Minecraft continues rendering after cleanup.
- Existing Java, native, metadata, Gradle, 4C, 4D-A, and 4D-B checks remain green. The known optional Streamline NGX shutdown regression is not rerun.

## Acceptance Criteria

- Capture executes exactly once after 4D-B success from `WorldRenderEvents.END`.
- The temporary framebuffer is complete and the world-color blit succeeds.
- OpenGL sees non-uniform, non-black captured pixels.
- D3D12 readback completes and reports the exact same FNV-1a hash.
- HUD/log report every 4D-C stage true and resources released.
- Minecraft continues rendering normally after cleanup.
- The existing backend remains `OPENGL`, `BLOCKED_UNSUPPORTED_BACKEND`, and `dlss-ready=false`.

## Non-Claim

Passing 4D-C proves one downscaled world-color frame can enter the shared path before HUD composition. It does not prove full-resolution quality, persistent frame pacing, resize handling, depth, motion vectors, camera constants, separate render/output resolution, Streamline evaluation, or final composition.
