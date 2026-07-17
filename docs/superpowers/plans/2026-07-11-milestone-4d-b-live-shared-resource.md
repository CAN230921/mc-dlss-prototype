# Milestone 4D-B Live Shared Resource Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove one 64 x 64 D3D12 texture and fence can be imported, written, synchronized, read back, verified, and released inside Minecraft Fabric's live OpenGL context.

**Architecture:** Java owns probe orchestration and OpenGL objects while native code owns the D3D12 device, shared resources, Win32 handles, and readback. A mutex-protected registry exposes positive session IDs through JNI; the probe is attempted once after the existing 4D-A capability check succeeds.

**Tech Stack:** Java 21, Fabric 1.21.1, LWJGL OpenGL EXT memory/semaphore APIs, JNI, C++20, D3D12, DXGI, CMake/CTest.

## Global Constraints

- Windows x64 and Fabric client only.
- Use exactly one `DXGI_FORMAT_R8G8B8A8_UNORM` texture at 64 x 64.
- Fence value 1 transfers OpenGL writes to D3D12; value 2 transfers D3D12 completion to OpenGL.
- Keep imported Win32 handles owned and closed by native code.
- Bound native waits to 5,000 milliseconds.
- Attempt once per Minecraft process and never attach the probe texture to Minecraft's framebuffer.
- Leave the DLSS backend diagnostic blocked.
- This workspace has no Git repository, so verification replaces commit steps.

---

### Task 1: Core Session Contract

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeInteropSessionInfo.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Test: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeInteropSessionInfoSelfTest.java`

**Interfaces:**
- Produces: `NativeInteropSessionInfo`, `openD3D12InteropSession(int,int)`, `submitD3D12InteropReadback(long)`, `verifyD3D12InteropReadback(long)`, and `closeD3D12InteropSession(long)`.

- [ ] Write a failing self-test requiring unavailable results for zero IDs/handles, exact 64 x 64 validation, and unsupported `NativeBridge` defaults.
- [ ] Run `./gradlew.bat :mc-dlss-core:testSmoke` and verify compilation fails because the contract does not exist.
- [ ] Add the dependency-free record and default bridge methods. Add JNI declarations using a three-element `long[]` result `[sessionId, textureHandle, fenceHandle]`; convert load, symbol, and runtime failures to unavailable/false results.
- [ ] Run `./gradlew.bat :mc-dlss-core:testSmoke` and verify all core self-tests pass.

### Task 2: Shared Native Pattern Logic

**Files:**
- Create: `mc-dlss-native/include/interop_test_pattern.h`
- Create: `mc-dlss-native/src/interop_test_pattern.cpp`
- Create: `mc-dlss-native/tests/interop_test_pattern_test.cpp`
- Modify: `mc-dlss-native/src/gl_d3d12_interop_probe.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `makeInteropTestPattern(uint32_t,uint32_t)` and `interopReadbackMatches(const uint8_t*,size_t,const std::vector<uint8_t>&,uint32_t,uint32_t)`.

- [ ] Add a failing native test for deterministic corner pixels, padded row pitch, and one-byte mismatch rejection.
- [ ] Configure/build CTest and verify the new test fails to link before implementation.
- [ ] Extract the existing 4C RGBA formula into the shared helper and make the 4C probe consume it.
- [ ] Run the new test and the existing 4C report/runtime tests; verify they pass without changing the 4C pattern.

### Task 3: Native Live Session And Registry

**Files:**
- Create: `mc-dlss-native/include/live_d3d12_interop_session.h`
- Create: `mc-dlss-native/src/live_d3d12_interop_session.cpp`
- Create: `mc-dlss-native/include/live_d3d12_interop_registry.h`
- Create: `mc-dlss-native/src/live_d3d12_interop_registry.cpp`
- Create: `mc-dlss-native/tests/live_d3d12_interop_session_test.cpp`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Consumes: the shared pattern helper from Task 2.
- Produces: open/submit/verify/close registry operations and JNI exports matching Task 1.

- [ ] Add a failing CTest that opens a 64 x 64 session, rejects unsupported dimensions and unknown IDs, checks positive borrowed handles, and closes twice safely.
- [ ] Build the target and verify it fails before the session and registry exist.
- [ ] Implement hardware-adapter D3D12 creation, shareable texture/fence, retained NT handles, readback buffer, command objects, and completion event.
- [ ] Implement `submit` as queue wait(1), COMMON-to-COPY_SOURCE transition, copy, COPY_SOURCE-to-COMMON transition, execute, and queue signal(2), with no CPU wait.
- [ ] Implement `verify` with a 5,000 ms event wait, row-pitch-aware full-pixel comparison, and safe map/unmap.
- [ ] Implement a mutex-protected monotonic positive-ID registry; make close remove the entry before destruction and tolerate repeated close calls.
- [ ] Add JNI conversion without exposing pointers and return unavailable/false on invalid sessions or native failures.
- [ ] Run all native CTests and verify session creation/cleanup plus all previous tests pass.

### Task 4: Fabric Result Model And Overlay

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftLiveInteropSnapshot.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftLiveInteropOverlayLines.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/MinecraftLiveInteropSnapshotSelfTest.java`

**Interfaces:**
- Produces: immutable stage result, strict `success()` gate, and compact HUD lines for all nine stages.

- [ ] Write a failing self-test proving success requires every stage and overlay output includes `live-share`, synchronization, verification, and release state.
- [ ] Run `./gradlew.bat :mc-dlss-fabric:testSmoke` and verify compilation fails because the types do not exist.
- [ ] Implement the record and formatter with stable false-state output and no dependency on Minecraft classes.
- [ ] Run Fabric smoke tests and verify they pass.

### Task 5: Minecraft Live OpenGL Probe

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftLiveInteropProbe.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricEntrypoint.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`

**Interfaces:**
- Consumes: Task 1 native methods and Task 4 result model.
- Produces: one render-thread probe attempt cached beside the 4D-A snapshot.

- [ ] Add entrypoint delegation and a compile reference to the new probe, then run Fabric compilation to expose exact LWJGL API signatures.
- [ ] Import the borrowed resource handle with `GL_HANDLE_TYPE_D3D12_RESOURCE_EXT`, create one-level external `GL_RGBA8` storage, and upload the same deterministic 64 x 64 RGBA bytes as native code.
- [ ] Import the borrowed fence with `GL_HANDLE_TYPE_D3D12_FENCE_EXT`, signal value 1 with texture layout `GL_LAYOUT_GENERAL_EXT`, flush, and call native submit.
- [ ] Set fence value 2, queue the OpenGL semaphore wait with the texture/layout pair, finish, and call native verify.
- [ ] Check `glGetError()` after import, storage, signal, and wait stages. In `finally`, delete semaphore, texture, and memory object before closing the native session; record release success even after an earlier failure.
- [ ] Cache the attempt only after 4D-A reports ready, append overlay lines, log one `[mc_dlss/fabric/live-share]` summary, and catch runtime failures so HUD rendering continues.
- [ ] Run the complete Gradle build outside the restricted sandbox if Loom's temporary mappings file is denied; verify all Java self-tests and compilation pass.

### Task 6: Regression And Live Acceptance

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`
- Modify: `docs/windows-toolchain.md`
- Create: `outputs/gl-d3d12-live-interop-probe-2026-07-11.json` only if the runtime helper emits a report.

**Interfaces:**
- Consumes: complete 4D-B probe.
- Produces: reproducible evidence and an explicit next-step boundary.

- [ ] Run core/Fabric self-tests, metadata checks, JNI symbol checks, all native CTests, the standalone D3D12 resource probe, and the standalone 4C interop probe.
- [ ] Confirm no Minecraft Java process is holding the native DLL, rebuild/copy the native library, then start `runClient` for user-driven world entry.
- [ ] Ask the user to enter the existing world and press F2 after the HUD shows 4D-B; do not automate their UI.
- [ ] Verify the live log reports every 4D-B stage true, copy the F2 screenshot into `outputs`, and confirm Minecraft continued rendering after cleanup.
- [ ] Update status/criteria/toolchain docs with the exact evidence and state that framebuffer integration, persistent per-frame synchronization, and DLSS inputs remain unproven.

## Self-Review

- Spec coverage: core contract, native ownership/registry, both fence directions, bounded verification, cleanup ordering, one-shot Fabric lifecycle, HUD/log evidence, regressions, and non-claim all map to Tasks 1-6.
- Placeholder scan: no TBD/TODO/follow-up-only implementation steps remain.
- Type consistency: Java bridge method names and JNI stages are identical in Tasks 1, 3, and 5; dimensions and fence values remain 64 x 64, 1, and 2 throughout.
