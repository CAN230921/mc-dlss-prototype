# Dual DLSS and FSR3 Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add official FSR3 super resolution and frame generation beside the existing DLSS SR backend without changing the stable distribution artifact.

**Architecture:** A dynamically loaded FidelityFX API layer owns provider discovery and context lifetime. The existing D3D12 interop session supplies resources to either DLSS SR or FSR3 SR; FSR3 FG consumes the upscaled HUD-less color plus depth, motion vectors, timing, and UI composition data as an independent final stage.

**Tech Stack:** C++17, D3D12/DXGI, AMD FidelityFX SDK 2.3.0 API, JNI, Java 21, NeoForge 1.21.1, CMake/CTest, Gradle.

## Global Constraints

- Use only the official signed FidelityFX SDK 2.3.0 DX12 DLLs already stored under `work/ffx230`.
- Preserve the existing DLSS SR implementation and stable SR JAR.
- Any FSR loader, provider, context, or dispatch error must disable only the affected feature and return a diagnostic.
- Keep FSR3 SR and FSR3 FG independently selectable.
- Do not enable frame generation until valid HUD-less color, depth, motion vectors, timing, reset state, and UI data are available.

---

### Task 1: FidelityFX Capability Report

**Files:**
- Create: `mc-dlss-native/include/fsr3_capability_report.h`
- Create: `mc-dlss-native/src/fsr3_capability_report.cpp`
- Create: `mc-dlss-native/tests/fsr3_capability_report_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `Fsr3CapabilitySnapshot`, `Fsr3FeatureState`, `summarizeFsr3Capability(const Fsr3CapabilitySnapshot&)`.

- [ ] Write a failing test covering loader missing, SR-only, FG-only, dual-provider, and context-failure snapshots.
- [ ] Run `ctest -C Release -R fsr3_capability_report --output-on-failure`; expect failure because the report model is absent.
- [ ] Implement the pure report model with no FidelityFX header dependency.
- [ ] Register the support library and test target in CMake.
- [ ] Run the report test; expect all cases to pass.

### Task 2: Official DX12 Loader Probe

**Files:**
- Create: `mc-dlss-native/include/fsr3_api_runtime.h`
- Create: `mc-dlss-native/src/fsr3_api_runtime.cpp`
- Create: `mc-dlss-native/src/fsr3_capability_probe_main.cpp`
- Create: `mc-dlss-native/tests/fsr3_api_runtime_contract_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Consumes: `D3D12ProbeContext::device()` and `commandQueue()`.
- Produces: `Fsr3ApiRuntime::load(Path)`, `queryProviders(ID3D12Device*)`, `createProbeContexts(...)`, and `Fsr3CapabilitySnapshot`.

- [ ] Write a failing contract test for missing DLL, missing exports, idempotent shutdown, and return-code mapping.
- [ ] Add `FIDELITYFX_ROOT` CMake configuration with exact header, import-library, and signed-DLL checks.
- [ ] Load `amd_fidelityfx_loader_dx12.dll` with `LoadLibraryExW` and resolve all five `ffxFunctions` exports.
- [ ] Query `FFX_API_CREATE_CONTEXT_DESC_TYPE_UPSCALE` and `FFX_API_CREATE_CONTEXT_DESC_TYPE_FRAMEGENERATION` versions using `ffxQuery`.
- [ ] Create and destroy 1920x1080-to-3840x2160 SR and FG contexts, then query their V2 GPU memory usage.
- [ ] Build and run `mc_dlss_fsr3_capability_probe`; require named SR/FG providers and successful context destruction.

### Task 3: Runtime Backend Selection and Fallback

**Files:**
- Create: `mc-dlss-native/include/upscaler_backend.h`
- Create: `mc-dlss-native/src/upscaler_backend.cpp`
- Create: `mc-dlss-native/include/live_fsr3_session.h`
- Create: `mc-dlss-native/src/live_fsr3_session.cpp`
- Create: `mc-dlss-native/tests/upscaler_backend_test.cpp`
- Create: `mc-dlss-native/tests/live_fsr3_session_test.cpp`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `UpscalerBackend { Native, Dlss, Fsr3 }`, `FrameGenerationBackend { Disabled, Fsr3 }`, and `LiveFsr3Session` lifecycle methods.

- [ ] Write failing selection tests for requested backend, provider availability, dispatch latch-off, and fallback ordering.
- [ ] Implement deterministic selection: requested backend, then DLSS SR when available, then FSR3 SR, then native.
- [ ] Implement `LiveFsr3Session` ownership of loader, SR context, FG context, and provider-version diagnostics.
- [ ] Expose JNI status and lifecycle entry points while preserving existing DLSS JNI signatures.
- [ ] Run all native session tests and the existing DLSS tests.

### Task 4: Real FSR3 SR and FG Dispatch

**Files:**
- Modify: `mc-dlss-native/include/live_fsr3_session.h`
- Modify: `mc-dlss-native/src/live_fsr3_session.cpp`
- Modify: `mc-dlss-native/src/live_d3d12_frame_session.cpp`
- Create: `mc-dlss-native/include/fsr3_frame_validation.h`
- Create: `mc-dlss-native/src/fsr3_frame_validation.cpp`
- Create: `mc-dlss-native/tests/fsr3_frame_validation_test.cpp`

**Interfaces:**
- Consumes: input color, output color, depth, motion vectors, HUD-less color, UI surface, command list, jitter, motion scale, frame time, camera near/far/FOV, and reset flag.
- Produces: one upscaled frame and optional generated frame, plus exact FidelityFX return code and timing.

- [ ] Write failing validation tests for dimensions, formats, missing motion/depth/UI, camera reset, and zero frame time.
- [ ] Implement FSR3 SR dispatch using the selected provider and existing D3D12 resource states.
- [ ] Implement FSR3 FG prepare/configure/dispatch with HUD-less and UI composition descriptors.
- [ ] Add resource barriers and queue synchronization at the existing interop ownership boundary.
- [ ] Latch SR or FG off after a dispatch error and recreate only on pipeline reset.
- [ ] Run validation, native session, interop, depth, motion, and DLSS regression tests.

### Task 5: NeoForge Settings and Experimental Distribution

**Files:**
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/DlssSettings.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeDlssBridge.java`
- Modify: `mc-dlss-neoforge/src/main/java/dev/mcdlss/neoforge/client/DlssOptionsScreen.java`
- Modify: `mc-dlss-neoforge/src/main/resources/assets/mc_dlss/lang/zh_cn.json`
- Modify: `mc-dlss-neoforge/src/main/resources/assets/mc_dlss/lang/en_us.json`
- Modify: `mc-dlss-neoforge/build.gradle`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/Fsr3BackendSettingsSelfTest.java`

**Interfaces:**
- Produces settings for upscaler `Native/DLSS/FSR3`, frame generation `Off/FSR3`, quality mode, and concise runtime status.

- [ ] Write a failing Java smoke test for configuration defaults, persistence, invalid-value fallback, and Chinese labels.
- [ ] Add independent upscaler and FG selectors to the existing options screen.
- [ ] Embed the three signed AMD DLLs and their SHA-256 metadata only in the experimental classifier JAR.
- [ ] Build the experimental JAR and run `NativeBundleProbeCli` from a clean directory.
- [ ] Verify the stable SR artifact hash is unchanged.
- [ ] Launch NeoForge, confirm backend status, then measure native, DLSS SR, DLSS SR+FSR3 FG, and FSR3 SR+FG at the same scene and resolution.

## Final Verification

- [ ] Run all CTest targets in Release.
- [ ] Run `gradlew.bat build --console=plain --no-daemon --no-parallel`.
- [ ] Confirm every embedded AMD DLL has a valid AMD Authenticode signature.
- [ ] Confirm the experimental JAR passes clean extraction and native loading.
- [ ] Confirm the stable SR JAR SHA-256 remains `70D0A7332A73C9F77B027EB4B09EFC12AE9142082EDF316F34570B821758B717`.
