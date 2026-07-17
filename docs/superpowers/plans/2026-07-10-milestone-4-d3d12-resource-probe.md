# Milestone 4 D3D12 Resource Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and run a project-owned D3D12 probe that proves the native layer can own a DLSS-ready device, command context, input color, depth, motion vectors, and output color resources.

**Architecture:** A platform-neutral report model and validator are compiled into a native support library. Windows-only D3D12 code populates the model from real device and texture objects; a small executable writes deterministic JSON and returns failure when any required capability is missing.

**Tech Stack:** C++17, CMake 3.24+, Visual Studio 2022 Build Tools, DXGI 1.6, Direct3D 12, WRL `ComPtr`, PowerShell.

## Global Constraints

- Windows 10/11 x64 only.
- First test GPU: NVIDIA GeForce RTX 4070 Laptop GPU.
- Input resolution is exactly 1280 x 720; output resolution is exactly 1920 x 1080.
- Super Resolution only; no Frame Generation, Minecraft hook, loader hook, OpenGL interop, or Streamline linking.
- The existing JNI DLL behavior and exported functions remain unchanged.
- The native probe must compile without `work/upstream/Streamline`.
- The project is not a Git repository, so commit steps are recorded as skipped rather than attempted.

---

### Task 1: Pure report contract and validation

**Files:**
- Create: `mc-dlss-native/include/d3d12_probe_report.h`
- Create: `mc-dlss-native/src/d3d12_probe_report.cpp`
- Create: `mc-dlss-native/tests/d3d12_probe_report_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `ProbeSnapshot`, `validateProbeSnapshot(const ProbeSnapshot&)`, and `toJson(const ProbeSnapshot&)` in namespace `mc_dlss`.
- Consumes: only C++17 standard-library types; no GPU is required.

- [ ] **Step 1: Write the failing report tests**

Create a test executable whose `main()` constructs one complete snapshot and one incomplete snapshot. Assert that the complete snapshot validates, the incomplete snapshot fails, success JSON contains `"backend":"D3D12"`, all four resource names, unequal resolutions, and failure JSON contains `"success":false` plus the supplied message.

```cpp
mc_dlss::ProbeSnapshot complete{};
complete.adapterName = "NVIDIA GeForce RTX 4070 Laptop GPU";
complete.vendorId = 0x10de;
complete.deviceAvailable = true;
complete.commandContextAvailable = true;
complete.inputWidth = 1280;
complete.inputHeight = 720;
complete.outputWidth = 1920;
complete.outputHeight = 1080;
complete.inputColor = true;
complete.depth = true;
complete.motionVectors = true;
complete.outputColor = true;
complete.commandSubmissionCompleted = true;
assert(mc_dlss::validateProbeSnapshot(complete));
assert(mc_dlss::toJson(complete).find("\"backend\":\"D3D12\"") != std::string::npos);
```

- [ ] **Step 2: Configure and build to verify the test fails**

Run:

```powershell
./scripts/configure-native.ps1
```

Expected: compilation fails because `d3d12_probe_report.h` and its functions do not exist yet.

- [ ] **Step 3: Implement the minimal report model**

Define the exact model:

```cpp
struct ProbeSnapshot {
    std::string adapterName;
    std::uint32_t vendorId = 0;
    bool softwareAdapter = false;
    bool debugLayerAvailable = false;
    bool deviceAvailable = false;
    bool commandContextAvailable = false;
    std::uint32_t inputWidth = 0;
    std::uint32_t inputHeight = 0;
    std::uint32_t outputWidth = 0;
    std::uint32_t outputHeight = 0;
    bool inputColor = false;
    bool depth = false;
    bool motionVectors = false;
    bool outputColor = false;
    bool commandSubmissionCompleted = false;
    std::string message;
};

bool validateProbeSnapshot(const ProbeSnapshot& snapshot) noexcept;
std::string toJson(const ProbeSnapshot& snapshot);
```

Validation returns true only for a non-software adapter, a valid device and command context, non-zero input/output dimensions that differ, all four resources, and completed command submission. JSON escaping must handle quotes, backslashes, newline, carriage return, and tab in adapter names and messages.

- [ ] **Step 4: Add the support library and test target**

Add `mc_dlss_probe_support` as a static library, link it into `mc_dlss_native`, enable CTest, add `mc_dlss_probe_report_test`, and register it as `d3d12_probe_report`.

```cmake
add_library(mc_dlss_probe_support STATIC src/d3d12_probe_report.cpp)
target_include_directories(mc_dlss_probe_support PUBLIC include)
target_compile_features(mc_dlss_probe_support PUBLIC cxx_std_17)

include(CTest)
if(BUILD_TESTING)
    add_executable(mc_dlss_probe_report_test tests/d3d12_probe_report_test.cpp)
    target_link_libraries(mc_dlss_probe_report_test PRIVATE mc_dlss_probe_support)
    add_test(NAME d3d12_probe_report COMMAND mc_dlss_probe_report_test)
endif()
```

- [ ] **Step 5: Build and run the unit test**

Run:

```powershell
./scripts/configure-native.ps1
ctest --test-dir build/native -C Release --output-on-failure
```

Expected: `d3d12_probe_report` passes.

### Task 2: Real D3D12 context and resource set

**Files:**
- Create: `mc-dlss-native/include/d3d12_resource_probe.h`
- Create: `mc-dlss-native/src/d3d12_resource_probe.cpp`
- Create: `mc-dlss-native/src/d3d12_resource_probe_main.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Consumes: `ProbeSnapshot` and `toJson` from Task 1.
- Produces: `ProbeSnapshot runD3D12ResourceProbe(std::uint32_t inputWidth, std::uint32_t inputHeight, std::uint32_t outputWidth, std::uint32_t outputHeight)` and `mc_dlss_resource_probe.exe`.

- [ ] **Step 1: Write a failing argument/runtime smoke test**

Register a CTest test that invokes the executable with fixed dimensions and a report path:

```cmake
add_test(
    NAME d3d12_resource_probe
    COMMAND mc_dlss_resource_probe
        --input-width 1280 --input-height 720
        --output-width 1920 --output-height 1080
        --report ${CMAKE_BINARY_DIR}/d3d12-resource-probe-test.json
)
```

Expected before implementation: CMake generation or linking fails because the executable source and `runD3D12ResourceProbe` do not exist.

- [ ] **Step 2: Implement hardware adapter and device creation**

Use `CreateDXGIFactory2`, `IDXGIFactory6::EnumAdapterByGpuPreference`, and `D3D12CreateDevice`. Skip adapters with `DXGI_ADAPTER_FLAG3_SOFTWARE`. Record the selected adapter description and vendor ID after converting UTF-16 with `WideCharToMultiByte`.

Create a direct command queue, direct command allocator, graphics command list, fence, and Win32 event. Wrap COM interfaces in `Microsoft::WRL::ComPtr` and the event in a destructor-backed handle class.

- [ ] **Step 3: Create the four committed textures**

Create default-heap `TEXTURE2D` resources with one mip, one array layer, and sample count one:

```cpp
createTexture(1280, 720, DXGI_FORMAT_R16G16B16A16_FLOAT,
              D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET,
              D3D12_RESOURCE_STATE_RENDER_TARGET);
createTexture(1280, 720, DXGI_FORMAT_D32_FLOAT,
              D3D12_RESOURCE_FLAG_ALLOW_DEPTH_STENCIL,
              D3D12_RESOURCE_STATE_DEPTH_WRITE);
createTexture(1280, 720, DXGI_FORMAT_R16G16_FLOAT,
              D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET,
              D3D12_RESOURCE_STATE_RENDER_TARGET);
createTexture(1920, 1080, DXGI_FORMAT_R16G16B16A16_FLOAT,
              D3D12_RESOURCE_FLAG_ALLOW_UNORDERED_ACCESS,
              D3D12_RESOURCE_STATE_UNORDERED_ACCESS);
```

Supply matching clear values for render-target and depth resources. Mark each resource field true only after `CreateCommittedResource` succeeds and its descriptor matches size, format, dimension, flags, and sample count.

- [ ] **Step 4: Prove command submission**

Close the command list, execute it on the direct queue, signal fence value `1`, wait with `SetEventOnCompletion`, and set `commandSubmissionCompleted` only after `GetCompletedValue() >= 1`.

- [ ] **Step 5: Implement the command-line boundary**

Accept only the fixed dimension options plus optional `--report <path>`. Print exactly one JSON object to standard output. Write the same JSON plus a newline to the report file. Return codes are `0` success, `2` arguments, `3` D3D12 probe failure, and `4` report-write failure.

- [ ] **Step 6: Link the Windows graphics libraries and run CTest**

```cmake
add_executable(mc_dlss_resource_probe
    src/d3d12_resource_probe.cpp
    src/d3d12_resource_probe_main.cpp
)
target_link_libraries(mc_dlss_resource_probe
    PRIVATE mc_dlss_probe_support d3d12 dxgi dxguid
)
```

Run:

```powershell
./scripts/configure-native.ps1
ctest --test-dir build/native -C Release --output-on-failure
```

Expected: both `d3d12_probe_report` and `d3d12_resource_probe` pass.

### Task 3: Reproducible runtime verification

**Files:**
- Create: `scripts/probe-d3d12-resources.ps1`
- Modify: `scripts/configure-native.ps1`
- Modify: `docs/windows-toolchain.md`

**Interfaces:**
- Consumes: `build/native/Release/mc_dlss_resource_probe.exe` and its schema-version-1 JSON.
- Produces: a checked report at a caller-supplied path, defaulting to `outputs/d3d12-resource-probe-2026-07-10.json`.

- [ ] **Step 1: Write the verification script with explicit assertions**

The script runs the executable, parses JSON with `ConvertFrom-Json`, and throws unless all of these expressions are true:

```powershell
$report.schemaVersion -eq 1
$report.success -eq $true
$report.backend -eq "D3D12"
$report.adapter.software -eq $false
$report.deviceAvailable -eq $true
$report.commandContextAvailable -eq $true
$report.resources.inputColor -eq $true
$report.resources.depth -eq $true
$report.resources.motionVectors -eq $true
$report.resources.outputColor -eq $true
$report.commandSubmissionCompleted -eq $true
$report.inputResolution.width -ne $report.outputResolution.width
```

Use the executable's `--report` option rather than redirecting standard output. Resolve the executable and report to absolute paths and create only the report parent directory.

- [ ] **Step 2: Make native configuration run CTest**

After the Release build in `configure-native.ps1`, run:

```powershell
cmd.exe /c "call `"$vsDevCmd`" -arch=x64 -host_arch=x64 && ctest --test-dir `"$build`" -C Release --output-on-failure"
```

Exit immediately if CTest returns non-zero.

- [ ] **Step 3: Document the two commands and expected evidence**

Add to `docs/windows-toolchain.md`:

```powershell
./scripts/configure-native.ps1
./scripts/probe-d3d12-resources.ps1
```

Document that success produces `outputs/d3d12-resource-probe-2026-07-10.json` with backend `D3D12`, hardware adapter, four valid resources, and completed command submission.

- [ ] **Step 4: Run the reproducible probe**

Run:

```powershell
./scripts/configure-native.ps1
./scripts/probe-d3d12-resources.ps1
```

Expected: exit code `0`, RTX 4070 Laptop in the report, and every required Boolean true.

### Task 4: Regression verification and status handoff

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`

**Interfaces:**
- Consumes: native report artifact plus existing Java, JNI, loader, and Gradle verification commands.
- Produces: an evidence-backed Milestone 4A status and a precise Milestone 4B next step.

- [ ] **Step 1: Run all existing project checks**

Run:

```powershell
./scripts/verify-java.ps1
./scripts/verify-loader-metadata.ps1
./scripts/probe-native.ps1 ./build/native/Release/mc_dlss_native.dll
./scripts/gradle-local.ps1 -GradleArgs 'build','--console=plain','--no-daemon'
```

Expected: all self-tests pass, metadata verification passes, native probe reports `available=true`, and Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 2: Record only observed results**

Add the successful D3D12 adapter, device, command, resource, report path, native CTest, and regression results to `docs/local-status-2026-07-10.md`. Update `docs/backend-feasibility-spike-criteria.md` to distinguish the proven standalone D3D12 resource path from the still-blocked Minecraft OpenGL resource path.

- [ ] **Step 3: Perform final artifact checks**

Run:

```powershell
Get-Content -Raw outputs/d3d12-resource-probe-2026-07-10.json | ConvertFrom-Json | Format-List
Get-Item build/native/Release/mc_dlss_resource_probe.exe
Get-Item build/native/Release/mc_dlss_native.dll
```

Expected: parseable schema-version-1 JSON, both binaries present, and no claim that Minecraft itself is using D3D12 or DLSS.

## Plan Self-Review

- Spec coverage: Tasks 1-4 cover report schema, D3D12 device and command ownership, four native resources, command completion, runtime artifact, and regression checks.
- Scope: Streamline linking and Minecraft renderer replacement remain excluded and are named as Milestone 4B and later work.
- Type consistency: `ProbeSnapshot`, `validateProbeSnapshot`, `toJson`, and `runD3D12ResourceProbe` are defined once and consumed under the same names.
- Repository state: no commit commands are included because `mc-dlss-prototype` is not inside a Git repository.
