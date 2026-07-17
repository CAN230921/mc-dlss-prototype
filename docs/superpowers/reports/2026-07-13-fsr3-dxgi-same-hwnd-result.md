# FSR3 Same-HWND DXGI Probe Result

## Environment

- Adapter: NVIDIA GeForce RTX 4070 Laptop GPU
- OpenGL vendor: NVIDIA Corporation
- OpenGL renderer: NVIDIA GeForce RTX 4070 Laptop GPU/PCIe/SSE2
- Initial client/backbuffer dimensions: 320 x 180
- Resized client/backbuffer dimensions: 640 x 360
- Swapchain: D3D12, two buffers, `DXGI_FORMAT_R8G8B8A8_UNORM`, `DXGI_SWAP_EFFECT_FLIP_DISCARD`, no tearing flag

## Commands

```powershell
cmd /d /c "set PATH=&& ""C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"" --build build\native-fsr3 --config Release --target mc_dlss_dxgi_window_present_probe dxgi_window_present_probe_report_test"

.\build\native-fsr3\Release\mc_dlss_dxgi_window_present_probe.exe --width 320 --height 180 --resized-width 640 --resized-height 360 --report work\dxgi-window-present-probe\report.json

cmd /d /c "set PATH=&& ""C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\ctest.exe"" --test-dir build\native-fsr3 -C Release -R dxgi_window_present_probe --output-on-failure"
```

## Probe JSON

```json
{"schemaVersion":1,"success":true,"adapterName":"NVIDIA GeForce RTX 4070 Laptop GPU","openGlVendor":"NVIDIA Corporation","openGlRenderer":"NVIDIA GeForce RTX 4070 Laptop GPU/PCIe/SSE2","sameWindowHandle":true,"openGlContextCurrent":true,"openGlSwapCompleted":true,"dxgiSwapchainCreated":true,"firstBackBufferMatched":true,"firstPresentSucceeded":true,"resizeSucceeded":true,"secondBackBufferMatched":true,"secondPresentSucceeded":true,"openGlContextStillCurrent":true,"deviceRemovedReason":0,"message":"Same-HWND DXGI presentation completed."}
```

## Evidence

- Release build: passed.
- CPU report contract: passed.
- Post-review same-HWND user-session probe: passed with exit code `0`.
- Post-review readiness fix: compiled successfully with `/W4 /permissive-`.
- Same HWND, first/second Present, resize, red/green GPU readback, WGL-current check, and device status: passed.
- 25-run stress: `25/25` independent processes passed.
- Stress JSON validation: `25/25` reports contained every required true milestone and `deviceRemovedReason:0`.
- Existing FSR3/JNI/interop regressions: `9/9` passed.
- Cleanup: no probe process or probe GPU process-counter instance remained; all stress error logs were empty.

The automation host cannot launch the unsigned real-probe child because of Windows Code Integrity policy, so the exact executable was run directly in the user's command session. The security policy was not disabled or bypassed.

Decision: PASS - proceed with same-HWND Minecraft DXGI takeover.
