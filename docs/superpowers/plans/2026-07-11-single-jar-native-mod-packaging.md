# Single-JAR Native Mod Packaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one Fabric 1.21.1 mod JAR that embeds, extracts, verifies, and loads the Windows x64 DLSS native bundle without launcher JVM arguments.

**Architecture:** Java selects and extracts a hashed embedded native bundle. A system-only bootstrap DLL establishes a safe bundle-local Windows DLL search path and loads the existing Streamline JNI DLL. Fabric configuration enables the verified Live DLSS controller only after native bootstrap and runtime probing succeed.

**Tech Stack:** Java 21, Fabric Loader/API, Gradle/Fabric Loom, C++20, JNI, Win32 `AddDllDirectory`/`LoadLibraryExW`, CMake, SHA-256.

## Global Constraints

- Support Fabric 1.21.1 on Windows x64 with NVIDIA RTX first.
- The distribution is one JAR copied to `mods`; no launcher-specific JVM arguments.
- Extract only below `<run-directory>/mc-dlss/natives/<bundle-sha256>`.
- Do not modify system environment variables or download binaries at runtime.
- Unsupported or failed native loading disables DLSS without crashing Minecraft.
- Preserve existing development properties, A/B fast path, resize recovery, and viewport fixes.
- The repository has no Git metadata; replace commit steps with local verification evidence.

---

### Task 1: Native Bundle Manifest And Cache

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBundleManifest.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBundleExtractor.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeBundleExtractorSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: `NativeBundleManifest.parse(InputStream)` and `NativeBundleExtractor.extract(ClassLoader, Path, NativeBundleManifest)` returning a verified bundle directory.

- [ ] Write a failing self-test using an in-memory manifest and temporary cache. Cover valid extraction, cache reuse, hash repair, and `../` path rejection.
- [ ] Run `powershell.exe -ExecutionPolicy Bypass -File scripts/verify-java.ps1`; expect missing manifest/extractor compile errors.
- [ ] Implement strict property parsing, SHA-256 verification, temporary-file atomic moves, and a bundle lock file.
- [ ] Re-run Java verification; expect all self-tests to pass.

### Task 2: Windows Bootstrap DLL

**Files:**
- Create: `mc-dlss-native/src/native_bootstrap.cpp`
- Create: `mc-dlss-native/include/native_bootstrap.h`
- Create: `mc-dlss-native/tests/native_bootstrap_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces JNI export `Java_dev_mcdlss_core_NativeBundleBootstrap_nativeLoad(JNIEnv*, jclass, jstring, jstring)` returning an error string or empty string on success.

- [ ] Write a native test that rejects relative paths and mismatched parent directories, then loads a fixture DLL from an isolated directory twice.
- [ ] Build and run the test; expect failure because bootstrap APIs do not exist.
- [ ] Implement canonical path containment, `SetDefaultDllDirectories`, `AddDllDirectory`, `LoadLibraryExW`, process-lifetime retention, and idempotence.
- [ ] Run default and bootstrap CTest suites; expect all tests to pass.

### Task 3: Java Platform Selection And Bootstrap

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBundlePlatform.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBundleBootstrap.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBundleLoadResult.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeBundleBootstrapSelfTest.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: `NativeBundleBootstrap.load(Path runDirectory)` with stages `PLATFORM`, `MANIFEST`, `EXTRACTION`, `BOOTSTRAP`, `MAIN_LIBRARY`, and `READY`.

- [ ] Write failing tests for Windows x64 selection, unsupported-platform status, single-load idempotence, and structured failure messages.
- [ ] Run Java verification; expect missing-class errors.
- [ ] Implement platform selection, extraction orchestration, absolute `System.load` of bootstrap, and native main-library loading.
- [ ] Make `NativeLibraryBridge` accept an already loaded JNI library without calling `System.loadLibrary` again.
- [ ] Run Java verification and JNI export inspection.

### Task 4: Embedded Native Distribution JAR

**Files:**
- Modify: `mc-dlss-fabric/build.gradle`
- Create: `scripts/verify-distribution.ps1`
- Generate: `mc-dlss-fabric/build/generated/native-bundle/manifest.properties`

**Interfaces:**
- Produces Gradle task `windowsX64DistributionJar` and artifact `mc-dlss-fabric-0.1.0-windows-x86_64.jar`.

- [ ] Write distribution verification that fails unless all seven DLLs and the manifest exist under `native/windows-x86_64/` and every embedded SHA-256 matches.
- [ ] Run it against the current JAR; expect missing native bundle failure.
- [ ] Add Gradle inputs for Release bootstrap/JNI/Streamline/DLSS files, generate deterministic manifest content, embed files, and set the classifier.
- [ ] Build the distribution and run verification; expect exact inventory and hashes to pass.

### Task 5: Packaged Configuration And Safe Enablement

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssConfig.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/McDlssConfigSelfTest.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: `McDlssConfig.load(Path configDirectory)` with `enabled`, `mode`, and `debugOverlay`; packaged startup uses bundle readiness plus config to install render hooks.

- [ ] Write failing tests for defaults, persisted values, invalid mode fallback, and development-property precedence.
- [ ] Run Java verification; expect missing config errors.
- [ ] Implement atomic property writing and Quality-only validation.
- [ ] Load the native bundle before creating `McDlssFabricEntrypoint`; install the controller only when enabled and native loading succeeds.
- [ ] Verify disabled and unsupported paths leave framebuffer hooks uninstalled.

### Task 6: Clean-Instance Acceptance

**Files:**
- Create: `work/distribution-test/` at test time.
- Update: `docs/windows-toolchain.md`
- Update: `docs/local-status-2026-07-10.md`

**Interfaces:**
- Consumes the final distribution JAR only; no external native directory or JVM native-path flags.

- [ ] Run all Java self-tests, full Gradle build, default native 13/13, and Streamline suite excluding only the known shutdown probe.
- [ ] Create a clean Fabric 1.21.1 test instance containing Fabric API and the single distribution JAR.
- [ ] Launch without `java.library.path`, `PATH`, or project development properties; verify native stage `READY`.
- [ ] Enter a world and require `COMPLETE ready=true`, `30/30`, `300/300`, matching hashes, and balanced A/B slots.
- [ ] Resize the window and require a second `COMPLETE ready=true` with correct block outline and breaking-crack alignment.
- [ ] Save the final log and JAR hash under `outputs/` and document installation as copying one JAR into `mods`.
