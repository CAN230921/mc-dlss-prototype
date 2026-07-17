# Milestone 4C OpenGL-D3D12 Interop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and run a native probe proving zero-copy color-texture sharing and bidirectional GPU fence synchronization between OpenGL and D3D12.

**Architecture:** A platform-neutral report model is tested without a GPU. A Windows-only runtime owns a hidden WGL context, D3D12 shared texture and fence, OpenGL imports, D3D12 readback, and deterministic cleanup; a small command-line wrapper writes machine-readable evidence.

**Tech Stack:** C++17, CMake 3.24+, Visual Studio 2022 Build Tools, Win32/WGL, OpenGL EXT external-memory/semaphore APIs, D3D12, DXGI, WRL `ComPtr`, PowerShell.

## Global Constraints

- Windows 10/11 x64 only.
- Probe texture is exactly 64 x 64 and `DXGI_FORMAT_R8G8B8A8_UNORM`.
- Required extensions are exactly `GL_EXT_memory_object`, `GL_EXT_memory_object_win32`, `GL_EXT_semaphore`, and `GL_EXT_semaphore_win32`.
- Fence value 1 transfers OpenGL writes to D3D12; fence value 2 transfers D3D12 completion back to OpenGL.
- No Minecraft, Iris, Sodium, Streamline, depth, motion-vector, or frame-loop integration.
- Existing native targets and the SDK-independent default build remain unchanged in behavior.
- The project is not a Git repository, so commit steps are skipped.

---

### Task 1: Interop report contract

**Files:**
- Create: `mc-dlss-native/include/gl_d3d12_interop_report.h`
- Create: `mc-dlss-native/src/gl_d3d12_interop_report.cpp`
- Create: `mc-dlss-native/tests/gl_d3d12_interop_report_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `GlD3D12InteropSnapshot`, `validateGlD3D12InteropSnapshot(const GlD3D12InteropSnapshot&)`, and `toJson(const GlD3D12InteropSnapshot&)` in namespace `mc_dlss`.
- Consumes: C++17 standard-library types only.

- [ ] **Step 1: Write the failing test**

Construct one complete snapshot and one incomplete snapshot. Assert that only the complete snapshot validates, all four extension names appear in JSON, success requires `readbackMatched` and `openGlWaitCompleted`, and quotes/backslashes/newlines are escaped.

- [ ] **Step 2: Run the target and verify RED**

Run `./scripts/configure-native.ps1` and confirm compilation fails because the report header and functions do not exist.

- [ ] **Step 3: Implement the minimal report model**

Define string identity fields, four extension booleans, and booleans for entry-point loading, shared object creation, imports, both synchronization directions, and readback. Validation returns true only when every required stage is true and the adapter/vendor/renderer/version strings are non-empty.

- [ ] **Step 4: Register and run the pure test**

Add the report source to `mc_dlss_probe_support`, create `mc_dlss_gl_d3d12_interop_report_test`, register `gl_d3d12_interop_report`, then run CTest and expect the pure test to pass.

### Task 2: Hidden WGL context and extension loader

**Files:**
- Create: `mc-dlss-native/include/gl_d3d12_interop_probe.h`
- Create: `mc-dlss-native/src/gl_d3d12_interop_probe.cpp`

**Interfaces:**
- Produces: `GlD3D12InteropSnapshot runGlD3D12InteropProbe(std::uint32_t width, std::uint32_t height)`.
- Internal owners: `HiddenWglContext`, `ScopedHandle`, and `GlInteropFunctions`.

- [ ] **Step 1: Add the runtime CTest before its target exists**

Register `gl_d3d12_interop_probe` with fixed 64 x 64 dimensions and a build-directory report path. Configure and confirm RED because the executable is absent.

- [ ] **Step 2: Implement WGL ownership**

Register a private window class, create a hidden window, acquire its device context, select and set an RGBA double-buffered OpenGL pixel format, create a WGL context, and make it current. Destruction reverses those operations.

- [ ] **Step 3: Implement extension discovery and entry-point loading**

Read `GL_VENDOR`, `GL_RENDERER`, `GL_VERSION`, and the compatibility extension string. Require all four exact extension tokens. Load memory-object, semaphore, import, texture-storage, semaphore-parameter, signal, and wait entry points with `wglGetProcAddress`; reject null and sentinel pointer values.

### Task 3: Shared texture, fence, and readback

**Files:**
- Modify: `mc-dlss-native/src/gl_d3d12_interop_probe.cpp`

**Interfaces:**
- Consumes: active WGL context and loaded EXT functions from Task 2.
- Produces: a fully populated `GlD3D12InteropSnapshot`.

- [ ] **Step 1: Create D3D12 objects on the hardware adapter**

Select a non-software high-performance adapter, create a D3D12 device, direct queue, allocator, list, a 64 x 64 shareable RGBA8 committed texture in common state, a shareable fence, and a readback buffer sized with `GetCopyableFootprints`.

- [ ] **Step 2: Export and import shared objects**

Create NT handles for the texture and fence, duplicate the handles for OpenGL import, import the texture into an OpenGL memory object with `GL_HANDLE_TYPE_D3D12_RESOURCE_EXT`, allocate `GL_RGBA8` external texture storage, and import the fence into an OpenGL semaphore with `GL_HANDLE_TYPE_D3D12_FENCE_EXT`.

- [ ] **Step 3: Submit the OpenGL write and fence value 1**

Generate a deterministic per-pixel RGBA pattern, upload it with `glTexSubImage2D`, set `GL_D3D12_FENCE_VALUE_EXT` to 1, and signal the imported semaphore with the texture in `GL_LAYOUT_GENERAL_EXT`.

- [ ] **Step 4: Wait, copy, and signal from D3D12**

Queue a wait for value 1, transition the texture from common to copy source, copy it into the placed readback footprint, close and execute the list, then queue a signal for value 2. Wait at most 10 seconds before mapping the readback buffer.

- [ ] **Step 5: Verify pixels and wait in OpenGL**

Compare every logical pixel while respecting the readback row pitch. Set the imported semaphore fence value to 2, call the OpenGL wait with the texture layout, and use `glFinish` to prove completion.

### Task 4: Executable, script, and runtime evidence

**Files:**
- Create: `mc-dlss-native/src/gl_d3d12_interop_probe_main.cpp`
- Create: `scripts/probe-gl-d3d12-interop.ps1`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `mc_dlss_gl_d3d12_interop_probe.exe` and `outputs/gl-d3d12-interop-probe-2026-07-11.json`.

- [ ] **Step 1: Implement the command-line wrapper**

Accept `--width`, `--height`, and `--report`; allow only 64 x 64 for this milestone. Always print one JSON object, write the same object to the requested report where possible, and return non-zero for invalid arguments, runtime failure, or report-write failure.

- [ ] **Step 2: Add the Windows executable target**

Link the probe to `mc_dlss_probe_support`, `d3d12`, `dxgi`, `dxguid`, `opengl32`, `gdi32`, and `user32`; apply the existing warning policy and register the runtime CTest.

- [ ] **Step 3: Add the PowerShell verifier**

Run the Release executable, parse JSON, and throw unless schema version, success, all four extension booleans, every creation/import/synchronization stage, and readback are true.

- [ ] **Step 4: Run the native build and probe**

Run `./scripts/configure-native.ps1` followed by `./scripts/probe-gl-d3d12-interop.ps1`. Expect exit code 0 and a report naming the NVIDIA OpenGL renderer and D3D12 adapter.

### Task 5: Regression verification and handoff

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`
- Modify: `docs/windows-toolchain.md`

**Interfaces:**
- Consumes: the interop report and all existing verification commands.
- Produces: evidence-backed Milestone 4C status and the exact next Minecraft-context diagnostic step.

- [ ] **Step 1: Run all native and Streamline checks**

Run the default native build/CTest, D3D12 resource verifier, Streamline-enabled build/CTest, Streamline DLSS verifier, and inspect both new and existing JSON reports.

- [ ] **Step 2: Run Java and loader regressions**

Run `verify-java.ps1`, `verify-loader-metadata.ps1`, the JNI probe, and the full Gradle build.

- [ ] **Step 3: Record observed results only**

Document extension availability, exact OpenGL/D3D12 identities, each successful stage, report path, test counts, and any remaining limitation. Do not claim Minecraft is using the shared texture.

## Plan Self-Review

- Spec coverage: report, extension gates, WGL ownership, shared texture, shared fence, GL-to-D3D12 synchronization, D3D12-to-GL synchronization, readback, evidence, and regressions are each assigned to a task.
- Placeholder scan: no TBD, TODO, unspecified error handling, or deferred implementation step remains.
- Type consistency: `GlD3D12InteropSnapshot`, `validateGlD3D12InteropSnapshot`, `toJson`, and `runGlD3D12InteropProbe` use the same names throughout.
- Scope: Minecraft integration remains explicitly outside 4C and becomes the next milestone only after this probe passes.
