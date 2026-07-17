# Milestone 4C OpenGL-D3D12 Interop Design

Date: 2026-07-11

## Goal

Prove on the target RTX 4070 Laptop that an OpenGL context and D3D12 can use the same Windows texture allocation and D3D12 fence without CPU texture copies.

This milestone is successful when OpenGL imports a D3D12 shared texture and fence, writes a deterministic pixel pattern, signals the shared fence, D3D12 waits and reads the same pattern back, then signals a second fence value that OpenGL waits for successfully.

## Scope

- Windows 10/11 x64 only.
- OpenGL and D3D12 only; Streamline evaluation remains in the existing Milestone 4B probe.
- One `DXGI_FORMAT_R8G8B8A8_UNORM` texture at 64 x 64.
- One hidden Win32 window and WGL context owned by the probe.
- `GL_EXT_memory_object`, `GL_EXT_memory_object_win32`, `GL_EXT_semaphore`, and `GL_EXT_semaphore_win32` are mandatory.
- No Minecraft render hook, Iris patch, Sodium patch, depth sharing, motion vectors, DLSS invocation, or frame-loop integration.
- The default native build remains independent of the Streamline SDK.

## Selected Approach

Add a project-owned executable named `mc_dlss_gl_d3d12_interop_probe`. It is separate from the existing D3D12 resource and Streamline probes so an interop failure cannot weaken already proven milestones.

The probe performs these stages:

1. Create a hidden Win32 window, select an OpenGL-capable pixel format, create a WGL context, and make it current.
2. Record the OpenGL vendor, renderer, and version.
3. Verify the four required extension strings and load every required entry point through `wglGetProcAddress`.
4. Select a hardware D3D12 adapter and create a device, direct queue, command allocator, and command list.
5. Create a shareable 64 x 64 `DXGI_FORMAT_R8G8B8A8_UNORM` committed texture with `D3D12_HEAP_FLAG_SHARED` and initial state `D3D12_RESOURCE_STATE_COMMON`.
6. Create a fence with `D3D12_FENCE_FLAG_SHARED` and export NT handles for the texture and fence.
7. Import the texture into an OpenGL memory object with `GL_HANDLE_TYPE_D3D12_RESOURCE_EXT`, then bind it to a `GL_TEXTURE_2D` using immutable external storage.
8. Import the fence into an OpenGL semaphore with `GL_HANDLE_TYPE_D3D12_FENCE_EXT`.
9. Upload a deterministic RGBA pattern through OpenGL and signal fence value 1 with the imported texture declared in `GL_LAYOUT_GENERAL_EXT`.
10. Make the D3D12 queue wait for value 1, transition the shared texture from common to copy-source state, copy it to a readback buffer, and signal fence value 2.
11. Set the imported OpenGL semaphore to value 2, wait on it, and complete the OpenGL queue.
12. Map the readback buffer and compare representative pixels and the full row payload against the expected pattern.

## Ownership And Synchronization

All COM objects use `Microsoft::WRL::ComPtr`. Win32 handles, window classes, device contexts, windows, WGL contexts, OpenGL memory objects, textures, and semaphores use destructor-backed owners.

The probe duplicates exported NT handles before passing them to OpenGL. This isolates OpenGL import ownership rules from the D3D12-side owner and lets cleanup remain deterministic.

Fence values are fixed:

- Value 1 means OpenGL has completed its texture writes and released the texture to D3D12.
- Value 2 means D3D12 has completed the readback copy and released the texture back to OpenGL.

No CPU wait is used between the OpenGL signal and the D3D12 queue wait. A bounded CPU wait is used only at the end to confirm D3D12 completion and safely inspect readback memory.

## Report Contract

The executable writes schema-version-1 JSON with these stable fields:

- `success`
- `adapterName`
- `openGlVendor`
- `openGlRenderer`
- `openGlVersion`
- `requiredExtensions`
- `entryPointsLoaded`
- `sharedTextureCreated`
- `sharedFenceCreated`
- `memoryImported`
- `semaphoreImported`
- `openGlWriteSubmitted`
- `d3d12WaitCompleted`
- `readbackMatched`
- `d3d12SignalCompleted`
- `openGlWaitCompleted`
- `message`

`success` is true only when every required extension, import, synchronization, and readback stage succeeds. Pointer and handle values are never written to the report.

## Error Handling

- Missing extensions or entry points produce a valid failure report and non-zero exit code.
- D3D12, Win32, WGL, and OpenGL failures identify the exact failed stage in `message`.
- Every execution path attempts deterministic cleanup.
- Fence waits use a 10-second timeout.
- The probe script parses the report and independently asserts every required success field.

## Testing

Pure C++ tests cover report validation, JSON escaping, required extension aggregation, and failure propagation without creating a GPU context.

Runtime CTest covers the real hidden WGL context, D3D12 shared resource import, semaphore import, bidirectional fence synchronization, and pixel readback on the target machine.

Regression verification runs the default native tests, the existing D3D12 resource probe, the optional Streamline probe, Java tests, loader metadata checks, JNI loading, and the Gradle build.

## Acceptance Criteria

- The default native build creates the new executable without requiring Streamline.
- The OpenGL context reports all four required extensions.
- All required OpenGL entry points load.
- The D3D12 shared texture and shared fence are created and exported.
- OpenGL imports both objects.
- D3D12 observes the OpenGL fence signal at value 1.
- D3D12 readback exactly matches the OpenGL pattern.
- OpenGL completes a wait for D3D12 fence value 2.
- A schema-version-1 report records every stage as true.
- Existing Milestones 0-4B remain green.

## Non-Claim

Passing this milestone proves the local driver can share one color texture and synchronize between a probe-owned OpenGL context and D3D12. It does not yet prove that Minecraft's live OpenGL context can import the resource, that Iris can render directly into it, or that depth and motion vectors are available for production-quality DLSS.
