# Milestone 4D-A Minecraft OpenGL Context Diagnostic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-shot Fabric render-thread diagnostic that proves Minecraft's OpenGL interop extensions and adapter LUID match the native D3D12 adapter.

**Architecture:** Core defines dependency-free adapter identity and live-context result contracts. JNI returns the D3D12 adapter LUID, while Fabric uses LWJGL only on the render thread to read OpenGL capabilities and LUID; the first result is cached and appended to the current HUD.

**Tech Stack:** Java 21, Fabric Loader 0.19.3, Fabric API 0.116.13+1.21.1, Minecraft 1.21.1, LWJGL 3.3.3, JNI, C++17, DXGI 1.6, D3D12, CMake, PowerShell.

## Global Constraints

- Fabric client only for live-context runtime verification.
- Probe exactly once per process on Minecraft's render thread.
- No shared texture or semaphore import, world-frame mixin, DLSS evaluation, render scaling, or GUI reordering.
- LUID format is exactly 16 lowercase hexadecimal characters for eight raw bytes.
- Existing NeoForge behavior and the blocked DLSS backend diagnostic remain unchanged.
- The project is not a Git repository, so commit steps are skipped.

---

### Task 1: Platform-neutral adapter identity contract

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeAdapterIdentity.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeAdapterIdentitySelfTest.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: `NativeAdapterIdentity.available(String)` and `unavailable(String)` plus `NativeBridge.probeD3D12Adapter()`.

- [ ] **Step 1: Write a failing pure Java test**

Assert that only `[0-9a-f]{16}` is accepted as available, normalization lowercases valid input, unavailable values retain a message, and the default `NativeBridge` method returns unavailable.

- [ ] **Step 2: Run `verify-java.ps1` and verify RED**

Expected: `javac` fails because `NativeAdapterIdentity` and the new bridge method do not exist.

- [ ] **Step 3: Implement the minimal record and default bridge method**

The record fields are `boolean available`, `String luid`, and `String message`. Invalid available LUID values throw `IllegalArgumentException`; null strings normalize to empty.

- [ ] **Step 4: Register the self-test and verify GREEN**

Run `verify-java.ps1`; expect the new test and all existing tests to pass.

### Task 2: Native D3D12 adapter LUID JNI boundary

**Files:**
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Modify: `mc-dlss-native/include/mc_dlss_native.h`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`
- Modify: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeProbeCliSelfTest.java`

**Interfaces:**
- Produces: `NativeLibraryBridge.probeD3D12Adapter()` and JNI `nativeGetD3D12AdapterLuid()`.

- [ ] **Step 1: Extend the Java test before JNI implementation**

Load the built DLL in the existing native probe flow and require either a canonical available LUID on Windows or a stable unavailable result. Verify RED through a missing JNI symbol after rebuilding Java against the old DLL.

- [ ] **Step 2: Implement D3D12 adapter selection and formatting**

On Windows, enumerate high-performance DXGI adapters, skip software adapters, require D3D feature level 12.0, and format the selected `AdapterLuid` raw bytes. Return an empty string when no adapter is available. On other platforms return empty.

- [ ] **Step 3: Export JNI and link Windows graphics libraries**

Add `Java_dev_mcdlss_core_NativeLibraryBridge_nativeGetD3D12AdapterLuid` and link `mc_dlss_native` to `d3d12` and `dxgi` only under `WIN32`.

- [ ] **Step 4: Rebuild native and verify JNI**

Run `configure-native.ps1`, `probe-native.ps1`, and the Java native identity check. Expect a canonical RTX adapter LUID and all native CTests to pass.

### Task 3: Pure live-context snapshot and overlay model

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftGlInteropSnapshot.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftGlInteropOverlayLines.java`
- Create: `mc-dlss-fabric/src/test/java/dev/mcdlss/fabric/MinecraftGlInteropSnapshotTest.java`

**Interfaces:**
- Produces: immutable snapshot validation, `interopPrerequisitesReady()`, and compact overlay lines.

- [ ] **Step 1: Write failing Gradle tests**

Test full readiness, each missing extension, malformed or mismatched LUID values, failure messages, and overlay lines containing `interop-ready`, renderer, and LUID.

- [ ] **Step 2: Run Fabric tests and verify RED**

Run `:mc-dlss-fabric:test`; expect compilation failure because the result classes do not exist.

- [ ] **Step 3: Implement the minimal model and formatter**

Readiness requires all four extension booleans and exact canonical LUID equality. Overlay output is bounded and does not replace existing DLSS status lines.

- [ ] **Step 4: Run Fabric tests and verify GREEN**

Expect all snapshot and overlay tests to pass.

### Task 4: LWJGL render-thread probe and one-shot HUD integration

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftGlInteropProbe.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`

**Interfaces:**
- Consumes: `NativeBridge.probeD3D12Adapter()`, active LWJGL capabilities, and `RenderSystem.isOnRenderThread()`.
- Produces: one cached `MinecraftGlInteropSnapshot` per client process.

- [ ] **Step 1: Add a compile-time call from the HUD before the probe exists**

Reference `MinecraftGlInteropProbe.probe(entrypoint.nativeBridge())` from a one-shot helper and run Fabric compilation. Expect RED because the class and bridge accessor do not exist.

- [ ] **Step 2: Implement the render-thread probe**

Read `GL_VENDOR`, `GL_RENDERER`, `GL_VERSION`, four `GLCapabilities` booleans, and `GL_DEVICE_LUID_EXT` through `EXTMemoryObject.glGetUnsignedBytevEXT`. Check `GL11.glGetError` and compare against the native LUID.

- [ ] **Step 3: Cache and render the result**

Use a nullable field owned by `McDlssFabricClientMod`; initialize it once in the HUD callback, catch runtime failures into a failure snapshot, and append `MinecraftGlInteropOverlayLines` after existing lines.

- [ ] **Step 4: Compile and run regressions**

Run Fabric tests, Java smoke tests, metadata verification, native CTest, JNI probe, 4C interop probe, and the full Gradle build.

### Task 5: Live Minecraft verification

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`

**Interfaces:**
- Produces: fresh client log and screenshot evidence for the live context.

- [ ] **Step 1: Launch the Fabric dev client**

Run `:mc-dlss-fabric:runClient` with `build/native/Release` on `java.library.path`. Wait for the title screen or an existing test world while keeping the render thread active.

- [ ] **Step 2: Capture log and screenshot evidence**

Require all four extensions true, canonical OpenGL and D3D12 LUID values, `adapterLuidMatched=true`, `interopPrerequisitesReady=true`, and the existing `dlss-ready=false`. Save a screenshot under `outputs/`.

- [ ] **Step 3: Record only observed status**

Document the exact renderer/version/LUID, evidence paths, test counts, and the 4D-B next step. Do not claim any shared texture has entered Minecraft.

## Plan Self-Review

- Spec coverage: render-thread execution, four extensions, GL/D3D12 LUID comparison, one-shot caching, HUD output, failure isolation, tests, logs, and screenshot are covered.
- Placeholder scan: no deferred or unspecified implementation instruction remains.
- Type consistency: `NativeAdapterIdentity`, `MinecraftGlInteropSnapshot`, `MinecraftGlInteropProbe`, and `MinecraftGlInteropOverlayLines` use consistent names across tasks.
- Scope: resource import and frame hooks remain outside 4D-A.
