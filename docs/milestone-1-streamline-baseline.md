# Milestone 1 Streamline/DLSS Baseline

Record the official NVIDIA Streamline/DLSS sample result here after running it on the RTX 4070 Laptop.

## Test Machine

- Date: 2026-07-10
- Windows version: Microsoft Windows [Version 10.0.26200.8737]
- GPU name: NVIDIA GeForce RTX 4070 Laptop GPU
- NVIDIA driver version: 610.62
- GPU memory: 8188 MiB
- CUDA UMD version: 13.3, reported by `nvidia-smi`
- Laptop power mode: not recorded
- Java version: 21.0.10
- Git version: 2.55.0.windows.2
- CMake version: 3.31.6-msvc6 through Visual Studio Build Tools
- Visual Studio Build Tools version: 17.14.37411.7
- Project-local Gradle version: 9.6.1

## NVIDIA Components

- Streamline source or SDK version: `v2.12.0`
- Streamline source commit: `e8aaa6eaac968711fb62473d4ae8256dde20919b`
- Streamline SDK release zip: `streamline-sdk-v2.12.0.zip`
- DLSS package/runtime version: bundled with Streamline SDK `v2.12.0`; `_bin/nvngx_dlss.dll` copied from the release SDK
- Sample name and commit/tag: `NVIDIA-RTX/Streamline_Sample`, tag `v2.12.0`, commit `dd6e1803b97e5b3f218f5776d85f09e4c386c34f`
- Build configuration: CMake Visual Studio 17 2022 generator, x64, Release, `USE_SL=1`
- Render API tested: D3D12

## Result

- Streamline sample builds: yes
- Streamline sample launches: yes, exited with code `0`
- DLSS Super Resolution listed as supported: yes
- Selected render API: D3D12
- Error messages:
  - `Get-CimInstance Win32_OperatingSystem` returned access denied from this sandbox.
  - Standalone `Kitware.CMake` winget install failed twice with HTTP 504, but Visual Studio's bundled CMake works.
  - `gradlew.bat build` currently fails to download Gradle from `services.gradle.org` with HTTP 504; project-local Gradle works.
  - Streamline sample build produced MSVC warning `C4819` for `taskflow` headers on code page 936; build still completed.

## Evidence

- Screenshot path:
- Log path: `<workspace>\work\streamline-logs\sl-sample-d3d12.log`
- App log path: `<workspace>\work\upstream\Streamline_Sample\_bin\log.txt`
- Evidence summary path: `<workspace>\work\streamline-logs\dlss-evidence-summary.txt`
- Sample executable: `<workspace>\work\upstream\Streamline_Sample\_bin\StreamlineSample.exe`
- Notes:
  - `nvidia-smi` successfully detects the target RTX 4070 Laptop GPU.
  - Java/Javac self-tests run locally.
  - `mc-dlss-native` builds as a DLL through Visual Studio Build Tools.
  - Java can load the native DLL and round-trip `NativeProbeCli`.
  - `StreamlineSample.exe -d3d12 -DLSS_mode 3 -maxFrames 240 -logToFile -sllog` generated log evidence.
  - Evidence line `1098` in `_bin/log.txt`: `DLSS is supported on this system`.
  - Evidence line `1083` reports `eD3D12Supported=true` and `eVulkanSupported=true` for the first feature requirements block.
  - Evidence lines `1089` and `1090` report detected driver `610.62` and required driver `512.15` for DLSS.

## Decision

- Proceed to render path investigation: yes, for feasibility analysis only.
- Blocker to resolve first: inspect the exact Iris/Sodium 1.21.1 render path and determine whether compatible D3D/Vulkan native resources can exist. Do not implement Minecraft renderer hooks yet.
