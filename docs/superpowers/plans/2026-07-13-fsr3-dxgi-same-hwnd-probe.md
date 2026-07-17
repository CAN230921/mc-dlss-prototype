# FSR3 Same-HWND DXGI Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove or disprove that a GLFW-compatible WGL window can stop OpenGL presentation and be safely presented and resized through a D3D12 DXGI swapchain on the same `HWND`.

**Architecture:** Add a standalone Windows-native probe beside the existing OpenGL/D3D12 interop probe. The executable creates one WGL window, performs one OpenGL swap, creates a flip-model DXGI swapchain for that exact `HWND`, verifies known backbuffer colors before two successful presents separated by `ResizeBuffers`, and emits a validated JSON report. No Minecraft, JNI, FSR3 session, mixin, or packaged-mod code changes are allowed in this plan.

**Tech Stack:** C++17, Win32/WGL, OpenGL 1.1 bootstrap API, D3D12, DXGI 1.6, WRL `ComPtr`, CMake/CTest, Visual Studio 2022 Build Tools.

## Global Constraints

- Work only under `mc-dlss-native` plus the probe evidence document described below.
- Do not modify Minecraft Java code, mixins, JNI methods, `LiveFsr3Session`, or packaging tasks in this plan.
- The probe must use the same `HWND` for WGL and DXGI; a child or overlay window is not a passing result.
- The OpenGL context must remain alive while DXGI performs both presents.
- Treat `DXGI_STATUS_OCCLUDED`, device removal, a timeout, or failed resize as a failed feasibility result.
- Use `DXGI_SWAP_EFFECT_FLIP_DISCARD`, two buffers, `DXGI_FORMAT_R8G8B8A8_UNORM`, and no tearing flag for this first probe.
- Time out every GPU fence wait after 10 seconds.
- Keep the original FSR3 experimental JAR unchanged.
- The workspace currently has an empty `.git` directory and is not recognized as a repository. Run the listed commit steps only after Git metadata is repaired; otherwise record that the commit was unavailable and continue without inventing commit IDs.

---

## File Structure

- `mc-dlss-native/include/dxgi_window_present_probe_report.h`: plain result structure and validation/JSON declarations.
- `mc-dlss-native/src/dxgi_window_present_probe_report.cpp`: validation and stable JSON serialization only.
- `mc-dlss-native/tests/dxgi_window_present_probe_report_test.cpp`: CPU-only tests for success requirements and JSON escaping.
- `mc-dlss-native/include/dxgi_window_present_probe.h`: public `runDxgiWindowPresentProbe` declaration.
- `mc-dlss-native/src/dxgi_window_present_probe.cpp`: WGL window, D3D12 device/queue, swapchain, clear/readback, present, resize, and cleanup.
- `mc-dlss-native/src/dxgi_window_present_probe_main.cpp`: argument parsing, stdout/report-file output, and exit codes.
- `mc-dlss-native/CMakeLists.txt`: report test, probe executable, libraries, and GPU-labelled CTest registration.
- `work/dxgi-window-present-probe/report.json`: generated probe evidence; never hand-edit.
- `docs/superpowers/reports/2026-07-13-fsr3-dxgi-same-hwnd-result.md`: command, hardware identity, JSON result, and pass/fail architecture decision.

---

### Task 1: Probe Report Contract

**Files:**
- Create: `mc-dlss-native/include/dxgi_window_present_probe_report.h`
- Create: `mc-dlss-native/src/dxgi_window_present_probe_report.cpp`
- Create: `mc-dlss-native/tests/dxgi_window_present_probe_report_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `mc_dlss::DxgiWindowPresentProbeSnapshot`
- Produces: `bool mc_dlss::validateDxgiWindowPresentProbeSnapshot(const DxgiWindowPresentProbeSnapshot&) noexcept`
- Produces: `std::string mc_dlss::toJson(const DxgiWindowPresentProbeSnapshot&)`

- [ ] **Step 1: Write the failing CPU-only report test**

Create `tests/dxgi_window_present_probe_report_test.cpp` with a `completeSnapshot()` helper that sets these exact fields:

```cpp
mc_dlss::DxgiWindowPresentProbeSnapshot snapshot{};
snapshot.adapterName = "NVIDIA GeForce RTX 4070 Laptop GPU";
snapshot.openGlVendor = "NVIDIA Corporation";
snapshot.openGlRenderer = "NVIDIA GeForce RTX 4070 Laptop GPU/PCIe/SSE2";
snapshot.sameWindowHandle = true;
snapshot.openGlContextCurrent = true;
snapshot.openGlSwapCompleted = true;
snapshot.dxgiSwapchainCreated = true;
snapshot.firstBackBufferMatched = true;
snapshot.firstPresentSucceeded = true;
snapshot.resizeSucceeded = true;
snapshot.secondBackBufferMatched = true;
snapshot.secondPresentSucceeded = true;
snapshot.openGlContextStillCurrent = true;
snapshot.deviceRemovedReason = 0;
snapshot.message = "Same-HWND DXGI presentation completed.";
```

Assert that the complete snapshot validates, JSON contains `"schemaVersion":1`, `"success":true`, and all boolean field names, then independently set `sameWindowHandle`, `firstPresentSucceeded`, `resizeSucceeded`, `secondPresentSucceeded`, and `openGlContextStillCurrent` false and assert each snapshot fails. Set the message to `Present \"failed\" at C:\\dxgi.\nStopped.` and assert JSON escaping.

- [ ] **Step 2: Register and run the test to verify RED**

Add a CMake executable named `dxgi_window_present_probe_report_test` from the test and report source, include `include`, and register CTest name `dxgi_window_present_probe_report`.

Run:

```powershell
cmd /d /c "set PATH=&& ""C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"" --build build\native-fsr3 --config Release --target dxgi_window_present_probe_report_test"
```

Expected: compilation fails because `dxgi_window_present_probe_report.h` and its declarations do not exist.

- [ ] **Step 3: Add the minimal report contract**

Define the snapshot with the exact fields used by the test. Implement validation as a conjunction of non-empty adapter/OpenGL identity strings, every required boolean, and `deviceRemovedReason == 0`. Implement JSON serialization with a local `jsonBoolean` and `escapeJson`, following `gl_d3d12_interop_report.cpp`.

- [ ] **Step 4: Build and verify GREEN**

Run the build command from Step 2, then:

```powershell
cmd /d /c "set PATH=&& ""C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\ctest.exe"" --test-dir build\native-fsr3 -C Release -R ^dxgi_window_present_probe_report$ --output-on-failure"
```

Expected: `1/1` passed.

- [ ] **Step 5: Commit the report contract when Git is available**

```powershell
git add mc-dlss-native/include/dxgi_window_present_probe_report.h mc-dlss-native/src/dxgi_window_present_probe_report.cpp mc-dlss-native/tests/dxgi_window_present_probe_report_test.cpp mc-dlss-native/CMakeLists.txt
git commit -m "test: define same-window DXGI probe contract"
```

Expected when Git remains unavailable: record `not a git repository` in the execution notes and do not initialize or repair Git as part of this feature.

---

### Task 2: Same-HWND WGL and DXGI Presenter

**Files:**
- Create: `mc-dlss-native/include/dxgi_window_present_probe.h`
- Create: `mc-dlss-native/src/dxgi_window_present_probe.cpp`
- Create: `mc-dlss-native/src/dxgi_window_present_probe_main.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Consumes: `DxgiWindowPresentProbeSnapshot` from Task 1.
- Produces: `DxgiWindowPresentProbeSnapshot runDxgiWindowPresentProbe(std::uint32_t width, std::uint32_t height, std::uint32_t resizedWidth, std::uint32_t resizedHeight) noexcept`.
- Produces executable: `mc_dlss_dxgi_window_present_probe.exe`.

- [ ] **Step 1: Write the executable shell before the implementation**

Declare `runDxgiWindowPresentProbe` in the new header. In `dxgi_window_present_probe_main.cpp`, parse `--width`, `--height`, `--resized-width`, `--resized-height`, and `--report`; default to `320`, `180`, `640`, and `360`. Reject zero or non-numeric dimensions with exit code `2`. Print JSON, optionally write it to the report path, return `0` only when validation passes, `3` for a failed probe, and `4` for report-file I/O failure.

Temporarily implement `runDxgiWindowPresentProbe` in the new `.cpp` as:

```cpp
mc_dlss::DxgiWindowPresentProbeSnapshot mc_dlss::runDxgiWindowPresentProbe(
    std::uint32_t, std::uint32_t, std::uint32_t, std::uint32_t) noexcept {
    DxgiWindowPresentProbeSnapshot snapshot{};
    snapshot.message = "DXGI window presenter is not implemented.";
    return snapshot;
}
```

- [ ] **Step 2: Register, build, and verify the shell fails for the right reason**

Add `mc_dlss_dxgi_window_present_probe` with the report and probe sources. Link `d3d12`, `dxgi`, `dxguid`, `opengl32`, `gdi32`, and `user32`. Build it and run:

```powershell
.\build\native-fsr3\Release\mc_dlss_dxgi_window_present_probe.exe --report work\dxgi-window-present-probe\report.json
```

Expected: exit code `3`, JSON has `"success":false`, and message is `DXGI window presenter is not implemented.`

- [ ] **Step 3: Implement scoped Win32/WGL ownership**

Add private RAII classes in `dxgi_window_present_probe.cpp`:

- `ScopedHandle` closes non-null handles.
- `ProbeWglWindow` registers a `CS_OWNDC` class, creates a `WS_OVERLAPPEDWINDOW` window, obtains its `HDC`, chooses and sets a double-buffered RGBA pixel format, creates and makes current an `HGLRC`, and destroys resources in reverse order.
- `ProbeWglWindow::hwnd()` returns the exact `HWND` later passed to DXGI.
- `ProbeWglWindow::show(width, height)` shows without activation and pumps pending window messages.
- `ProbeWglWindow::resize(width, height)` uses `SetWindowPos` and pumps messages.

After creation, capture `GL_VENDOR` and `GL_RENDERER`, clear the default framebuffer to blue, call Win32 `SwapBuffers(hdc)`, and set `openGlContextCurrent` and `openGlSwapCompleted` only after success.

- [ ] **Step 4: Implement D3D12 adapter, device, queue, and swapchain creation**

Create `IDXGIFactory6`, enumerate `DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE`, skip software adapters, and select the first adapter supporting `D3D_FEATURE_LEVEL_12_0`. Record the adapter description in UTF-8.

Create an `ID3D12Device`, direct command queue, command allocator, command list, RTV descriptor heap for two buffers, and fence/event. Build `DXGI_SWAP_CHAIN_DESC1` with:

```cpp
desc.Width = width;
desc.Height = height;
desc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
desc.SampleDesc.Count = 1;
desc.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
desc.BufferCount = 2;
desc.Scaling = DXGI_SCALING_STRETCH;
desc.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
desc.AlphaMode = DXGI_ALPHA_MODE_IGNORE;
```

Call `CreateSwapChainForHwnd(queue.Get(), window.hwnd(), ...)`, query `IDXGISwapChain4`, and call `MakeWindowAssociation(hwnd, DXGI_MWA_NO_ALT_ENTER)`. Set `sameWindowHandle` only after `GetHwnd` returns the identical handle.

- [ ] **Step 5: Implement known-color rendering and GPU verification**

Add a private `renderAndVerify` helper that accepts an RGBA float color. It transitions the current swapchain backbuffer from `PRESENT` to `RENDER_TARGET`, clears it, transitions to `COPY_SOURCE`, copies it to a readback buffer using `GetCopyableFootprints`, transitions it back to `PRESENT`, executes the command list, signals the fence, and waits at most 10 seconds.

Map the readback and verify every visible pixel is within one byte of the expected UNORM RGBA value. Ignore row-pitch padding. Return false on any mismatch. Reset the allocator and command list before every call.

- [ ] **Step 6: Present, resize, and prove WGL remains current**

Render and verify solid red, set `firstBackBufferMatched`, then call `swapchain->Present(0, 0)`. Accept only `S_OK`; `DXGI_STATUS_OCCLUDED` is failure. Set `firstPresentSucceeded` after success.

Wait for the D3D12 queue, release backbuffer references, resize the Win32 window, call `ResizeBuffers(2, resizedWidth, resizedHeight, DXGI_FORMAT_R8G8B8A8_UNORM, 0)`, recreate RTVs/readback resources, and set `resizeSucceeded`.

Render and verify solid green, present again, set the second-presentation fields, and finally require:

```cpp
wglGetCurrentContext() == window.renderingContext()
wglGetCurrentDC() == window.deviceContext()
```

Store `device->GetDeviceRemovedReason()` in `deviceRemovedReason`. On success use message `Same-HWND DXGI presentation completed.` Catch exceptions into `message` without throwing across the public `noexcept` boundary.

- [ ] **Step 7: Build and run the real probe**

Register CTest `dxgi_window_present_probe_real` with the executable and arguments `--width 320 --height 180 --resized-width 640 --resized-height 360`. Apply labels `gpu;windows;dxgi`.

Build and run:

```powershell
cmd /d /c "set PATH=&& ""C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"" --build build\native-fsr3 --config Release --target mc_dlss_dxgi_window_present_probe"
.\build\native-fsr3\Release\mc_dlss_dxgi_window_present_probe.exe --width 320 --height 180 --resized-width 640 --resized-height 360 --report work\dxgi-window-present-probe\report.json
```

Expected for feasibility: exit code `0`, `success=true`, both presents true, resize true, same window true, WGL context still current, and device removed reason `0`.

- [ ] **Step 8: Commit the native probe when Git is available**

```powershell
git add mc-dlss-native/include/dxgi_window_present_probe.h mc-dlss-native/src/dxgi_window_present_probe.cpp mc-dlss-native/src/dxgi_window_present_probe_main.cpp mc-dlss-native/CMakeLists.txt
git commit -m "feat: probe same-window DXGI presentation"
```

---

### Task 3: Regression and Stress Checks

**Files:**
- Modify only if a probe defect is found: files created in Tasks 1 and 2.
- Generate: `work/dxgi-window-present-probe/report.json`

**Interfaces:**
- Consumes: `mc_dlss_dxgi_window_present_probe.exe`.
- Produces: repeatable CTest and stress-run evidence.

- [ ] **Step 1: Run focused report and real-probe tests**

```powershell
cmd /d /c "set PATH=&& ""C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\ctest.exe"" --test-dir build\native-fsr3 -C Release -R ""dxgi_window_present_probe"" --output-on-failure"
```

Expected: `2/2` passed.

- [ ] **Step 2: Run 25 independent process iterations**

Run the executable 25 times from PowerShell, stop on the first non-zero exit, and preserve each JSON report as `report-01.json` through `report-25.json`. Assert all reports contain `"success":true` and no process remains after each iteration.

- [ ] **Step 3: Run existing FSR3 and JNI tests**

```powershell
cmd /d /c "set PATH=&& ""C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\ctest.exe"" --test-dir build\native-fsr3 -C Release -R ""fsr3|jni_exports|gl_d3d12_interop_report"" --output-on-failure"
```

Expected: every selected test passes. This proves the probe did not regress existing interop or FSR3 contracts.

- [ ] **Step 4: Inspect process and device cleanup**

After the stress loop, verify there is no `mc_dlss_dxgi_window_present_probe` process. Compare dedicated GPU memory before and after the loop; growth after the last process exits must be within normal measurement noise because all resources are process-owned and released.

- [ ] **Step 5: Commit stress fixes when Git is available**

If Steps 1 through 4 required code fixes, commit only those fixes and their test changes with:

```powershell
git commit -am "fix: stabilize same-window DXGI probe"
```

If no code changed, do not create an empty commit.

---

### Task 4: Record the Architecture Decision

**Files:**
- Create: `docs/superpowers/reports/2026-07-13-fsr3-dxgi-same-hwnd-result.md`
- Read: `work/dxgi-window-present-probe/report.json`

**Interfaces:**
- Consumes: validated JSON and stress results.
- Produces: an explicit `PASS` or `FAIL` gate for the later Minecraft DXGI presenter plan.

- [ ] **Step 1: Write the evidence report**

Include:

- Exact build and test commands.
- GPU adapter, OpenGL vendor/renderer, initial and resized dimensions.
- The complete single-run JSON in a fenced `json` block.
- Focused CTest count and 25-run stress count.
- Cleanup observation.
- One decision line using exactly one of:
  - `Decision: PASS - proceed with same-HWND Minecraft DXGI takeover.`
  - `Decision: FAIL - do not suppress Minecraft OpenGL presentation; design the child presentation window fallback.`

- [ ] **Step 2: Enforce the gate**

If any required snapshot field or stress iteration failed, select `FAIL` even if most presents succeeded. Do not begin Minecraft integration in this plan.

- [ ] **Step 3: Commit the evidence report when Git is available**

```powershell
git add docs/superpowers/reports/2026-07-13-fsr3-dxgi-same-hwnd-result.md
git commit -m "docs: record same-window DXGI feasibility"
```

Expected: the report, not intuition, determines the next architecture plan.
