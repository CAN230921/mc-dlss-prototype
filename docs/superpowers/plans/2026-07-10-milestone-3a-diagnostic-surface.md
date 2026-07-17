# Milestone 3A Diagnostic Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dependency-free debug snapshot factory and loader adapter methods so Fabric and NeoForge can expose the current native/backend diagnostic state.

**Architecture:** Keep core contracts in `mc-dlss-core`, snapshot composition in `mc-dlss-debug`, and loader entrypoints as thin adapters. Avoid Minecraft/Fabric/NeoForge APIs until the shared diagnostic surface is proven.

**Tech Stack:** Java 21 records/classes, existing Gradle multi-project layout, dependency-free smoke tests, PowerShell verification scripts.

## Global Constraints

- Minecraft target remains `1.21.1`.
- Iris target remains `1.8.x`.
- Sodium target remains `0.8.x`.
- Fabric and NeoForge adapters must remain thin.
- Do not implement renderer hooks or DLSS evaluation in this step.
- Do not add external dependencies.

---

### Task 1: Debug Snapshot Factory

**Files:**
- Create: `mc-dlss-debug/src/testSmoke/java/dev/mcdlss/debug/DlssDebugSnapshotFactorySelfTest.java`
- Create: `mc-dlss-debug/src/main/java/dev/mcdlss/debug/DlssDebugSnapshotFactory.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: `NativeProbeResult`, `DlssBackendDiagnostic`
- Produces: `DlssDebugSnapshotFactory.fromProbe(NativeProbeResult, DlssBackendDiagnostic)`

- [x] **Step 1: Write failing smoke test for unavailable native probe**
- [x] **Step 2: Write failing smoke test for native available but OpenGL backend blocked**
- [x] **Step 3: Update `verify-java.ps1` to compile and run debug smoke tests**
- [x] **Step 4: Run verification and confirm failure because `DlssDebugSnapshotFactory` does not exist**
- [x] **Step 5: Implement minimal factory**
- [x] **Step 6: Run verification and confirm debug tests pass**

### Task 2: Loader Adapter Debug Snapshots

**Files:**
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/McDlssFabricEntrypointSelfTest.java`
- Create: `mc-dlss-neoforge/src/testSmoke/java/dev/mcdlss/neoforge/McDlssNeoForgeEntrypointSelfTest.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricEntrypoint.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssNeoForgeEntrypoint.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: `DlssDebugSnapshotFactory.fromProbe(...)`
- Produces: `McDlssFabricEntrypoint.debugSnapshot()`, `McDlssNeoForgeEntrypoint.debugSnapshot()`

- [x] **Step 1: Write failing Fabric adapter smoke test**
- [x] **Step 2: Write failing NeoForge adapter smoke test**
- [x] **Step 3: Update `verify-java.ps1` to run loader smoke tests**
- [x] **Step 4: Run verification and confirm failure because adapter methods do not exist**
- [x] **Step 5: Implement `debugSnapshot()` in both adapters**
- [x] **Step 6: Run verification and confirm loader tests pass**

### Task 3: Documentation And Verification

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-10-milestone-3a-diagnostic-surface.md`

**Interfaces:**
- Consumes: all Task 1 and Task 2 outputs
- Produces: documented Milestone 3A status and verification evidence

- [x] **Step 1: Update status docs with Milestone 3A result**
- [x] **Step 2: Run `scripts/verify-java.ps1`**
- [x] **Step 3: Run `scripts/gradle-local.ps1 build --console=plain --no-daemon` outside the managed sandbox if the sandbox jar-classpath issue appears**
- [x] **Step 4: Run `scripts/probe-native.ps1 .\build\native\Release\mc_dlss_native.dll`**
- [x] **Step 5: Mark this plan complete only after fresh verification output is checked**
