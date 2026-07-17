# Minecraft Java DLSS Prototype Handoff

## Start Prompt For Windows Codex

Continue this as a fresh Windows project named `mc-dlss-prototype`. The goal is an experimental Minecraft Java 1.21.1 DLSS Super Resolution prototype for Windows + RTX 40 hardware, compatible in architecture with both Fabric/Iris/Sodium and NeoForge/Iris/Sodium. Do not start with Frame Generation. First prove whether DLSS SR can be integrated into the Iris/Sodium render path with the required resources and timing.

## Fixed Scope

- Minecraft: `1.21.1`
- Iris: `1.8.x`
- Sodium: `0.8.x`
- Loaders: Fabric and NeoForge
- OS: Windows 10/11 x64
- GPU target: RTX 40 series, first test machine is an RTX 4070 Laptop
- First DLSS feature: Super Resolution only
- Deferred DLSS feature: Frame Generation

The current modpack context is Airship Survival, a NeoForge 1.21.1 Packwiz pack using:

- `neoforge = 21.1.234`
- `iris-neoforge-1.8.14-beta.1+mc1.21.1`
- `sodium-neoforge-0.8.12-beta.2+mc1.21.1`

Do not build the prototype directly inside the modpack at first. Use a clean repository and bring results back later.

## Primary Goal

Build a research prototype that answers one question:

Can Minecraft Java 1.21.1 with Iris/Sodium provide or be modified to provide the render resources, frame timing, and native graphics API access needed to run NVIDIA DLSS Super Resolution correctly?

The first success state is not public release quality. It is a controlled RTX 40 Windows prototype that can enter a world, render the 3D scene at a lower internal resolution, run DLSS SR, and composite the UI at output resolution without crashing.

## Non-Goals

- No DLSS Frame Generation in the first phase.
- No Multi Frame Generation.
- No support for non-RTX GPUs.
- No Linux or macOS support.
- No compatibility promise for Minecraft versions outside 1.21.1.
- No public Modrinth/CurseForge release target until the render backend problem is solved.
- No attempt to implement DLSS inside a shaderpack.

## Architecture

Use one shared implementation with two loader adapters.

### `mc-dlss-core`

Shared Java module used by both loaders.

Responsibilities:

- DLSS enable/disable state.
- Quality mode selection: Quality, Balanced, Performance, Ultra Performance, DLAA if feasible later.
- Frame lifecycle model.
- Error codes and diagnostics.
- Capability detection surface exposed to the loader modules.
- Data contracts for frame resources, camera constants, jitter, reset state, render size, and output size.

This module must not depend on Fabric or NeoForge APIs.

### `mc-dlss-native`

C++ native layer exposed through JNI/JNA or a deliberately small JNI bridge.

Responsibilities:

- Load and initialize NVIDIA Streamline/DLSS.
- Validate that the host system supports the requested DLSS feature.
- Receive native graphics resources from the render backend.
- Tag color, depth, motion vector, and output resources.
- Set per-frame constants.
- Call DLSS SR evaluation at the chosen upscaling point.
- Return structured errors to Java.

This module is Windows-only for the prototype.

### `mc-dlss-fabric`

Fabric loader adapter.

Responsibilities:

- Loader entrypoint.
- Fabric config integration.
- Iris/Sodium integration hooks.
- Convert Fabric-side lifecycle events into `mc-dlss-core` calls.
- Provide debug commands or keybinds.

### `mc-dlss-neoforge`

NeoForge loader adapter.

Responsibilities:

- NeoForge mod entrypoint.
- NeoForge config integration.
- Iris/Sodium integration hooks.
- Convert NeoForge-side lifecycle events into `mc-dlss-core` calls.
- Match the Airship Survival test stack after the clean prototype works.

### `mc-dlss-debug`

May be a shared debug package or a small feature inside `mc-dlss-core`.

Responsibilities:

- In-game overlay or debug screen showing:
  - DLSS availability.
  - Selected mode.
  - Input resolution.
  - Output resolution.
  - Native initialization result.
  - Last Streamline/DLSS error.
  - Motion vector status.
  - Depth status.
  - Jitter/reset status.

## DLSS Input Requirements

DLSS SR needs more than the final color buffer. The prototype must account for:

- Low-resolution scene color buffer.
- Depth buffer.
- Motion vectors.
- Render resolution and output resolution.
- Current and previous frame camera matrices.
- Jitter offset in pixel space.
- Reset flags for discontinuities such as world load, dimension change, teleport, resize, shader reload, and camera mode changes.
- Exposure or auto-exposure choice.
- Correct resource states at the evaluation point.

Motion vectors are the hardest input. A camera-only motion vector buffer can prove part of the path, but it will ghost on moving entities, hand rendering, particles, animated blocks, water, vegetation, and shader-driven vertex movement. The first prototype may start with camera-derived motion vectors, but the design must leave room for per-object motion vectors.

## Critical Render Backend Risk

Minecraft Java, Iris, and Sodium are traditionally OpenGL-based. NVIDIA Streamline integration expects the host application to integrate through supported native graphics API paths such as D3D/Vulkan and to evaluate DLSS through native command buffers/resources.

This is the central risk. Do not spend weeks polishing loader code before answering how DLSS sees the graphics resources.

The Windows prototype should investigate these paths in order:

1. Confirm Streamline/DLSS sample runs on the RTX 4070 Laptop.
2. Confirm Java can load a small native DLL and round-trip capability/status calls.
3. Inspect the exact Iris/Sodium 1.21.1 render path and resource ownership.
4. Decide whether a Vulkan backend/fork, interop layer, or deeper renderer fork is required.
5. Only then wire a real Minecraft frame into DLSS SR.

If the render backend cannot provide compatible native resources, the project should stop and document why instead of pretending a shader-level solution exists.

## Data Flow Target

The desired steady-state frame flow:

1. Minecraft starts a frame.
2. DLSS module computes or receives the render size for the selected mode.
3. 3D world scene renders at internal resolution.
4. Depth and motion vector resources are available at internal resolution.
5. UI and HUD are not baked into the low-resolution scene color.
6. `mc-dlss-native` receives resource handles and per-frame constants.
7. DLSS SR evaluates into an output-resolution scene target.
8. Minecraft composites UI/HUD at output resolution.
9. The final frame is presented normally.

## Loader Compatibility Strategy

Treat Fabric and NeoForge as adapters, not separate implementations.

Shared code should own:

- DLSS state model.
- Capability checks.
- Config schema concepts.
- Native bridge interface.
- Debug data model.
- Frame resource contracts.

Loader-specific code should own:

- Entry points.
- Mod metadata.
- Config file binding.
- Event/hook registration.
- Loader-specific command/keybind registration.
- Any loader-specific Iris/Sodium access glue.

If a hook differs between Fabric and NeoForge, hide it behind a small adapter interface and keep the core untouched.

## First Milestones

### Milestone 0: Clean Repo And Toolchain

- Create `mc-dlss-prototype`.
- Use Gradle multi-project layout.
- Add modules for core, native, Fabric adapter, NeoForge adapter, and debug support.
- Add Windows build notes for Visual Studio, CMake, JDK, Gradle, and NVIDIA Streamline/DLSS SDK.

Acceptance:

- Empty Fabric and NeoForge test mods build.
- Native DLL can be built.
- Java can load the DLL and call a `getVersion()` or `probeSystem()` function.

### Milestone 1: NVIDIA Baseline

- Build/run the official Streamline/DLSS sample on the RTX 4070 Laptop.
- Capture driver version, Windows version, GPU name, Streamline version, and DLSS feature support.

Acceptance:

- A local note records that DLSS SR is supported on the test machine.
- Failure logs are captured if it does not run.

### Milestone 2: Render Path Investigation

- Inspect Iris/Sodium 1.21.1 render ownership.
- Identify where scene color, depth, and final composite targets live.
- Identify whether native resource handles compatible with Streamline can exist.
- Identify the best hook point after world render and before UI composition.

Acceptance:

- A short technical note says whether direct DLSS SR integration is feasible with the current backend.
- If not feasible, it recommends the least-bad renderer fork/backend path.

### Milestone 3: Minecraft Diagnostic Mod

- Add an in-game debug screen or overlay.
- Show Iris/Sodium detected, render size, output size, current world, frame counter, and native bridge state.

Acceptance:

- Fabric dev client shows the overlay.
- NeoForge dev client shows the overlay.

### Milestone 4: Resource Prototype

- Capture or generate scene color, depth, and preliminary motion vector resources.
- Confirm whether the native layer can see or consume them.

Acceptance:

- Debug HUD reports valid/invalid resource status.
- A screenshot/log proves the resource path being tested.

### Milestone 5: First DLSS SR Evaluation

- Run DLSS SR on a controlled scene if backend resources are compatible.
- Keep UI out of the low-res input if possible.

Acceptance:

- A world can render through DLSS SR at least once without crashing.
- Output resolution differs from internal resolution.
- Known visual artifacts are documented.

## Frame Generation Deferred Design

Frame Generation is intentionally out of the first phase.

It will require:

- HUD-less scene color.
- UI alpha/UI color handling.
- More complete motion vectors.
- Reflex integration.
- Present/swapchain timing.
- Frame pacing and latency validation.

Do not add Frame Generation until DLSS SR is stable enough to profile and inspect.

## Testing Expectations

Track three categories separately:

- Build tests: Gradle and native compilation.
- Runtime capability tests: native DLL loading, GPU/driver feature probing.
- In-game validation: Fabric dev client, NeoForge dev client, and later the Airship Survival modpack.

The first benchmark target should compare:

- Native resolution without DLSS.
- Lower internal resolution without DLSS, using nearest/bilinear fallback.
- Lower internal resolution with DLSS SR.

Capture FPS, GPU usage, CPU usage, frame time, and visual artifacts.

## Practical Risks

- Streamline may not be usable with the current OpenGL render path.
- Iris/Sodium internals may change across minor versions.
- Motion vectors may be incomplete and produce ghosting.
- UI composition may be mixed with scene rendering in inconvenient places.
- Shaderpacks may render effects that do not have useful motion vectors.
- Native DLL loading can be blocked by path, signing, antivirus, or architecture issues.
- NVIDIA SDK licensing/distribution rules may affect what can be shipped.

## Decision Rules

- If DLSS cannot access compatible native resources, document the blocker before writing more loader glue.
- If only one loader works initially, keep the shared interfaces intact and do not hard-code loader assumptions into `mc-dlss-core`.
- If motion vectors are poor, continue only if the output is useful enough for a research prototype.
- If Frame Generation becomes tempting early, defer it until SR has a stable evaluation path.

## Useful Source Links

- NVIDIA Streamline repository: https://github.com/NVIDIA-RTX/Streamline
- Streamline Programming Guide: https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuide.md
- DLSS Programming Guide: https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS.md
- DLSS Frame Generation Programming Guide: https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS_G.md
- NVIDIA DLSS developer page: https://developer.nvidia.com/rtx/dlss
- Iris ShaderDoc: https://github.com/IrisShaders/ShaderDoc

## Recommended Next Action On Windows

Create a clean `mc-dlss-prototype` folder, paste this file into the project root as `HANDOFF.md`, then ask Codex:

```text
Use HANDOFF.md as the project brief. First create a development plan for Milestone 0 and Milestone 1 only. Do not implement Minecraft renderer hooks yet. Inspect current NVIDIA Streamline requirements and the target Iris/Sodium source before deciding the first code changes.
```
