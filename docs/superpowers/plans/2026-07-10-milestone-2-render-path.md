# Milestone 2 Render Path Investigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a source-backed feasibility note saying whether direct DLSS SR integration is viable in the current Iris/Sodium Minecraft `1.21.1` render backend.

**Architecture:** Keep the project code unchanged and perform a read-only upstream source investigation. Store upstream repositories under `work/upstream/`, save findings in `docs/milestone-2-render-path-investigation.md`, and update `docs/upstream-notes.md` with durable conclusions.

**Tech Stack:** Git, Java/Minecraft renderer source, Iris `1.8.x`, Sodium `0.8.x`, LWJGL/OpenGL source inspection, NVIDIA Streamline/DLSS `v2.12.0` documentation.

## Global Constraints

- Minecraft: `1.21.1`
- Iris: `1.8.x`
- Sodium: `0.8.x`
- Loaders: Fabric and NeoForge
- OS: Windows 10/11 x64
- GPU target: RTX 40 series, first test machine is an RTX 4070 Laptop
- First DLSS feature: Super Resolution only
- Deferred DLSS feature: Frame Generation
- Do not implement Minecraft renderer hooks in Milestone 2.
- Do not implement DLSS inside a shaderpack.
- Use primary sources only: official repositories, release source artifacts, and official NVIDIA Streamline documentation.

---

### Task 1: Fetch And Pin Upstream Sources

**Files:**
- Create: `docs/milestone-2-render-path-investigation.md`

**Interfaces:**
- Consumes: target versions from `HANDOFF.md`.
- Produces: exact source paths and commits/tags for Iris and Sodium.

- [x] **Step 1: Query upstream tags**

Run:

```powershell
git ls-remote --tags https://github.com/IrisShaders/Iris.git
git ls-remote --tags https://github.com/CaffeineMC/sodium.git
```

Expected: tags or release branches for the target versions can be identified, or the note records that exact tags were not present.

- [x] **Step 2: Clone source repositories**

Run:

```powershell
git clone --recursive https://github.com/IrisShaders/Iris.git work/upstream/Iris
git clone --recursive https://github.com/CaffeineMC/sodium.git work/upstream/Sodium
```

Expected: repositories exist under `work/upstream/`.

- [x] **Step 3: Check out closest target refs**

Use exact `1.8.x` and `0.8.x` release refs if present. If exact target release tags are missing, use the closest visible branch/tag and record the mismatch.

### Task 2: Inspect Backend API Ownership

**Files:**
- Modify: `docs/milestone-2-render-path-investigation.md`

**Interfaces:**
- Consumes: pinned Iris/Sodium source paths.
- Produces: backend API determination.

- [x] **Step 1: Search renderer API usage**

Run searches for `GL`, `GlStateManager`, `RenderSystem`, `Framebuffer`, `Vk`, `D3D`, `ID3D`, `CommandBuffer`, `Texture`, and `RenderTarget`.

- [x] **Step 2: Record source-backed facts**

Document files and line numbers showing whether the renderer path is OpenGL/LWJGL, Vulkan, D3D, or another backend.

### Task 3: Inspect Frame Resource Boundaries

**Files:**
- Modify: `docs/milestone-2-render-path-investigation.md`

**Interfaces:**
- Consumes: backend API findings.
- Produces: map of scene color, depth, post-process, and UI composition ownership.

- [x] **Step 1: Locate world render pipeline classes**

Search Iris/Sodium for pipeline, framebuffer, render target, post-process, composite, terrain render, and hand/HUD boundaries.

- [x] **Step 2: Record the likely DLSS evaluation window**

Identify whether there is a source-visible point after world scene rendering and before UI/HUD composition.

### Task 4: Compare Against Streamline Requirements

**Files:**
- Modify: `docs/milestone-2-render-path-investigation.md`

**Interfaces:**
- Consumes: Streamline notes and frame resource findings.
- Produces: feasibility decision.

- [x] **Step 1: List required DLSS SR inputs**

Use `docs/upstream-notes.md` and official Streamline docs already cloned under `work/upstream/Streamline`.

- [x] **Step 2: Mark each requirement as satisfied, missing, or requiring renderer fork**

Requirements: color, output, depth, motion vectors, constants, jitter/reset, command buffer/resource states, native D3D/Vulkan resources.

### Task 5: Final Verification And Recommendation

**Files:**
- Modify: `docs/milestone-2-render-path-investigation.md`
- Modify: `docs/upstream-notes.md`
- Modify: `docs/local-status-2026-07-10.md`

**Interfaces:**
- Consumes: all findings.
- Produces: final recommendation for Milestone 3/4 path.

- [x] **Step 1: Run local project verification**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-java.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\gradle-local.ps1 build --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\probe-native.ps1 .\build\native\Release\mc_dlss_native.dll
```

Expected: existing prototype still passes.

- [x] **Step 2: Save final recommendation**

Document one of:

- Direct integration feasible with current backend.
- Direct integration blocked; renderer fork/backend replacement required.
- Further specific source inspection required before a decision.
