# Milestone 4D-D Persistent Full-Resolution Color Design

Date: 2026-07-11

## Goal

Prove that Minecraft can sustain a resize-aware, full-resolution, double-buffered OpenGL-D3D12 world-color path for 120 consecutive pre-HUD frames, verify every frame on both APIs, retain the resources for 30 additional frames, and release them cleanly.

## Selected Approach

Create two persistent shared RGBA8 slots at the current Minecraft framebuffer dimensions. Alternate slots from `WorldRenderEvents.END`, copy one full world frame per event, synchronize with monotonically increasing per-slot fence values, compare full-frame OpenGL and D3D12 FNV-1a fingerprints, and collect timing metrics.

Each frame remains synchronously verified with a bounded wait. Double buffering in this milestone proves alternating persistent-resource lifecycle and prepares the ownership model; it does not claim asynchronous overlap or production frame pacing.

## Alternatives Rejected

- A single persistent 64 x 64 texture would prove repetition but not full-resolution allocation, bandwidth, or resize behavior.
- A fully asynchronous two-frame pipeline would combine persistent lifetime with deferred verification and in-flight teardown, making first failure diagnosis slower.
- Adding depth and motion vectors now would cross multiple unproven format/state boundaries at once.

## Scope

- Fabric client and Windows x64 only.
- Two project-owned shared `DXGI_FORMAT_R8G8B8A8_UNORM` textures matching `Framebuffer.textureWidth` and `textureHeight`.
- Exactly 120 successfully verified capture frames.
- Exactly 30 additional world-render frames with both slots retained but idle before cleanup.
- One full-resolution `glBlitFramebuffer` from Minecraft's main framebuffer per capture frame.
- One OpenGL full-frame readback and one D3D12 full-frame readback fingerprint per capture frame.
- Window/framebuffer resize detection before each frame; both slots are drained, released, and recreated as a pair at the new dimensions.
- No HUD, hand, menus, depth, motion vectors, Streamline call, output upscaling, asynchronous overlap, or world framebuffer replacement.

## Persistent Native Contract

Existing 4D-B/4D-C fixed 64 x 64 APIs remain unchanged.

`NativePersistentInteropSessionInfo` contains availability, session ID, texture handle, fence handle, width, height, and message. Valid dimensions are 1 through 8192 in both axes.

`NativeBridge` adds:

- `openD3D12PersistentInteropSession(int width, int height)`
- `submitD3D12PersistentReadback(long sessionId, long waitValue, long signalValue)`
- `inspectD3D12PersistentReadback(long sessionId, long signalValue)`
- `closeD3D12PersistentInteropSession(long sessionId)`

The JNI layer returns structured primitive arrays and converts missing symbols or runtime failures into unavailable/false results.

## Native Session Lifecycle

Each slot owns its own D3D12 texture, fence, allocator, command list, readback buffer, Win32 handles, and completion event. For each reuse:

1. Require odd `waitValue`, `signalValue == waitValue + 1`, and values greater than the slot's prior signal.
2. Require the prior signal to be complete before resetting the allocator and command list.
3. Queue D3D12 wait on the OpenGL odd value.
4. Transition COMMON to COPY_SOURCE, copy full resolution to readback, and transition back to COMMON.
5. Execute and signal the even value without a CPU wait.
6. Inspection waits at most 5,000 ms for that exact even value, maps with row pitch, fingerprints all logical pixels, and caches the inspected signal.

Close remains idempotent through the registry. Registry IDs are positive monotonic integers, never pointers.

## Fence Schedule

Slots A and B maintain independent sequences:

- First use: OpenGL signals 1, D3D12 signals 2.
- Second use of the same slot: OpenGL signals 3, D3D12 signals 4.
- Continue by adding 2 per slot reuse.

The Java state machine rejects overflow, non-monotonic values, and slot reuse before the prior frame completed.

## Fabric State Machine

`MinecraftPersistentColorCaptureProbe` is created after 4D-C succeeds and is called once per `WorldRenderEvents.END` event.

States are `WAITING`, `CAPTURING`, `RETAINING`, `COMPLETE`, and `FAILED`.

- `WAITING`: no loaded world or prerequisite result yet.
- `CAPTURING`: alternate A/B slots and verify each frame.
- `RETAINING`: after frame 120, keep both complete slots alive for 30 more world frames without capture.
- `COMPLETE`: release both slots and publish final metrics.
- `FAILED`: release partial resources once, cache failure, and never retry in the process.

On dimension change during `CAPTURING`, both slots are synchronously released and recreated. Successful frame count continues, while `resizeCount` increments. A failed pair recreation fails the probe.

## Per-Frame Flow

For the selected slot:

1. Save read/draw framebuffer and texture bindings.
2. Bind Minecraft's main FBO for read and the slot FBO for draw.
3. Blit the complete color target at native dimensions.
4. Read the slot in OpenGL and calculate full-frame FNV/non-uniform/non-black metrics.
5. Signal the slot's odd fence value and flush.
6. Submit native D3D12 readback with the odd/even pair.
7. Wait in OpenGL for the even value and finish.
8. Inspect the native readback for that exact value.
9. Require matching hashes, non-uniform content, and at least one non-black pixel.
10. Restore all bindings in `finally`.

OpenGL objects and native sessions remain allocated between frames and are deleted only on resize, failure, or final cleanup.

## Metrics And HUD

`MinecraftPersistentColorCaptureSnapshot` reports:

- state and success
- current dimensions
- successful frames out of 120
- retained frames out of 30
- slot A/B use counts
- resize count
- distinct consecutive hash changes
- latest OpenGL/D3D12 hash
- average and maximum capture time in milliseconds
- resources released
- message

Log only lifecycle transitions, failures, and the final summary under `[mc_dlss/fabric/persistent-color]`. The HUD updates progress each frame without logging every frame.

Success requires 120/120 verified frames, both slots used, at least one hash change, 30/30 retained frames, final resources released, and no failed state. Runtime instructions ask the user to move the camera during capture so the hash-change requirement represents real frame updates.

## Error Handling

- All work runs on Minecraft's render thread.
- Native waits remain bounded to 5,000 ms.
- Every frame restores GL bindings even when it fails.
- Pair creation is transactional: if either slot fails, both are released.
- Resize cleanup occurs before pair recreation.
- Failure is cached and does not retry automatically.
- World unload during capture releases resources and records failure rather than retaining handles across worlds.
- Arithmetic validates dimensions, byte sizes, and fence progression before allocation or submission.

## Testing

- Pure Java tests cover dimension validation, per-slot fence schedules, state transitions, 120/30 thresholds, hash-change counting, resize counting, success gating, and overlay formatting.
- Pure native tests cover dynamic session dimension validation and repeated submission argument validation without changing fixed 4D-B behavior.
- Existing fingerprint tests cover full logical rows with padded D3D row pitch.
- Gradle compilation verifies Fabric/LWJGL integration.
- Runtime acceptance moves the camera during 120 capture frames, waits through 30 retained frames, requires final success and release, captures F2 evidence, and confirms Minecraft continues rendering.
- Existing Java, native, metadata, Gradle, 4C, 4D-A, 4D-B, and 4D-C checks remain green. The known optional Streamline NGX shutdown regression is not rerun.

## Acceptance Criteria

- Two full-resolution imported slots are created and both are used.
- All 120 frames complete GL-to-D3D12 and D3D12-to-GL synchronization with exact hash matches.
- At least one consecutive frame hash change is observed.
- A resize, if it occurs, recreates both slots and capture continues; zero resize events is acceptable when the window remains unchanged.
- Both slots remain alive for 30 idle world frames after capture.
- Final cleanup releases all GL/native resources and Minecraft continues rendering.
- Average and maximum synchronous capture timings are reported honestly and are not presented as production performance.
- Backend remains `OPENGL`, `BLOCKED_UNSUPPORTED_BACKEND`, and `dlss-ready=false`.

## Non-Claim

Passing 4D-D proves full-resolution persistent color-resource lifetime and repeated synchronous interoperability. It does not prove asynchronous overlap, acceptable production frame time, depth/motion-vector sharing, DLSS constants, render/output resolution separation, Streamline evaluation in Minecraft, or final composition.
