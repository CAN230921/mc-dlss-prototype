# Milestone 4D-C World Color Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture one real pre-HUD Minecraft world frame into the proven 64 x 64 OpenGL-D3D12 shared texture and verify identical OpenGL/D3D12 fingerprints.

**Architecture:** Reuse the 4D-B session and fence exchange. Add row-pitch-aware native FNV inspection, invoke a one-shot Fabric probe from `WorldRenderEvents.END`, blit the bound world framebuffer into an imported texture, and cache a strict result for HUD/log evidence.

**Tech Stack:** Java 21, Fabric 1.21.1 rendering events, LWJGL OpenGL 3.2/EXT interop, JNI, C++17, D3D12, CMake/CTest.

## Global Constraints

- Capture exactly one 64 x 64 RGBA8 world frame per process.
- Run only after 4D-B succeeds and only from `WorldRenderEvents.END` while a world is loaded.
- Preserve framebuffer/texture bindings and delete OpenGL objects before closing native handles.
- Keep fence values 1 and 2 and the 5,000 ms native wait.
- Leave `dlss-ready=false`; do not add full-resolution, persistent, depth, motion-vector, Streamline, or HUD capture work.
- This workspace is not a Git repository, so verification replaces commit steps.

---

### Task 1: Shared Fingerprint Contracts

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeReadbackFingerprint.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/RgbaFrameFingerprint.java`
- Test: core and Fabric smoke tests.

**Interfaces:**
- Produces: `inspectD3D12InteropReadback(long)` and FNV-1a/non-uniform/non-black calculation over tightly packed RGBA8 bytes.

- [ ] Add failing tests for known FNV output, uniform/black rejection, native unavailable validation, and default bridge behavior.
- [ ] Run `scripts/verify-java.ps1` and verify missing types/methods fail compilation.
- [ ] Implement the records, Java fingerprint calculator, bridge default, JNI `long[3]` wrapper, and smoke-test registration.
- [ ] Run Java verification and require all tests to pass.

### Task 2: Native Row-Pitched Fingerprint

**Files:**
- Modify: `mc-dlss-native/include/interop_test_pattern.h`
- Modify: `mc-dlss-native/src/interop_test_pattern.cpp`
- Modify: `mc-dlss-native/tests/interop_test_pattern_test.cpp`
- Modify: live session, registry, JNI header/source, and CMake files.

**Interfaces:**
- Produces: `InteropReadbackFingerprint`, row-pitch-aware fingerprint calculation, session inspection, registry inspection, and JNI `jlongArray [hash, nonUniform, nonBlackPixelCount]`.

- [ ] Add a failing native test for padded-row FNV, uniform detection, and one-pixel hash sensitivity.
- [ ] Build the test and verify the new API is missing.
- [ ] Implement fingerprint calculation and reuse the session's bounded fence wait/map path without changing deterministic 4D-B verification.
- [ ] Add registry and JNI inspection with unavailable results for invalid IDs/errors.
- [ ] Rebuild and require all native CTests to pass.

### Task 3: Fabric One-Shot World Capture

**Files:**
- Create: `MinecraftWorldColorCaptureSnapshot.java`
- Create: `MinecraftWorldColorCaptureOverlayLines.java`
- Create: `MinecraftWorldColorCaptureProbe.java`
- Modify: `McDlssFabricEntrypoint.java`
- Modify: `McDlssFabricClientMod.java`
- Create: `MinecraftWorldColorCaptureSnapshotSelfTest.java`
- Modify: `scripts/verify-java.ps1`.

**Interfaces:**
- Consumes: 4D-B success, current `WorldRenderEvents.END` framebuffer, 64 x 64 native session, and Task 1 fingerprint APIs.
- Produces: one cached pre-HUD capture result and `[mc_dlss/fabric/world-color]` log line.

- [ ] Add failing snapshot/overlay tests requiring framebuffer completeness, blit, non-uniform content, D3D submit, GL wait, exact hash match, and release.
- [ ] Run Java verification and confirm missing types fail.
- [ ] Implement the model/overlay and make pure tests pass.
- [ ] Register `WorldRenderEvents.END`; only run once after 4D-B success and with a loaded world.
- [ ] Import the second session, attach texture to a temporary framebuffer, save bindings, blit the bound full-size world target to 64 x 64, read/fingerprint the destination, perform fence exchange, inspect D3D readback, compare, restore, and release in `finally`.
- [ ] Compile Fabric to verify exact LWJGL/Fabric calls; fix only signature/state issues exposed by compilation.

### Task 4: Regression And Live Acceptance

**Files:**
- Modify: local status, feasibility criteria, and Windows toolchain docs.
- Create: `outputs/fabric-world-color-capture-overlay-2026-07-11.png` and runtime log evidence.

- [ ] Run Java smoke tests, loader metadata, complete Gradle build, all native CTests, and standalone 4C regression.
- [ ] Launch Fabric, enter the existing world, and require all `[mc_dlss/fabric/world-color]` stages plus `success=true`.
- [ ] Capture F2 evidence and confirm continued world rendering after cleanup.
- [ ] Update docs with the proof and the remaining full-resolution/persistent/depth/motion/DLSS boundaries.

## Self-Review

- Coverage: event timing, pre-HUD scope, 64 x 64 blit, dual fingerprint, fence exchange, cleanup, evidence, regressions, and non-claims map to Tasks 1-4.
- Placeholder scan: no deferred implementation wording remains.
- Type consistency: `inspectD3D12InteropReadback(long)` returns hash/non-uniform/non-black data through core, JNI, registry, and Fabric with identical field meanings.
