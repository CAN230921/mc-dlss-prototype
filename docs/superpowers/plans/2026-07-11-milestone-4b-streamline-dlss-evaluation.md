# Milestone 4B Streamline DLSS Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Perform one verified Streamline 2.12 DLSS Super Resolution evaluation over project-owned D3D12 resources and emit a machine-readable report.

**Architecture:** Extract Milestone 4A's D3D12 ownership into a reusable static library. Keep the existing probe linked to system D3D libraries, and add a separate optional executable that links Streamline only when an explicit SDK root is supplied.

**Tech Stack:** C++17, CMake 3.24+, D3D12, DXGI 1.6, Streamline 2.12.0, PowerShell, Visual Studio 2022 Build Tools.

## Global Constraints

- Windows x64 and D3D12 only.
- DLSS Super Resolution Quality mode only; output is 1920 x 1080.
- Project identity is GUID `7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e`, custom engine version `mc-dlss-prototype/0.1.0`, and numeric application ID `0`.
- Default JNI and Milestone 4A builds must not require Streamline.
- The optional target uses signed production DLLs from Streamline 2.12.0.
- No Minecraft hooks, OpenGL interop, swapchain, presentation, Frame Generation, Ray Reconstruction, Reflex, NIS, or OTA updates.
- The directory is not a Git repository, so no commit commands are executed.

---

### Task 1: Streamline report contract

**Files:**
- Create: `mc-dlss-native/include/streamline_probe_report.h`
- Create: `mc-dlss-native/src/streamline_probe_report.cpp`
- Create: `mc-dlss-native/tests/streamline_probe_report_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `StreamlineProbeSnapshot`, `validateStreamlineProbeSnapshot`, `toJson` in namespace `mc_dlss`.
- Consumes: `ProbeSnapshot` from Milestone 4A.

- [ ] **Step 1: Write the failing test**

Construct a complete snapshot with every stage true and assert validation succeeds and JSON contains the fixed project ID plus `"streamlineDlssEvaluationSucceeded":true`. Set `resourcesTagged=false` and assert validation and JSON success become false.

```cpp
mc_dlss::StreamlineProbeSnapshot snapshot{};
snapshot.projectId = "7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e";
snapshot.streamlineInitialized = true;
snapshot.d3dDeviceAccepted = true;
snapshot.dlssSupported = true;
snapshot.optionsAccepted = true;
snapshot.constantsAccepted = true;
snapshot.resourcesTagged = true;
snapshot.evaluationCalled = true;
snapshot.streamlineDlssEvaluationSucceeded = true;
snapshot.commandSubmissionCompleted = true;
snapshot.streamlineShutdownSucceeded = true;
assert(mc_dlss::validateStreamlineProbeSnapshot(snapshot));
```

- [ ] **Step 2: Run the build and verify RED**

Run `scripts/configure-native.ps1`. Expected: compilation fails because `streamline_probe_report.h` does not exist.

- [ ] **Step 3: Implement the report model and JSON**

Use exact stable fields from the design. Validation requires all stage booleans, non-empty adapter/project/version, non-zero differing resolutions, `lastResult == "eOk"`, and successful shutdown.

- [ ] **Step 4: Register and run the pure test**

Add `mc_dlss_streamline_probe_report_test` to CTest and link only `mc_dlss_probe_support`. Run `scripts/configure-native.ps1`; expected: all SDK-independent tests pass.

### Task 2: Reusable D3D12 context

**Files:**
- Create: `mc-dlss-native/include/d3d12_probe_context.h`
- Create: `mc-dlss-native/src/d3d12_probe_context.cpp`
- Modify: `mc-dlss-native/src/d3d12_resource_probe.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `D3D12ProbeContext::create(inputWidth, inputHeight, outputWidth, outputHeight)`, native resource/device/list getters, adapter LUID, `submitAndWait()`, and `snapshot()`.
- Consumes: D3D12 resource contract from Milestone 4A.

- [ ] **Step 1: Preserve the existing runtime test as the failing refactor guard**

Move D3D12 implementation files into a new `mc_dlss_d3d12_probe_support` static target before the class exists. Expected: build fails on missing context symbols while the existing `d3d12_resource_probe` CTest remains the behavioral acceptance test.

- [ ] **Step 2: Implement `D3D12ProbeContext` with PImpl**

The public header exposes native D3D12 pointers and keeps WRL details private:

```cpp
class D3D12ProbeContext {
public:
    static std::unique_ptr<D3D12ProbeContext> create(
        std::uint32_t inputWidth, std::uint32_t inputHeight,
        std::uint32_t outputWidth, std::uint32_t outputHeight);
    ~D3D12ProbeContext();
    ID3D12Device* device() const noexcept;
    ID3D12GraphicsCommandList* commandList() const noexcept;
    ID3D12Resource* inputColor() const noexcept;
    ID3D12Resource* depth() const noexcept;
    ID3D12Resource* motionVectors() const noexcept;
    ID3D12Resource* outputColor() const noexcept;
    LUID adapterLuid() const noexcept;
    ProbeSnapshot snapshot() const;
    bool submitAndWait();
};
```

Creation records clear commands but leaves the graphics command list open. `submitAndWait` closes, executes, signals, and waits once.

- [ ] **Step 3: Reimplement the 4A wrapper over the context**

`runD3D12ResourceProbe` creates the context, calls `submitAndWait`, updates `commandSubmissionCompleted`, and returns the same schema-version-1 report values as before.

- [ ] **Step 4: Verify no 4A regression**

Run `scripts/configure-native.ps1` and `scripts/probe-d3d12-resources.ps1`. Expected: all CTest tests pass and the report remains `success=true` with 1280 x 720 input and 1920 x 1080 output.

### Task 3: Optional Streamline build and one DLSS evaluation

**Files:**
- Create: `mc-dlss-native/include/streamline_dlss_probe.h`
- Create: `mc-dlss-native/src/streamline_dlss_probe.cpp`
- Create: `mc-dlss-native/src/streamline_dlss_probe_main.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `runStreamlineDlssProbe(pluginPath, logPath)` and `mc_dlss_streamline_probe.exe` only when `STREAMLINE_ROOT` is valid.
- Consumes: `D3D12ProbeContext`, Streamline headers/import library, and five production runtime DLLs.

- [ ] **Step 1: Add an optional target with a failing smoke test**

Configure with `-DSTREAMLINE_ROOT=<sdk>`. Register `streamline_dlss_probe` CTest invoking the new executable. Expected: generation fails because the source files do not exist.

- [ ] **Step 2: Initialize Streamline before D3D12**

Set only `sl::kFeatureDLSS`, `renderAPI=eD3D12`, `flags=eUseFrameBasedResourceTagging`, plugin/log paths, custom project GUID, engine type, and engine version. Call `slInit` before creating `D3D12ProbeContext`, and always call `slShutdown` on initialized paths.

- [ ] **Step 3: Query settings and create the context**

Call `slDLSSGetOptimalSettings` with Quality mode and 1920 x 1080 output. Create resources using the returned render width/height and verify it is non-zero and smaller than output.

- [ ] **Step 4: Set device, support, options, constants, and tags**

Call `slSetD3DDevice`; check `slIsFeatureSupported` using the context adapter LUID; obtain frame token 0; use viewport 0. Set auto exposure, HDR color, no alpha upscaling, recommended sharpness, deterministic camera constants, and four `eValidUntilEvaluate` tags with exact resource states and extents.

- [ ] **Step 5: Evaluate and synchronize**

Call `slEvaluateFeature(sl::kFeatureDLSS, ...)`, submit the command list, wait for the fence, call `slFreeResources`, then shut down Streamline. Record every result in the report and return non-zero unless the full report validates.

- [ ] **Step 6: Copy required production DLLs and run the target**

Add post-build copies for `sl.interposer.dll`, `sl.common.dll`, `sl.pcl.dll`, `sl.dlss.dll`, and `nvngx_dlss.dll`. Run the Streamline-enabled build and CTest. Expected: `streamline_dlss_probe` passes on the RTX 4070 Laptop.

### Task 4: Reproducible scripts, evidence, and regressions

**Files:**
- Create: `scripts/configure-native-streamline.ps1`
- Create: `scripts/probe-streamline-dlss.ps1`
- Modify: `docs/windows-toolchain.md`
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`

**Interfaces:**
- Produces: `outputs/streamline-dlss-probe-2026-07-11.json` and `outputs/streamline-logs/sl.log`.
- Consumes: local SDK root and Streamline-enabled executable.

- [ ] **Step 1: Add a separate Streamline build script**

Resolve the default local SDK at `../work/upstream/streamline-sdk-v2.12.0` relative to the project parent, allow `-StreamlineRoot` override, configure `build/native-streamline`, build Release, and run CTest.

- [ ] **Step 2: Add report assertions**

Run the executable and parse JSON. Require schema 1, project GUID, DLSS support, all setup stages, evaluation success, GPU completion, shutdown success, and differing input/output resolution.

- [ ] **Step 3: Run both native paths and full regressions**

Run:

```powershell
./scripts/configure-native.ps1
./scripts/probe-d3d12-resources.ps1
./scripts/configure-native-streamline.ps1
./scripts/probe-streamline-dlss.ps1
./scripts/verify-java.ps1
./scripts/verify-loader-metadata.ps1
./scripts/probe-native.ps1 ./build/native/Release/mc_dlss_native.dll
./scripts/gradle-local.ps1 -GradleArgs 'build','--console=plain','--no-daemon'
```

Expected: all native tests, both reports, Java tests, metadata, JNI, and Gradle pass.

- [ ] **Step 4: Record only observed evidence**

Document exact Streamline results and preserve the non-claim that Minecraft remains OpenGL-blocked.

## Plan Self-Review

- Coverage: identity, optional SDK boundary, resource reuse, support query, options, constants, tags, evaluation, GPU wait, cleanup, report, and regressions are covered.
- Scope: no visual quality claim, Minecraft hook, or frame generation work is included.
- Interfaces: `D3D12ProbeContext`, `StreamlineProbeSnapshot`, and `runStreamlineDlssProbe` are defined once and consistently consumed.
- Placeholders: no deferred implementation placeholders are present.
