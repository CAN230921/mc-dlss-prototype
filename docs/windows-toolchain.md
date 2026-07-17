# Windows Toolchain Notes

## Required For Milestone 0

- Windows 10/11 x64.
- JDK 21 with `java` and `javac` available.
- Gradle, or a generated Gradle wrapper added later.
- CMake 3.24 or newer.
- Visual Studio 2022 Build Tools with the Desktop development with C++ workload.

## Required For Milestone 1

- NVIDIA RTX 40 series GPU.
- Recent NVIDIA Game Ready or Studio driver.
- NVIDIA Streamline repository or SDK checkout.
- DLSS runtime files as required by NVIDIA's current Streamline documentation and license terms.

## Local Verification Commands

```powershell
java -version
javac -version
gradle -version
cmake --version
```

```powershell
javac -d work/mc-dlss-core-test `
  mc-dlss-core/src/main/java/dev/mcdlss/core/*.java `
  mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/CoreContractSelfTest.java
java -cp work/mc-dlss-core-test dev.mcdlss.core.CoreContractSelfTest
```

```powershell
.\scripts\configure-native.ps1
```

The native build now runs its CTest suite. To create and validate the D3D12
resource-path evidence:

```powershell
.\scripts\configure-native.ps1
.\scripts\probe-d3d12-resources.ps1
```

Success creates `outputs/d3d12-resource-probe-2026-07-10.json`. The report must
name a hardware D3D12 adapter, show 1280 x 720 input and 1920 x 1080 output,
mark input color, depth, motion vectors, and output color valid, and confirm
that a submitted D3D12 command list completed.

To build and validate the OpenGL-D3D12 shared-resource path:

```powershell
.\scripts\configure-native.ps1
.\scripts\probe-gl-d3d12-interop.ps1
```

Success creates `outputs/gl-d3d12-interop-probe-2026-07-11.json`. The report
must identify the NVIDIA OpenGL renderer and D3D12 adapter, mark all four
external-memory/semaphore extensions available, confirm both imports and both
fence directions, and record `readbackMatched=true`.

To verify the same prerequisite in Minecraft's live OpenGL context, launch the
Fabric dev client and enter a world:

```powershell
.\tools\gradle-9.6.1\bin\gradle.bat :mc-dlss-fabric:runClient --console=plain --no-daemon --no-parallel
```

The top-left HUD must show `interop-ready: true`. The log line prefixed with
`[mc_dlss/fabric/live-gl]` must show all four extension fields true, identical
`glLuid` and `d3d12Luid` values, `adapterLuidMatched=true`, and
`interopPrerequisitesReady=true`. The 2026-07-11 evidence paths are
`work/run-logs/fabric-runClient-live-gl-2026-07-11.log` and
`outputs/fabric-live-gl-interop-overlay-2026-07-11.png`.

The same client now performs one Milestone 4D-B live shared-resource round trip
after the prerequisite check. Enter a world and require the HUD to show
`live-share: true`, `gl-write=true`, `d3d-submit=true`, `gl-wait=true`,
`readback=true`, and `released=true`. The log line prefixed with
`[mc_dlss/fabric/live-share]` must record every stage and `success=true`.
The 2026-07-11 evidence is at
`outputs/fabric-live-shared-resource-2026-07-11.log` and
`outputs/fabric-live-shared-resource-overlay-2026-07-11.png`.

Milestone 4D-C runs automatically on the next world frame after 4D-B. The HUD
must show `world-color: true`, `blit=true`, `content=true`,
`hash-match=true`, and `released=true`. The
`[mc_dlss/fabric/world-color]` log line must report identical `glHash` and
`d3d12Hash` values plus `success=true`. Evidence is at
`outputs/fabric-world-color-capture-2026-07-11.log` and
`outputs/fabric-world-color-capture-overlay-2026-07-11.png`.

Milestone 4D-D then creates two full-resolution persistent slots. The HUD must
reach `persistent-color: true`, `frames=120/120`, `slots=60/60`,
`retained=30/30`, and `released=true`. The final log line prefixed with
`[mc_dlss/fabric/persistent-color]` must report matching `glHash` and
`d3d12Hash`, at least one hash change, and `state=COMPLETE`. Evidence is at
`outputs/fabric-persistent-full-resolution-color-2026-07-11.log`.

For padded D3D12 readbacks, the mapped read range must end after the logical
bytes of the final row, not after a full padded row pitch. The native
`interop_test_pattern` regression covers this rule.

Milestone 4D-E starts after persistent color completes. The HUD/log must reach
`persistent-frame: true`, `frames=120/120`, `slots=60/60`,
`retained=30/30`, and `released=true`. Require matching GL/D3D color hashes,
matching GL/D3D depth hashes, all `width*height` depth samples finite and in
range, and nonzero scene/far sample counts. Evidence is at
`outputs/fabric-persistent-full-resolution-color-depth-2026-07-11.log`.

Milestone 4D-F upgrades that probe to a three-resource frame. Require the final
`[mc_dlss/fabric/persistent-motion]` line to report `success=true`,
`frames=120/120`, `slotAUses=60`, `slotBUses=60`, `retained=30/30`, matching
`glMotionHash` and `d3d12MotionHash`, `motionFinite=width*height`,
`motionOut=0`, `stationary=true`, `moving=true`, at least one reset, and
`resourcesReleased=true`. Evidence is at
`outputs/fabric-persistent-camera-motion-2026-07-11.log`.

For an unattended development run, pass
`-PmcDlssQuickPlayWorld="New World" -PmcDlssAutoMotionTest=true` to
`:mc-dlss-fabric:runClient`. These properties only affect the development run:
quick-play enters the named local world, while the motion test turns the camera
five degrees after frame 30 and restores it after frame 45.

To run Milestone 4D-G with the Streamline-enabled JNI library:

```powershell
.\gradlew.bat :mc-dlss-fabric:runClient --console=plain --no-daemon --no-parallel `
  -PmcDlssUseStreamlineNative=true `
  -PmcDlssLivePresentation=true `
  -PmcDlssQuickPlayWorld="New World" `
  -PmcDlssAutoMotionTest=true
```

The development run adds `build/native-streamline/Release` to both
`java.library.path` and the child process `PATH`; the latter is required for
Windows to resolve `sl.interposer.dll` as a transitive JNI dependency. Require
the final `[mc_dlss/fabric/live-dlss]` line to report `state=COMPLETE`,
`ready=true`, `size=569x320->854x480`, `frames=120/120`, `retained=30/30`,
`slotAUses=60`, `slotBUses=60`, matching GL/D3D12 hashes, finite FP16 output,
and `framebufferRestored=true`.

For the fallback acceptance, add `-PmcDlssLiveFailureFrame=2`. Require
`state=FAILED`, `failureStage=INJECTED_EVALUATE`, at least one fallback frame,
`framebufferRestored=true`, and `resourcesReleased=true`. Evidence paths are
`outputs/fabric-live-dlss-presentation-2026-07-11.log` and
`outputs/fabric-live-dlss-fallback-2026-07-11.log`.

Do not use the legacy standalone `streamline_dlss_probe` as a required final
regression on this machine: it calls `slShutdown`, which reproducibly stalls in
NVIDIA NGX. The Minecraft path calls `slFreeResources` and retains global
Streamline state until process exit.

To build and run the optional Streamline 2.12 DLSS evaluation:

```powershell
.\scripts\configure-native-streamline.ps1
.\scripts\probe-streamline-dlss.ps1
```

The build script defaults to the local SDK at
`../work/upstream/streamline-sdk-v2.12.0` relative to the project folder. Use
`-StreamlineRoot <path>` to select another complete 2.12 SDK. The probe writes
`outputs/streamline-dlss-probe-2026-07-11.json` and
`outputs/streamline-logs/sl.log`.

The Streamline target uses project GUID
`7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e`, custom engine version
`mc-dlss-prototype/0.1.0`, and numeric application ID `0`. It does not reuse
the NVIDIA sample application ID.

On the 2026-07-11 regression rerun, DLSS evaluation and resource release reached
the NVIDIA NGX shutdown stage, but `slShutdown` did not return within five
minutes. Treat the earlier successful 4B report as historical evidence and do
not treat a new run as passing unless the executable exits and writes a fresh
success report.

## Notes

This project starts with JNI and fixed probe strings. Streamline linking should wait until the official sample has been built and run on the target RTX machine.

Current environment status on 2026-07-10:

- `java` and `javac` are available at version `21.0.10`.
- `nvidia-smi` detects `NVIDIA GeForce RTX 4070 Laptop GPU` with driver `610.62`.
- Git is installed at `C:\Program Files\Git\cmd\git.exe`, version `2.55.0.windows.2`.
- Visual Studio Build Tools is installed at `C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools`, version `17.14.37411.7`.
- Visual Studio's bundled CMake is installed, version `3.31.6-msvc6`.
- Project-local Gradle `9.6.1` is installed under ignored `tools/`.
- Use `scripts/configure-native.ps1`, `scripts/probe-native.ps1`, and `scripts/gradle-local.ps1` from the project root.
