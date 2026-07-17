# Milestone 3B Loader Dev Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure Fabric and NeoForge modules as real Minecraft `1.21.1` loader development skeletons that package metadata and log the existing debug snapshot on startup.

**Architecture:** Keep shared diagnostics dependency-free in `mc-dlss-core` and `mc-dlss-debug`. Add small loader API entrypoint classes in the Fabric and NeoForge modules while preserving the existing dependency-free adapter classes for smoke tests.

**Tech Stack:** Java 21, Gradle 9.6.1, Fabric Loom `1.17.13`, Fabric Loader `0.19.3`, Yarn `1.21.1+build.3`, NeoForge `21.1.234`, ModDevGradle `2.0.141`, PowerShell verification scripts.

## Global Constraints

- Minecraft target remains `1.21.1`.
- Iris target remains `1.8.x`.
- Sodium target remains `0.8.x`.
- First DLSS feature remains Super Resolution only.
- Fabric and NeoForge adapters must remain thin.
- No DLSS renderer hooks or Streamline evaluation in this step.
- No Fabric API HUD dependency until Milestone 3C.

---

### Task 1: Loader Metadata Verification

**Files:**
- Create: `scripts/verify-loader-metadata.ps1`
- Modify: `docs/superpowers/plans/2026-07-10-milestone-3b-loader-dev-skeleton.md`

**Interfaces:**
- Consumes: `mc-dlss-fabric/src/main/resources/fabric.mod.json`, `mc-dlss-neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- Produces: script output `Loader metadata verification passed`

- [x] **Step 1: Add metadata verification script**
- [x] **Step 2: Run it and confirm failure because loader metadata does not exist yet**

### Task 2: Fabric Loader Skeleton

**Files:**
- Modify: `settings.gradle`
- Modify: `gradle.properties`
- Modify: `mc-dlss-fabric/build.gradle`
- Create: `mc-dlss-fabric/src/main/resources/fabric.mod.json`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricMod.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: `McDlssFabricEntrypoint.debugSnapshot()`
- Produces: Fabric `ModInitializer` class `dev.mcdlss.fabric.McDlssFabricMod`

- [x] **Step 1: Add Fabric metadata and runtime entrypoint**
- [x] **Step 2: Configure Fabric Loom and Fabric dependencies**
- [x] **Step 3: Keep dependency-free smoke verification from compiling loader API classes**
- [x] **Step 4: Run metadata verification and confirm Fabric metadata passes**

### Task 3: NeoForge Loader Skeleton

**Files:**
- Modify: `settings.gradle`
- Modify: `gradle.properties`
- Modify: `mc-dlss-neoforge/build.gradle`
- Create: `mc-dlss-neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssNeoForgeMod.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: `McDlssNeoForgeEntrypoint.debugSnapshot()`
- Produces: NeoForge `@Mod` class `dev.mcdlss.neoforge.McDlssNeoForgeMod`

- [x] **Step 1: Add NeoForge metadata and runtime entrypoint**
- [x] **Step 2: Configure ModDevGradle and NeoForge dependency**
- [x] **Step 3: Run metadata verification and confirm NeoForge metadata passes**

### Task 4: Dependency Resolution And Build Verification

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-07-10-milestone-3b-loader-dev-skeleton.md`

**Interfaces:**
- Consumes: Tasks 1-3 outputs
- Produces: documented 3B status

- [x] **Step 1: Run `scripts/verify-java.ps1`**
- [x] **Step 2: Run `scripts/verify-loader-metadata.ps1`**
- [x] **Step 3: Run Fabric/NeoForge Gradle build tasks with network access**
- [x] **Step 4: Run `scripts/probe-native.ps1 .\build\native\Release\mc_dlss_native.dll`**
- [x] **Step 5: Update docs with verification results and any blockers**
