# Formal Launcher Runtime and DLSS Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix packaged Streamline directory resolution, prevent retry storms, and add a Chinese DLSS page to Sodium video settings.

**Architecture:** Core owns deterministic path selection, configuration values, persistence, and retry policy. NeoForge owns Sodium API registration and lifecycle wiring; the session controller consumes a snapshot of core configuration and rebuilds only on meaningful changes.

**Tech Stack:** Java 21, NeoForge 21.1.x, Sodium 0.8.12-beta.2 config API, Iris 1.8.14-beta.1, JNI/C++ D3D12/Streamline, Gradle smoke tests.

## Global Constraints

- Minecraft version is exactly 1.21.1 for this build.
- All visible setting names, tooltips, states, and errors are Simplified Chinese.
- Packaged native directory takes precedence over `java.library.path`.
- DLSS defaults disabled and a failed open cannot retry every client tick.
- Quality selection must reach native session creation/evaluation rather than remain cosmetic.
- Existing Iris/Sodium rendering remains usable when DLSS is disabled or unavailable.

---

### Task 1: Packaged Native Directory Resolution

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativePluginDirectoryResolver.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativePluginDirectoryResolverSelfTest.java`
- Modify: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/CoreContractSelfTest.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/IrisDlssSessionController.java`

**Interfaces:**
- Produces: `NativePluginDirectoryResolver.resolve(String packagedNativePath, String libraryPath): Path`
- Consumes: system properties `mcDlss.packagedNativePath` and `java.library.path`.

- [ ] **Step 1: Write the failing resolver test**

Test a packaged DLL path whose parent differs from a valid development path; require the packaged parent. Test blank packaged path fallback and both-sources-invalid failure with a Chinese message.

- [ ] **Step 2: Run the smoke test and verify RED**

Run: `.\gradlew.bat :mc-dlss-core:smokeTest --console=plain --no-daemon --no-parallel`

Expected: compilation fails because `NativePluginDirectoryResolver` does not exist.

- [ ] **Step 3: Implement the resolver**

Normalize the packaged DLL path, require an existing regular file, and return its parent. Otherwise scan `java.library.path` entries and return the first existing directory. Throw `IllegalStateException("找不到 MC DLSS 原生组件目录")` when neither source resolves.

- [ ] **Step 4: Replace `firstNativeDirectory()`**

Call the resolver with both system properties. Remove the local path-splitting implementation.

- [ ] **Step 5: Run core smoke and NeoForge compile**

Run: `.\gradlew.bat :mc-dlss-core:smokeTest :mc-dlss-neoforge:compileJava --console=plain --no-daemon --no-parallel`

Expected: `CoreContractSelfTest passed` and `BUILD SUCCESSFUL`.

### Task 2: Configuration and Retry Policy

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/DlssUserConfig.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/DlssRetryGate.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/DlssUserConfigSelfTest.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/DlssQualityMode.java`
- Modify: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/CoreContractSelfTest.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/IrisDlssSessionController.java`

**Interfaces:**
- Produces: immutable config `DlssUserConfig(boolean enabled, DlssQualityMode qualityMode)` with `load(Path)` and `save(Path)`.
- Produces: `DlssRetryGate.canAttempt(key, nowMillis)`, `recordFailure(key, nowMillis)`, and `clear()`.

- [ ] **Step 1: Write failing config and retry tests**

Require defaults `enabled=false`, `qualityMode=QUALITY`; round-trip properties `enabled=true` and `qualityMode=BALANCED`; require Chinese labels for four exposed modes. Require a failure to block the same key for 10 seconds while a changed key is immediately allowed.

- [ ] **Step 2: Run the smoke test and verify RED**

Expected: compilation fails for missing `DlssUserConfig` and `DlssRetryGate`.

- [ ] **Step 3: Implement minimal config persistence and labels**

Use `Properties` with atomic temp-file replacement where supported. Invalid or missing values fall back to disabled/quality. Add `chineseName()` to the quality enum and expose `QUALITY`, `BALANCED`, `PERFORMANCE`, `ULTRA_PERFORMANCE` in the UI.

- [ ] **Step 4: Implement retry gating in the controller**

Skip all work while disabled. On open failure record a cooldown keyed by target generation, dimensions, enabled flag, and quality mode. Clear the gate on successful open, configuration revision change, and target key change. Close active resources immediately when disabled.

- [ ] **Step 5: Improve invalid-session evidence**

Change `decodeLiveDlssSession` failure text to include array length and returned render/output dimensions without logging native handles.

- [ ] **Step 6: Run smoke and compile**

Expected: all core tests pass and NeoForge compiles.

### Task 3: Sodium Chinese Settings Page

**Files:**
- Create: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssSodiumConfigEntryPoint.java`
- Create: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssConfigManager.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssNeoForgeMod.java`
- Modify: `mc-dlss-neoforge/src/main/resources/META-INF/neoforge.mods.toml`

**Interfaces:**
- Consumes: Sodium `ConfigEntryPoint`, `ConfigBuilder`, boolean/enum option builders.
- Produces: `McDlssConfigManager.current()`, `setEnabled(boolean)`, `setQualityMode(DlssQualityMode)`, and monotonically increasing `revision()`.

- [ ] **Step 1: Register a Sodium config entry point**

Annotate the entry point with `@ConfigEntryPointForge("mc_dlss")` and implement `registerConfigLate(ConfigBuilder)`.

- [ ] **Step 2: Build the Chinese page**

Register mod options named `MC DLSS`, then add page `DLSS` with toggle `启用 DLSS` and enum `质量模式`. Use tooltips explaining the Iris shader-pack requirement and quality/performance trade-off. Quality control is enabled only when the pending toggle is enabled.

- [ ] **Step 3: Wire bindings and apply hooks**

Bindings read/write the config manager. Apply hooks save `config/mc-dlss.properties`, bump the revision once, and request a controlled session rebuild.

- [ ] **Step 4: Compile against exact Sodium**

Run: `.\gradlew.bat :mc-dlss-neoforge:compileJava --console=plain --no-daemon --no-parallel`

Expected: `BUILD SUCCESSFUL` with Sodium 0.8.12-beta.2 and Iris 1.8.14-beta.1 compile-only jars.

### Task 4: Native Quality Propagation and Distribution Acceptance

**Files:**
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/McDlssNeoForgeEntrypoint.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/IrisDlssSessionController.java`
- Modify: `mc-dlss-native/include/mc_dlss_native.h`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`
- Modify: `mc-dlss-native/include/live_dlss_registry.h`
- Modify: `mc-dlss-native/src/live_dlss_registry.cpp`
- Test: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeLiveDlssBridgeSelfTest.java`
- Test: `mc-dlss-native/tests/live_dlss_session_test.cpp`

**Interfaces:**
- Extends live-session open with `DlssQualityMode qualityMode` / integer native quality code.

- [ ] **Step 1: Write failing Java and C++ quality propagation tests**

Require each Java quality enum to encode a stable native value and require native session creation to store/use the requested Streamline DLSS preset.

- [ ] **Step 2: Verify both tests fail**

Run core smoke and the native live-session test target. Expected failures mention the missing quality parameter.

- [ ] **Step 3: Thread quality through JNI and session creation**

Pass the selected mode from controller to entry point, bridge, JNI, registry, and Streamline feature creation. Keep existing callers defaulting to `QUALITY` for source compatibility.

- [ ] **Step 4: Build native release and distribution JAR**

Build `mc_dlss_native`, `mc_dlss_bootstrap`, and native tests, then run `:mc-dlss-neoforge:windowsX64DistributionJar`.

- [ ] **Step 5: Run clean packaged probe**

Start `NativeBundleProbeCli` from a new empty directory with `-Djava.library.path=`. Expected: `bundleStage=READY bundleReady=true nativeReady=true`.

- [ ] **Step 6: Perform normal-launcher acceptance**

Replace only the MC DLSS JAR, launch the existing NeoForge 1.21.1 instance, enable an Iris shader pack, open the Chinese DLSS page, enable DLSS, select a quality mode, and apply. Verify overlay reaches `ACTIVE`, frame count rises, session-open logs do not repeat, and disabling DLSS restores normal Iris rendering immediately.
