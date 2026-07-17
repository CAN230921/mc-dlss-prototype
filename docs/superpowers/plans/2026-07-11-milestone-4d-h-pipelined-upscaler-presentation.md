# Milestone 4D-H Pipelined Upscaler Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the verified 4D-G DLSS presentation contract while switching after 30 validated frames to a two-slot GPU-ordered fast path with no per-frame diagnostic readback or CPU completion wait.

**Architecture:** A vendor-neutral Java/native execution-mode contract separates scheduling from the existing Streamline adapter. Native evaluation optionally omits output readback, OpenGL queues an external-semaphore wait and exact-size blit without `glFinish`, and a nonblocking A/B scheduler falls back rather than stalling when both slots are busy.

**Tech Stack:** Java 21, Fabric Loom/API 1.21.1, LWJGL OpenGL external memory/semaphores, JNI, C++17, D3D12, NVIDIA Streamline 2.12.0, CMake/CTest.

## Global Constraints

- Streamline DLSS Quality is the only implemented backend in 4D-H.
- Common scheduling types must not prevent future D3D12 XeSS or FSR 3 adapters.
- No XeSS, FSR 3, or frame-generation runtime is loaded or claimed compatible.
- The first 30 accepted frames use complete 4D-G GL/D3D12 validation.
- Fast mode performs no per-frame output readback, `glReadPixels`, or `glFinish`.
- One periodic validation occurs after every 300 accepted fast frames.
- Busy slots cause a visible fallback and temporal reset, never a CPU wait.
- Eight consecutive busy fallbacks disable the upscaler for the process.
- Main framebuffer restoration and native-resolution hand/HUD ordering remain unchanged.
- Minecraft calls `slFreeResources` but never `slShutdown`.
- Existing 4D-A through 4D-G evidence and regressions remain green.

---

### Task 1: Vendor-Neutral Mode And Slot Scheduler Contracts

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeUpscalerBackend.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeUpscalerExecutionMode.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveUpscalerSlotScheduler.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveUpscalerModeTracker.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/LiveUpscalerSchedulingSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces `NativeUpscalerBackend.STREAMLINE_DLSS`, with reserved `INTEL_XESS` and `AMD_FSR3` identifiers that carry no availability claim.
- Produces `NativeUpscalerExecutionMode.VALIDATING` and `FAST` integer codes for JNI.
- Produces `LiveUpscalerSlotScheduler.select(boolean slot0Ready, boolean slot1Ready, int preferred)` returning slot `0`, `1`, or `-1`.
- Produces `LiveUpscalerModeTracker` with 30 validation, 300 fast, periodic-sample, and eight-busy-fallback transitions.

- [ ] Write `LiveUpscalerSchedulingSelfTest` proving preferred/alternate selection, no-ready `-1`, transition at exactly 30 validations, readiness after 300 fast frames plus a periodic sample, and failure after eight consecutive busy frames.
- [ ] Run `scripts/verify-java.ps1` and require RED for the missing scheduling types.
- [ ] Implement the enums, scheduler, and tracker with immutable snapshot getters and reset-on-resize behavior.
- [ ] Run all Java self-tests and require a clean pass.

### Task 2: Native Execution Mode And Nonblocking Slot Readiness

**Files:**
- Modify: `mc-dlss-native/include/live_dlss_session.h`
- Modify: `mc-dlss-native/src/live_dlss_session.cpp`
- Modify: `mc-dlss-native/include/live_dlss_registry.h`
- Modify: `mc-dlss-native/src/live_dlss_registry.cpp`
- Modify: `mc-dlss-native/tests/live_dlss_session_test.cpp`

**Interfaces:**
- Adds `enum class LiveUpscalerExecutionMode : uint32_t { validating = 0, fast = 1 }`.
- Adds `bool LiveDlssSession::slotReady(size_t slotIndex) const noexcept` based only on completed versus last-submitted fence values.
- Extends `submitEvaluation` with `LiveUpscalerExecutionMode mode`.
- Adds inspection metadata `diagnosticReadbackRequested`.

- [ ] Extend the native session test to require both fresh slots ready, an in-flight submitted slot not ready, completion restoring readiness, validation inspection carrying a diagnostic sample, and fast inspection reporting no diagnostic sample.
- [ ] Build the focused test and require RED for missing APIs.
- [ ] Implement the execution enum, readiness query, registry forwarding, and inspection metadata without changing D3D command recording yet.
- [ ] Run the focused native test and require green.

### Task 3: D3D12 Fast Evaluation Path

**Files:**
- Modify: `mc-dlss-native/src/live_dlss_session.cpp`
- Modify: `mc-dlss-native/tests/live_streamline_evaluation_real_test.cpp`

**Interfaces:**
- Validation mode transitions output to copy source, copies it to readback, and returns it to common state.
- Fast mode transitions output directly from UAV to common and signals the shared fence without readback copy or CPU event use.

- [ ] Extend the real Streamline test with alternating validation and fast submissions; require validation fingerprint availability, fast completed fence, and no fast diagnostic sample.
- [ ] Run the real test against the pre-change implementation and require RED on fast metadata/behavior.
- [ ] Branch command recording by mode while keeping all four shared resources in `COMMON` before the even fence signal.
- [ ] Build and run contract plus real Streamline tests and require green.

### Task 4: JNI And Java Fast-Mode Bridge

**Files:**
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLiveDlssFrameResult.java`
- Modify: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeLiveDlssBridgeSelfTest.java`
- Modify: `mc-dlss-native/include/mc_dlss_native.h`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricEntrypoint.java`

**Interfaces:**
- Extends submission with `NativeUpscalerExecutionMode mode`.
- Adds `boolean isLiveUpscalerSlotReady(long sessionId, int slotIndex)`.
- Extends the fixed frame result array with a diagnostic-readback flag while retaining strict length validation.

- [ ] Extend bridge tests for mode integer preservation, invalid mode rejection, slot-readiness forwarding, validation result decoding, and fast result decoding without a hash.
- [ ] Run Java tests and require RED.
- [ ] Implement Java defaults, JNI exports, array layout, and native registry calls.
- [ ] Run Java tests, native build, and `dumpbin /exports` for the new unmangled readiness export.

### Task 5: OpenGL GPU-Ordered Fast Presentation

**Files:**
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssGlSlot.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssOutputComposite.java`
- Modify: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/LiveDlssOutputCompositeSelfTest.java`

**Interfaces:**
- Keeps `waitForOutput(long)` as validating behavior.
- Adds `queueWaitForOutput(long)` that calls `glWaitSemaphoreEXT` but not `glFinish`.
- Fast composite performs no output readback and queues after the semaphore wait in the same OpenGL stream.

- [ ] Add a pure operation-sequence test requiring `WAIT -> RESTORE_MAIN -> BLIT` and forbidding `FINISH`/`READ_PIXELS` in fast mode.
- [ ] Run Java tests and require RED.
- [ ] Implement the fast wait and shared sequence contract while preserving full GL state restoration.
- [ ] Compile Fabric and run Java self-tests.

### Task 6: Controller Mode Transition And Fast Acceptance

**Files:**
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssPresentationController.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssPresentationSnapshot.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/LiveDlssPresentationOverlayLines.java`
- Modify: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/LiveDlssPresentationSnapshotSelfTest.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`

**Interfaces:**
- Controller validates 30 frames, enters fast mode, samples after 300 fast frames, and reaches COMPLETE only after the sample passes.
- Scheduler checks both slots before populating inputs.
- Busy fallback does not advance frame token, fence use, temporal history, or fast counters.

- [ ] Extend snapshot tests for mode labels, 30/300/sample readiness, busy counters, timing separation, resize reset, and eight-busy permanent fallback.
- [ ] Run Java tests and require RED.
- [ ] Integrate mode tracker, scheduler, fast JNI/GL calls, separate timers, logs, and overlay fields.
- [ ] Run Java tests and the complete Gradle build.

### Task 7: Real GPU And Minecraft Acceptance

**Files:**
- Create: `outputs/fabric-live-upscaler-fast-2026-07-11.log`
- Create: `outputs/fabric-live-upscaler-busy-fallback-2026-07-11.log`
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`
- Modify: `docs/windows-toolchain.md`

**Interfaces:**
- Produces reproducible performance and failure evidence without claiming XeSS/FSR 3 support.

- [ ] Build Streamline native and run default 13-test plus Streamline 15-test suites, excluding only `streamline_dlss_probe` because of its known `slShutdown` stall.
- [ ] Run unattended Minecraft through 30 validation frames, 300 fast frames, one periodic validation, balanced A/B usage, and `ready=true`.
- [ ] Require sampled GL/D3D12 hashes to match and fast average CPU time to be below validation average.
- [ ] Run busy-slot injection and evaluation-failure injection; require visible fallback, temporal reset, framebuffer restoration, and appropriate recovery/permanent-fallback behavior.
- [ ] Run fresh Java self-tests and complete Gradle build.
- [ ] Record exact dimensions, counters, hashes, validation/fast timings, fallback fields, backend-neutral non-claims, and evidence paths in all three docs.
