# Milestone 4D-A Minecraft OpenGL Context Diagnostic Design

Date: 2026-07-11

## Goal

Prove that Minecraft Fabric 1.21.1's live render-thread OpenGL context exposes the Win32 external-memory and semaphore capabilities required by the standalone Milestone 4C bridge, and that the OpenGL context uses the same physical adapter as the project's D3D12 path.

## Approaches Considered

### 1. Render-thread capability and adapter diagnostic

This is the selected approach. It performs one read-only probe from the first HUD callback, compares OpenGL and D3D12 adapter LUID values, and displays the result. It has the smallest rendering risk and gives a precise gate before importing resources.

### 2. Import a shared texture immediately

This would prove more in one milestone, but a handle, ownership, or synchronization mistake would execute inside Minecraft's renderer before the actual context identity is known. It is deferred to 4D-B.

### 3. Add a world-frame mixin immediately

This would move closer to real DLSS timing, but it combines frame-boundary selection, OpenGL capability checks, native resources, and synchronization in one change. It is deferred until the live context is proven.

## Scope

- Fabric client only for the first live-context check.
- Minecraft 1.21.1, Fabric Loader 0.19.3, Fabric API 0.116.13+1.21.1, and LWJGL 3.3.3.
- One probe on Minecraft's render thread after an OpenGL context is current.
- No OpenGL memory-object creation, D3D12 shared resource creation, texture import, semaphore import, world framebuffer hook, DLSS evaluation, render-scale change, or GUI reordering.
- NeoForge retains its existing diagnostic behavior in this milestone.
- The current backend diagnostic remains blocked even when all 4D-A checks pass.

## Architecture

The core module gains a platform-neutral `NativeAdapterIdentity` result and a default `NativeBridge.probeD3D12Adapter()` method. Existing fake bridges and NeoForge code remain source compatible because the default reports unsupported.

`NativeLibraryBridge` calls a new JNI method only when the native DLL is loaded. The native implementation selects the same high-performance, non-software D3D12 adapter policy used by the standalone probes and returns the adapter LUID as 16 lowercase hexadecimal characters representing the eight raw `LUID` bytes.

The Fabric module gains two focused classes:

- `MinecraftGlInteropSnapshot` stores OpenGL identity, four extension booleans, OpenGL LUID, D3D12 LUID, match status, and a stable message.
- `MinecraftGlInteropProbe` runs only on the render thread. It reads `GL_VENDOR`, `GL_RENDERER`, and `GL_VERSION`, checks `GL_EXT_memory_object`, `GL_EXT_memory_object_win32`, `GL_EXT_semaphore`, and `GL_EXT_semaphore_win32`, queries `GL_DEVICE_LUID_EXT` into an eight-byte direct buffer, formats the bytes identically to native code, and compares the two LUID strings.

`McDlssFabricClientMod` stores the first completed snapshot and never probes again during the process. Its existing HUD keeps the current DLSS-blocked lines and appends compact interop lines.

## Data Flow

1. Fabric initializes the client and registers the existing HUD callback.
2. The first callback runs on Minecraft's render thread with the OpenGL context current.
3. `MinecraftGlInteropProbe` reads capabilities and OpenGL identity.
4. If the Win32 memory-object extension is available, it queries the OpenGL device LUID.
5. `NativeBridge.probeD3D12Adapter()` obtains the selected D3D12 adapter LUID through JNI.
6. The probe compares canonical LUID strings and produces an immutable snapshot.
7. Later HUD frames only render the cached snapshot.

## Result Contract

The live snapshot contains:

- `probed`
- `openGlVendor`
- `openGlRenderer`
- `openGlVersion`
- `memoryObjectExtension`
- `memoryObjectWin32Extension`
- `semaphoreExtension`
- `semaphoreWin32Extension`
- `openGlLuid`
- `d3d12Luid`
- `adapterLuidMatched`
- `interopPrerequisitesReady`
- `message`

`interopPrerequisitesReady` is true only when all four extensions are present, both LUID values are valid canonical strings, and they match exactly.

## Error Handling

- Calling the OpenGL probe off the render thread returns a failure snapshot rather than making GL calls.
- Missing extensions produce a stable failure message and skip unsupported LUID queries.
- A missing native DLL or JNI symbol produces an unavailable D3D12 identity without crashing the client.
- OpenGL errors after the LUID query are captured in the snapshot.
- The HUD callback catches runtime failures, caches one failure snapshot, and continues rendering the existing diagnostic.
- No retry occurs automatically; a new process is required for a fresh probe.

## Testing

- Pure core tests cover native adapter identity validation and the default bridge fallback.
- Pure Fabric-side tests cover snapshot readiness, LUID normalization/comparison, extension gating, and compact overlay lines without creating an OpenGL context.
- Gradle compilation verifies the LWJGL render-thread probe against Minecraft/Fabric dependencies.
- Native tests verify canonical LUID formatting and JNI symbol availability.
- Runtime verification launches the Fabric dev client, captures log evidence and a screenshot of the HUD, and confirms the live OpenGL and D3D12 LUID values match.
- Existing Java, metadata, JNI, native, D3D12, and 4C interop checks remain green.

## Acceptance Criteria

- The probe executes exactly once on Minecraft's render thread.
- Minecraft reports all four required extensions.
- OpenGL vendor, renderer, version, and an eight-byte device LUID are captured.
- The native DLL returns a canonical D3D12 adapter LUID.
- OpenGL and D3D12 LUID values match.
- The HUD shows `interop-ready: true` and the matching LUID.
- A fresh log and screenshot record the result.
- The backend diagnostic still reports DLSS evaluation blocked.

## Non-Claim

Passing 4D-A proves capability and adapter identity only. It does not prove that Minecraft can import a D3D12 texture, render world color into shared memory, synchronize each frame, provide depth or motion vectors, or run DLSS inside Minecraft.
