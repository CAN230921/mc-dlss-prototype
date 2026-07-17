# Milestone 4D-B Live Minecraft Shared Resource Design

Date: 2026-07-11

## Goal

Prove that Minecraft Fabric's live LWJGL OpenGL context can import a project-owned D3D12 shared texture and shared fence, perform one bidirectional GPU synchronization exchange, and produce a D3D12 readback matching an OpenGL-written pattern.

## Selected Approach

Run one ephemeral 64 x 64 round trip immediately after the successful 4D-A live-context diagnostic. The probe does not attach the texture to Minecraft's framebuffer and does not retain the session across frames.

Alternatives were rejected for this milestone:

- A persistent session adds resize, world-change, and device-loss lifecycle before import is proven.
- A direct world-framebuffer hook combines resource import with render-pipeline mutation and makes failures harder to isolate.

## Scope

- Fabric client and Windows x64 only.
- One `DXGI_FORMAT_R8G8B8A8_UNORM` texture at exactly 64 x 64.
- One D3D12 fence shared through an NT handle.
- OpenGL writes the same deterministic RGBA pattern used by Milestone 4C.
- Fence value 1 transfers OpenGL writes to D3D12.
- Fence value 2 transfers D3D12 completion back to OpenGL.
- No Minecraft framebuffer attachment, texture display, depth, motion vectors, Streamline call, render scaling, frame-loop integration, or NeoForge runtime integration.
- The current DLSS backend diagnostic remains blocked.

## Core Contract

`NativeInteropSessionInfo` is a dependency-free record containing:

- `available`
- `sessionId`
- `textureHandle`
- `fenceHandle`
- `width`
- `height`
- `message`

An available session requires positive identifiers and exact 64 x 64 dimensions.

`NativeBridge` gains default unsupported methods for opening, submitting, verifying, and closing an interop session. Existing fake bridges and NeoForge remain source compatible.

`NativeLibraryBridge` maps those methods to JNI and converts missing DLLs, missing symbols, and runtime exceptions into unavailable or false results.

## Native Architecture

`LiveD3D12InteropSession` owns:

- high-performance hardware adapter and D3D12 device
- direct command queue, allocator, and command list
- shareable RGBA8 committed texture in common state
- shareable D3D12 fence
- texture and fence NT handles kept open until session destruction
- readback buffer and completion event

A process-local mutex-protected registry maps monotonically increasing positive session IDs to session objects. JNI never exposes native pointers as IDs.

The native stages are split so the OpenGL wait remains meaningful:

1. `open` creates resources and returns the session ID plus borrowed handle values.
2. `submit` queues a wait for fence value 1, transitions and copies the texture, returns it to common state, and queues a signal for value 2 without a CPU wait.
3. Java queues the OpenGL wait for fence value 2 and calls `glFinish`.
4. `verify` waits at most five seconds for value 2, maps readback, and compares every logical pixel while respecting row pitch.
5. `close` removes the registry entry and releases all native objects.

## Fabric Architecture

`MinecraftLiveInteropSnapshot` records each stable stage:

- `attempted`
- `sessionCreated`
- `memoryImported`
- `semaphoreImported`
- `openGlWriteSubmitted`
- `d3d12ReadbackSubmitted`
- `openGlWaitCompleted`
- `readbackMatched`
- `resourcesReleased`
- `message`

Success requires every stage true.

`MinecraftLiveInteropProbe` runs only when 4D-A reports ready and only on the render thread. It:

1. Opens the native session.
2. Imports the borrowed texture handle with `GL_HANDLE_TYPE_D3D12_RESOURCE_EXT` and size zero.
3. Creates external `GL_RGBA8` texture storage.
4. Imports the borrowed fence handle with `GL_HANDLE_TYPE_D3D12_FENCE_EXT`.
5. Uploads the deterministic pattern.
6. Signals OpenGL fence value 1 and flushes.
7. Requests native D3D12 submission.
8. Waits in OpenGL for value 2 and finishes.
9. Requests native readback verification.
10. Deletes the OpenGL semaphore, texture, and memory object.
11. Closes the native session in a `finally` block.

The result is cached beside the 4D-A snapshot and appended to the existing HUD and one log line.

## Ownership And Error Handling

- Importing Win32 handles does not transfer handle ownership to OpenGL; native code keeps and closes the handles.
- Java deletes all OpenGL objects before closing the native session.
- Every JNI registry operation validates the session ID.
- A close call is idempotent from Java's perspective.
- Native waits are bounded to five seconds.
- OpenGL errors are checked after every import, storage, semaphore, and wait stage.
- The HUD catches all runtime failures and continues rendering the existing blocked diagnostic.
- A failed probe is not retried automatically in the same process.

## Testing

- Pure Java tests cover session validation, default bridge behavior, live snapshot success gating, and overlay formatting.
- Pure native tests cover deterministic pattern generation and readback row-pitch comparison.
- Native CTest exercises session creation and cleanup without OpenGL.
- Gradle compilation verifies all LWJGL calls.
- Runtime verification enters a world, requires every 4D-B stage true in the log and HUD, and captures an F2 screenshot.
- Existing Java, metadata, JNI, native, D3D12, 4C, and 4D-A checks remain green.

## Acceptance Criteria

- Minecraft creates one native session after 4D-A readiness.
- OpenGL imports the texture and fence without error.
- D3D12 observes fence value 1 and submits the readback copy.
- OpenGL completes the wait for value 2.
- Native readback matches every pixel written by Minecraft OpenGL.
- All OpenGL and native resources are released.
- HUD and log report every 4D-B stage true.
- Minecraft continues rendering normally after cleanup.
- The DLSS backend diagnostic remains blocked.

## Non-Claim

Passing 4D-B proves one ephemeral shared texture can round-trip inside Minecraft's live context. It does not prove that Minecraft world color can render into the texture, that per-frame synchronization is stable, or that DLSS has the required depth, motion vectors, constants, and composition timing.
