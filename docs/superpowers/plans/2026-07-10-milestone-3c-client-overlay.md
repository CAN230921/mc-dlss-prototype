# Milestone 3C Client Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal client HUD diagnostic overlay for Fabric and NeoForge that renders the existing `debugSnapshot()` status.

**Architecture:** Put display text composition in `mc-dlss-debug`, keep Fabric and NeoForge render hooks tiny, and keep shared core/debug modules free of Minecraft APIs.

**Tech Stack:** Java 21, Gradle 9.6.1, Fabric API `0.116.13+1.21.1`, Fabric `HudRenderCallback`, NeoForge `RegisterGuiLayersEvent`, Minecraft GUI text drawing APIs.

## Global Constraints

- Minecraft target remains `1.21.1`.
- Iris target remains `1.8.x`.
- Sodium target remains `0.8.x`.
- Do not implement DLSS renderer hooks or Streamline evaluation in this step.
- Do not make `mc-dlss-core` or `mc-dlss-debug` depend on Minecraft or loader APIs.

---

### Task 1: Shared Overlay Lines

**Files:**
- Create: `mc-dlss-debug/src/testSmoke/java/dev/mcdlss/debug/DlssOverlayLinesSelfTest.java`
- Create: `mc-dlss-debug/src/main/java/dev/mcdlss/debug/DlssOverlayLines.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: `DlssDebugSnapshot`
- Produces: `DlssOverlayLines.fromSnapshot(DlssDebugSnapshot): List<String>`

- [x] **Step 1: Write failing smoke test for overlay lines**
- [x] **Step 2: Run verification and confirm failure because `DlssOverlayLines` does not exist**
- [x] **Step 3: Implement minimal overlay line composer**
- [x] **Step 4: Run verification and confirm tests pass**

### Task 2: Fabric Client HUD

**Files:**
- Modify: `gradle.properties`
- Modify: `mc-dlss-fabric/build.gradle`
- Modify: `mc-dlss-fabric/src/main/resources/fabric.mod.json`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`

**Interfaces:**
- Consumes: `DlssOverlayLines.fromSnapshot(new McDlssFabricEntrypoint().debugSnapshot())`
- Produces: Fabric client entrypoint `dev.mcdlss.fabric.McDlssFabricClientMod`

- [x] **Step 1: Add Fabric API dependency and client entrypoint metadata**
- [x] **Step 2: Implement `HudRenderCallback` overlay drawing**
- [x] **Step 3: Run Fabric module build**

### Task 3: NeoForge Client GUI Layer

**Files:**
- Create: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssNeoForgeClientOverlay.java`

**Interfaces:**
- Consumes: `DlssOverlayLines.fromSnapshot(new McDlssNeoForgeEntrypoint().debugSnapshot())`
- Produces: NeoForge mod-bus GUI layer registration

- [x] **Step 1: Implement client-only `RegisterGuiLayersEvent` subscriber**
- [x] **Step 2: Run NeoForge module build**

### Task 4: Verification And Docs

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-10-milestone-3c-client-overlay.md`

**Interfaces:**
- Consumes: Tasks 1-3 outputs
- Produces: documented Milestone 3C status

- [x] **Step 1: Run `scripts/verify-java.ps1`**
- [x] **Step 2: Run `scripts/verify-loader-metadata.ps1`**
- [x] **Step 3: Run `scripts/gradle-local.ps1 build --console=plain --no-daemon` outside the managed sandbox**
- [x] **Step 4: Run `scripts/probe-native.ps1 .\build\native\Release\mc_dlss_native.dll`**
- [x] **Step 5: Update docs with verification results**
