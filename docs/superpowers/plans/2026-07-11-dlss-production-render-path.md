# DLSS Production Render Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove prototype validation stalls and color mismatch, then make Iris world targets render at the selected DLSS internal resolution while preserving native-resolution GUI coordinates.

**Architecture:** A core render policy chooses execution mode, color mode, and internal dimensions from user configuration. NeoForge applies that policy to the live session and version-locked Iris/Sodium Mixins; native Streamline receives explicit color options and avoids readback in production mode.

**Tech Stack:** Java 21, NeoForge 21.1.235, Iris 1.8.14-beta.1, Sodium 0.8.12-beta.2, Mixin 0.8.7, OpenGL EXT semaphore/memory objects, D3D12, NVIDIA Streamline 2.12.

## Global Constraints

- Normal gameplay uses FAST mode and performs no diagnostic readback.
- Iris post-tonemap input is LDR with automatic exposure disabled.
- Window, GUI, mouse, HUD, block outline, and breaking overlay remain native-resolution.
- Internal resolution activates only with DLSS enabled and an Iris shader pack active.
- Any hook failure falls back to normal Iris rendering without crashing.

---

### Task 1: Production Execution And Color Policy

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/DlssRenderPolicy.java`
- Test: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/DlssRenderPolicySelfTest.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/DlssUserConfig.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/IrisDlssSessionController.java`
- Modify: `mc-dlss-native/include/live_streamline_runtime.h`
- Modify: `mc-dlss-native/src/live_streamline_runtime.cpp`

- [ ] Write failing tests requiring FAST/LDR/no-auto-exposure by default and VALIDATING only when diagnostics are enabled.
- [ ] Run core smoke and verify the missing policy fails compilation.
- [ ] Implement policy and persist `diagnosticValidation=false` compatibly with existing config files.
- [ ] Pass explicit HDR/auto-exposure booleans through JNI to native Streamline options.
- [ ] Run Java and native tests; require production options to be LDR and auto-exposure off.

### Task 2: Remove Per-Frame Blocking

**Files:**
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/IrisDlssGlInputSlot.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/IrisDlssSessionController.java`
- Modify: `mc-dlss-native/src/live_dlss_session.cpp`
- Test: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeLiveDlssBridgeSelfTest.java`

- [ ] Add a failing contract test proving FAST completion does not request readback.
- [ ] Remove `glFinish()` after `glWaitSemaphoreEXT`; use `glFlush()` only where command visibility requires it.
- [ ] Skip `inspectLiveDlssEvaluation` readback work in FAST mode and mark the completed slot from fence completion.
- [ ] Keep full inspection only for diagnostic validation mode.
- [ ] Verify continuous frame progression and no validation readback in the formal launcher overlay.

### Task 3: Iris Internal Resolution Hooks

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/DlssInternalResolution.java`
- Test: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/DlssInternalResolutionSelfTest.java`
- Create: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/DlssInternalResolutionState.java`
- Create: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/mixin/IrisRenderingPipelineResolutionMixin.java`
- Create: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/mixin/LevelRendererViewportMixin.java`
- Modify: `mc-dlss-neoforge/src/main/resources/mc_dlss.neoforge.mixins.json`

- [ ] Write failing dimension tests for all four quality modes, disabled state, and absent shader pack.
- [ ] Implement a thread-safe state containing output dimensions, internal dimensions, active generation, and fallback reason.
- [ ] Redirect the width/height arguments used by `IrisRenderingPipeline` to construct `RenderTargets`, only when the state is active.
- [ ] Apply the internal viewport only during world rendering and restore the previous viewport before GUI rendering.
- [ ] Force one Iris pipeline reload when enabled, disabled, output size, or quality mode changes.
- [ ] Compile against exact Iris/Sodium jars and verify target snapshots report internal dimensions.

### Task 4: Settings, Packaging, And Formal Acceptance

**Files:**
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssSodiumConfigEntryPoint.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssNeoForgeClientOverlay.java`
- Modify: `mc-dlss-neoforge/build.gradle`

- [ ] Add Chinese `诊断验证模式` toggle with a performance warning and default off.
- [ ] Show Chinese output/internal resolution, FAST/VALIDATING, LDR/HDR, frames, and resets.
- [ ] Run core smoke, NeoForge compile, native tests, distribution build, and empty-library-path probe.
- [ ] Install the JAR into the PCL formal instance and test the same scene with Iris-only and each DLSS quality mode.
- [ ] Verify color alignment, higher frame rate/GPU occupancy, no per-frame retry/readback, and correct crosshair/block-outline/breaking-overlay alignment.
