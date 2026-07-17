# Milestone 4B Streamline DLSS Evaluation Design

Date: 2026-07-11

## Goal

Build a project-owned, optional Streamline 2.12 D3D12 probe that performs one controlled DLSS Super Resolution evaluation using the native resources proven by Milestone 4A.

Success means Streamline initializes, reports DLSS support for the selected RTX adapter, accepts the device, options, constants, and four required resource tags, returns success from `slEvaluateFeature`, and the submitted D3D12 command list completes on the GPU.

## Scope

- Windows 10/11 x64 and D3D12 only.
- NVIDIA Streamline SDK version 2.12.0.
- DLSS Super Resolution only; no Frame Generation, Ray Reconstruction, Reflex, NIS, or OTA updates.
- Output resolution is 1920 x 1080.
- DLSS mode is Quality; the input resolution comes from `slDLSSGetOptimalSettings` and is expected to be 1280 x 720 on the test system.
- One viewport, one frame token, one evaluation, and one GPU completion wait.
- A fixed flat test frame is sufficient. Image-quality validation and Minecraft integration are outside this milestone.
- No Fabric, NeoForge, Iris, Sodium, OpenGL interop, swapchain, presentation, or game-window work.

## Application Identity

The probe does not reuse NVIDIA's sample application ID.

It uses the Project ID form supported by Streamline/NGX:

- `projectId`: `7a7d47b1-8f6f-4bf4-b9d1-cc41ef57f56e`
- `engine`: `sl::EngineType::eCustom`
- `engineVersion`: `mc-dlss-prototype/0.1.0`
- `applicationId`: `0`

This identity is for the local research prototype. Shipping still requires a separate review of NVIDIA SDK registration, licensing, and redistribution terms.

## Approaches Considered

### 1. Re-run and parse the official Streamline sample

The official sample already proves that DLSS is supported on this machine, but it does not prove that this project's D3D12 resources can be tagged and evaluated.

### 2. Add Streamline directly to the Milestone 4A executable

This minimizes target count but makes the baseline native build require NVIDIA headers, libraries, and runtime DLLs. It would weaken the existing SDK-independent diagnostic path.

### 3. Add an optional Streamline executable over reusable D3D12 support

This is the selected approach. Shared D3D12 context code serves both probes, while only the new target depends on Streamline. A machine without the SDK can continue building JNI and Milestone 4A unchanged.

## Build Architecture

The native project gains a reusable Windows-only `mc_dlss_d3d12_probe_support` static library. It owns adapter selection, device and command objects, the four textures, descriptor heaps, clear commands, fence submission, and resource metadata.

Two executables consume it:

- `mc_dlss_resource_probe` remains the SDK-independent Milestone 4A check and links system `d3d12` and `dxgi`.
- `mc_dlss_streamline_probe` is created only when `STREAMLINE_ROOT` points to a valid Streamline 2.12 SDK. It links `sl.interposer.lib` and `dxguid`; D3D12 and DXGI entry points come through the Streamline interposer.

CMake validates these SDK inputs before creating the optional target:

- `include/sl.h`
- `lib/x64/sl.interposer.lib`
- `bin/x64/sl.interposer.dll`
- `bin/x64/sl.common.dll`
- `bin/x64/sl.pcl.dll`
- `bin/x64/sl.dlss.dll`
- `bin/x64/nvngx_dlss.dll`

The five DLLs are copied next to `mc_dlss_streamline_probe.exe` after build. They are not copied into source control.

## Runtime Architecture

`StreamlineSession` owns Streamline initialization and shutdown. Its constructor configures only `sl::kFeatureDLSS`, disables OTA flags, enables frame-based resource tagging, supplies the project identity and plugin/log paths, then calls `slInit` before any D3D12 or DXGI function.

`D3D12ProbeContext` is created after Streamline initialization. It exposes native pointers for the device, command list, input color, depth, motion vectors, and output color, plus the selected adapter LUID and current resource states.

`StreamlineDlssEvaluator` performs the feature-specific sequence:

1. Call `slSetD3DDevice` with the D3D12 device.
2. Call `slIsFeatureSupported` with the selected adapter LUID.
3. Call `slDLSSGetOptimalSettings` for Quality mode at 1920 x 1080.
4. Confirm the resource input size matches the returned optimal render size.
5. Obtain frame token index `0` and use viewport handle `0` everywhere.
6. Call `slDLSSSetOptions` with HDR color, automatic exposure, no alpha upscaling, and the recommended sharpness.
7. Provide deterministic row-major camera constants, zero jitter, pixel-space motion-vector scaling, reset true, and a perspective camera description.
8. Tag input color, output color, depth, and motion vectors with exact extents and D3D12 resource states.
9. Call `slEvaluateFeature(sl::kFeatureDLSS, ...)` on the open graphics command list.
10. Close, submit, and wait for the command list through the existing fence path.
11. Call `slFreeResources`, then `slShutdown` before D3D12 objects are destroyed.

## Resource Contract

| Role | Size | Format | State at tagging |
| --- | --- | --- | --- |
| Scaling input color | DLSS optimal render size | `DXGI_FORMAT_R16G16B16A16_FLOAT` | `D3D12_RESOURCE_STATE_RENDER_TARGET` |
| Depth | DLSS optimal render size | `DXGI_FORMAT_D32_FLOAT` | `D3D12_RESOURCE_STATE_DEPTH_WRITE` |
| Motion vectors | DLSS optimal render size | `DXGI_FORMAT_R16G16_FLOAT` | `D3D12_RESOURCE_STATE_RENDER_TARGET` |
| Scaling output color | 1920 x 1080 | `DXGI_FORMAT_R16G16B16A16_FLOAT` | `D3D12_RESOURCE_STATE_UNORDERED_ACCESS` |

Streamline receives the native `ID3D12Resource*`, the declared current state, and matching input/output extents. Resource lifecycles are `eValidUntilEvaluate` because the probe waits for the evaluation before releasing anything.

## Constants

The controlled frame uses identity current/previous transforms, zero jitter, zero motion vectors, and `reset=eTrue`. Camera vectors are right `(1,0,0)`, up `(0,1,0)`, and forward `(0,0,1)`. Near plane is `0.1`, far plane is `1000.0`, field of view is `1.0471976` radians, and aspect ratio is `1920/1080`.

Motion vectors are treated as pixel-space values, so `mvecScale` is `(1/renderWidth, 1/renderHeight)`. Depth is not inverted, camera motion is included, vectors are two-dimensional, and projection is perspective.

## Report Contract

The probe writes `outputs/streamline-dlss-probe-2026-07-11.json` with schema version 1. Stable fields include:

- `success`
- `streamlineVersion`
- `projectId`
- `adapterName`
- `dlssSupported`
- `optimalRenderResolution`
- `outputResolution`
- `streamlineInitialized`
- `d3dDeviceAccepted`
- `optionsAccepted`
- `constantsAccepted`
- `resourcesTagged`
- `evaluationCalled`
- `streamlineDlssEvaluationSucceeded`
- `commandSubmissionCompleted`
- `resourcesFreed`
- `streamlineShutdownSucceeded`
- `lastResult`
- `message`

The report never includes pointer values. `success` is true only when every required stage, evaluation, GPU completion, and shutdown succeeds.

## Error Handling

- SDK absence disables the optional target without breaking the default native build.
- A supplied but incomplete `STREAMLINE_ROOT` fails CMake configuration with the exact missing path.
- Every Streamline result is recorded by stage and converted to a stable symbolic result string where known.
- Streamline shutdown is attempted whenever initialization succeeded, including failure paths.
- D3D12 command submission is completed before `slFreeResources`.
- The executable returns non-zero when any required stage fails, but still writes a failure report when possible.
- Streamline logs are written under `outputs/streamline-logs` for diagnosis.

## Testing

Pure tests cover report validation, JSON formatting, project identity, fixed viewport/frame values, and failure-stage propagation without loading Streamline.

Runtime verification covers:

1. Default native build without `STREAMLINE_ROOT`; JNI and Milestone 4A still pass.
2. Streamline-enabled native build with the local SDK.
3. A runtime probe using signed production DLLs.
4. JSON assertions for all required success fields.
5. Streamline log assertions that the DLSS plugin loaded and evaluation did not report a Streamline or NGX error.
6. Existing Java, loader metadata, JNI, and full Gradle regression checks.

## Acceptance Criteria

Milestone 4B is complete when:

- The default SDK-independent native build remains green.
- The optional Streamline target builds from an explicit local SDK root.
- Streamline initializes before D3D12 and accepts the selected device.
- DLSS is supported on the selected RTX 4070 Laptop adapter.
- Optimal render settings are obtained and match the created input resources.
- Options, constants, and all four resource tags are accepted.
- `slEvaluateFeature` returns `sl::Result::eOk`.
- The evaluated command list completes on the GPU.
- Resources are freed and Streamline shuts down successfully.
- The schema-version-1 report records `streamlineDlssEvaluationSucceeded=true`.
- Existing Milestones 0-4A remain green.

## Non-Claim

Passing this milestone proves one controlled DLSS SR evaluation over project-owned D3D12 resources. It does not prove that Minecraft, Iris, or Sodium can supply those resources, and it does not make the current OpenGL client DLSS-ready.
