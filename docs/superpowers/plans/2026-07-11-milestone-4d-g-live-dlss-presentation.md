# Milestone 4D-G Live DLSS Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the Minecraft world at DLSS Quality input resolution, evaluate Streamline DLSS every frame, and present full-resolution output before native-resolution hand/HUD rendering with a reliable vanilla fallback.

**Architecture:** A render-world mixin temporarily swaps Minecraft's active main framebuffer to a persistent low-resolution target. At the existing END callback, a four-resource A/B D3D12 session evaluates DLSS synchronously, returns shared FP16 output to OpenGL, restores the full-resolution main framebuffer, and composites the result. A guarded lifecycle disables the path and restores vanilla rendering after any failure.

**Tech Stack:** Java 21, Fabric Loom/API 1.21.1, Sponge Mixin, JOML, LWJGL OpenGL/NV interop, JNI, C++17, D3D12, NVIDIA Streamline 2.12.0, CMake/CTest.

## Global Constraints

- Quality mode only; output equals the full Minecraft main framebuffer dimensions.
- Input formats are FP16 color, R32F depth, and RG16F camera motion; output is FP16 color.
- Main framebuffer is never resized; world rendering is redirected to a persistent low-resolution framebuffer.
- Hand and HUD render after DLSS presentation at output resolution.
- Startup, resize, world change, retry, or failed frame forces temporal reset.
- Every failure restores the original framebuffer and a valid visible image before disabling DLSS.
- Do not call `slShutdown` in the Minecraft process; call `slFreeResources` and leave process-global Streamline state loaded until process exit.
- Existing 4D-A through 4D-F contracts and evidence remain available.

---

### Task 1: DLSS Resolution And Presentation State Model

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeDlssOptimalSettings.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLiveDlssSessionInfo.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLiveDlssFrameResult.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeLiveDlssContractSelfTest.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssPresentationState.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssResolutionContract.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/LiveDlssResolutionContractSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces immutable validated output/render dimensions, session handles, frame results, and lifecycle transitions.
- Requires aspect agreement within one source pixel of rounded error and render dimensions strictly below output.

- [ ] Write failing tests for valid Quality dimensions, aspect mismatch, invalid handles, stage diagnostics, reset/fallback transitions, and completion gating.
- [ ] Run `scripts/verify-java.ps1` and verify failure names the missing types.
- [ ] Implement only the validated records/state model required by the tests.
- [ ] Run all Java self-tests and require a clean pass.

### Task 2: Render-World Framebuffer Redirection

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/access/MinecraftClientFramebufferAccessor.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/mixin/GameRendererWorldFramebufferMixin.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssFramebufferRedirector.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/LiveDlssFramebufferRedirectorSelfTest.java`
- Create: `mc-dlss-fabric/src/main/resources/mc_dlss.mixins.json`
- Modify: `mc-dlss-fabric/src/main/resources/fabric.mod.json`

**Interfaces:**
- Produces idempotent begin/restore hooks around `GameRenderer.renderWorld` and exposes the active low-resolution framebuffer to the END callback.

- [ ] Write a pure state-machine test proving nested begin is rejected, restore is idempotent, exceptions restore, and resize marks rebuild/reset.
- [ ] Verify RED with Java tests.
- [ ] Implement redirector state, accessor, and HEAD/RETURN mixin hooks.
- [ ] Compile Fabric and require mixin target validation success.

### Task 3: Native Four-Resource Session Contract

**Files:**
- Create: `mc-dlss-native/include/live_dlss_session.h`
- Create: `mc-dlss-native/src/live_dlss_session.cpp`
- Create: `mc-dlss-native/include/live_dlss_registry.h`
- Create: `mc-dlss-native/src/live_dlss_registry.cpp`
- Create: `mc-dlss-native/tests/live_dlss_session_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces a session with two A/B slots, each exposing input color/depth/motion, output color, and fence handles plus render/output dimensions.

- [ ] Write failing native tests for formats, extents, A/B handles, fence validation, output fingerprint, transactional cleanup, and invalid dimensions.
- [ ] Configure/build and verify the focused target fails for missing implementation.
- [ ] Implement shared resources, readbacks, registry, and idempotent close without Streamline evaluation.
- [ ] Run the complete default CTest suite.

### Task 4: Streamline Runtime Boundary And Evaluation

**Files:**
- Create: `mc-dlss-native/include/live_streamline_runtime.h`
- Create: `mc-dlss-native/src/live_streamline_runtime.cpp`
- Create: `mc-dlss-native/tests/live_streamline_runtime_contract_test.cpp`
- Modify: `mc-dlss-native/src/live_dlss_session.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Consumes four-resource slot, serialized temporal constants, viewport 0, and a monotonically increasing frame index.
- Produces optimal settings and structured stage results for init/support/options/constants/tags/evaluate/free.

- [ ] Write a fake-runtime contract test that injects failure at every Streamline stage and verifies exact diagnostics and cleanup.
- [ ] Verify RED.
- [ ] Implement the runtime abstraction and optional real Streamline 2.12 backend.
- [ ] Integrate D3D12 state transitions, frame tokens, constants, tags, `slEvaluateFeature`, output fingerprint, and `slFreeResources` without `slShutdown`.
- [ ] Build default and Streamline-enabled trees; run contract tests in both.

### Task 5: JNI And Java Live-DLSS Bridge

**Files:**
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeLiveDlssBridgeSelfTest.java`
- Modify: `mc-dlss-native/include/mc_dlss_native.h`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`

**Interfaces:**
- Produces query/open/evaluate/inspect/close methods with versioned fixed-length arrays and serialized 4D-F constants.

- [ ] Write failing Java tests for every array layout, float-bit preservation, malformed data, stage mapping, dimensions, and unavailable symbols.
- [ ] Verify RED.
- [ ] Implement Java decode/validation and C-linkage JNI exports.
- [ ] Run Java/native tests and verify unmangled exports with `dumpbin /exports`.

### Task 6: OpenGL FP16 Inputs And Output Composite

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssGlSlot.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssOutputComposite.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/LiveDlssOutputCompositeSelfTest.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftMotionVectorShader.java`

**Interfaces:**
- Imports four native shared resources; fills low-resolution inputs and blits exact-size full-resolution output into the original main framebuffer.

- [ ] Write failing tests for slot validation, output hash gating, exact-size copy, and GL-state restoration contract.
- [ ] Verify RED.
- [ ] Implement FP16 color import/FBO, existing depth/motion passes, output import/FBO, fingerprint reads, and composite.
- [ ] Compile Fabric and run Java self-tests.

### Task 7: Persistent Live-DLSS Presentation Controller

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssPresentationController.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssPresentationSnapshot.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssPresentationOverlayLines.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/LiveDlssPresentationSnapshotSelfTest.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`
- Modify: `mc-dlss-debug/src/main/java/dev/mcdlss/debug/DlssDebugSnapshot.java`

**Interfaces:**
- Coordinates redirector, temporal history, A/B GL/native slots, evaluation, output presentation, 30/120-frame gates, retention, resize, world unload, and fallback.

- [ ] Write failing lifecycle/snapshot tests for 30-frame gate, 120/120, A/B 60/60, hash changes, resize reset, injected failure fallback, retained resources, and `dlss-ready` gating.
- [ ] Verify RED.
- [ ] Implement controller and END callback integration.
- [ ] Add HUD/log fields and development enable/retry/failure-injection properties.
- [ ] Run Java tests and full Gradle build.

### Task 8: Live Acceptance, Fallback Injection, And Documentation

**Files:**
- Create: `outputs/fabric-live-dlss-presentation-2026-07-11.log`
- Create: `outputs/fabric-live-dlss-fallback-2026-07-11.log`
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`
- Modify: `docs/windows-toolchain.md`

**Interfaces:**
- Produces reproducible success and failure-recovery evidence.

- [ ] Build the Streamline-enabled native runtime and copy the required production DLLs beside the Minecraft process.
- [ ] Run unattended quick-play with DLSS enabled; require the 30-frame gate, then 120/120, A/B 60/60, matching output hashes, output changes, 30 retained frames, native-resolution HUD, and release.
- [ ] Run a second acceptance with evaluation failure injected after low-resolution rendering; require a valid linear fallback frame and subsequent vanilla full-resolution rendering.
- [ ] Close Minecraft and run fresh Java tests, complete default/Streamline CTest suites, and full Gradle build.
- [ ] Record exact dimensions, hashes, timing, readiness, fallback stage, retained Streamline global state, and non-claims in all three docs.
