# Iris/Sodium Render Target Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans task-by-task with TDD.

**Goal:** Prove and integrate Iris 1.8.14/Sodium 0.8.12 render target ownership before packaging the mod.

**Architecture:** Add exact Fabric runtime dependencies, a guarded Iris mixin accessor, a loader-neutral target snapshot, diagnostics, then switch Live DLSS input selection only after the Iris contract is validated.

**Tech Stack:** Java 21, Fabric Loom, Iris 1.8.14 beta.1, Sodium 0.8.12 beta.2, Mixin, OpenGL.

### Task 1: Exact Iris/Sodium Development Runtime
- [ ] Add Modrinth exclusive Maven repository and exact version properties.
- [ ] Add Iris and Sodium as Fabric `modRuntimeOnly` dependencies.
- [ ] Launch and require both exact versions in Fabric Loader output.

### Task 2: Iris Contract And Accessor
- [ ] Add failing tests for absent, wrong-version, vanilla-pipeline, and Iris-pipeline snapshots.
- [ ] Add optional-target mixin accessor for `IrisRenderingPipeline.renderTargets`.
- [ ] Implement adapter without hard failure when Iris is absent.

### Task 3: Render Target Diagnostics
- [ ] Expose current target dimensions, colortex0 main/alt, depth, pre-translucent depth, pre-hand depth, shaderpack state, and generation.
- [ ] Add overlay/log formatting and self-tests.
- [ ] Run Fabric with no shaderpack and capture evidence.

### Task 4: DLSS Input Switch
- [ ] Define target-selection tests for flip state and pipeline changes.
- [ ] Populate shared color/depth from selected Iris textures at the Iris finalization boundary.
- [ ] Reset and reopen on reload/resize/generation changes.

### Task 5: Shaderpack And NeoForge Gate
- [ ] Run one active shaderpack and validate actual Iris targets.
- [ ] Record DLSS success or exact blocker; do not use vanilla resources as a hidden fallback.
- [ ] Begin NeoForge adapter only after Fabric contract passes.
