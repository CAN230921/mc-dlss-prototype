# Milestone 4 D3D12 Resource Probe Design

Date: 2026-07-10

## Goal

Build a project-owned Windows D3D12 resource probe that proves the native layer can create and validate the resource set required by DLSS Super Resolution: low-resolution color, depth, motion vectors, high-resolution output color, a D3D12 device, and a command list.

This milestone does not replace Minecraft's OpenGL renderer and does not claim that Minecraft frames can enter DLSS. It creates the smallest reusable D3D12 foundation needed before Streamline evaluation or renderer-fork work.

## Fixed Scope

- Windows 10/11 x64 only.
- First test GPU: NVIDIA GeForce RTX 4070 Laptop GPU.
- Graphics API: D3D12.
- Input resolution: 1280 x 720.
- Output resolution: 1920 x 1080.
- DLSS feature scope: Super Resolution only.
- No Frame Generation, swapchain interception, Minecraft hooks, Fabric hooks, NeoForge hooks, or OpenGL interop.
- The probe must build with the installed Visual Studio Build Tools and bundled Visual Studio CMake.
- The probe must not require the ignored `work/upstream/Streamline` checkout to compile.

## Approaches Considered

### 1. Modify the NVIDIA Streamline sample

This is the shortest path to another DLSS evaluation, but the official sample already proves DLSS support on this machine. Changes would live in third-party code and would not establish a clean resource contract for this project.

### 2. Start a Minecraft D3D12 renderer fork immediately

This would attack the final blocker directly, but it combines device creation, renderer replacement, resource ownership, Minecraft lifecycle integration, motion vectors, and UI composition in one step. Failures would be difficult to isolate.

### 3. Add a project-owned D3D12 resource probe

This is the selected approach. It creates the exact resources and command context needed by later Streamline code while remaining independent of Minecraft and the NVIDIA sample. The resulting native types can be reused by a later Streamline evaluation target and, if viable, a renderer backend.

## Architecture

The existing `mc_dlss_native` JNI DLL remains unchanged in behavior. A new native support library owns D3D12 device and resource creation, and a console probe executable calls that library and emits one JSON report.

The support library is split into focused units:

- `D3D12Context` creates the DXGI factory, selects a hardware adapter, creates the D3D12 device, command queue, command allocator, command list, fence, and completion event.
- `DlssResourceSet` creates input color, depth, motion-vector, and output color textures with explicit formats, dimensions, and initial states.
- `ResourceProbeReport` converts adapter, device, command-context, and resource validation results into deterministic JSON.
- `mc_dlss_resource_probe` is a thin command-line entrypoint. It returns `0` only when every required capability and resource is valid.

No D3D12 pointer or handle crosses JNI in this milestone. Ownership stays in native code, which avoids inventing an unsafe Java handle contract before a Minecraft D3D12 backend exists.

## Resource Contract

The probe creates these committed D3D12 textures:

| Role | Size | Format | Required flags | Initial state |
| --- | --- | --- | --- | --- |
| Input color | 1280 x 720 | `DXGI_FORMAT_R16G16B16A16_FLOAT` | Render target | `D3D12_RESOURCE_STATE_RENDER_TARGET` |
| Depth | 1280 x 720 | `DXGI_FORMAT_D32_FLOAT` | Depth stencil | `D3D12_RESOURCE_STATE_DEPTH_WRITE` |
| Motion vectors | 1280 x 720 | `DXGI_FORMAT_R16G16_FLOAT` | Render target | `D3D12_RESOURCE_STATE_RENDER_TARGET` |
| Output color | 1920 x 1080 | `DXGI_FORMAT_R16G16B16A16_FLOAT` | Unordered access | `D3D12_RESOURCE_STATE_UNORDERED_ACCESS` |

The selected formats are suitable for a first DLSS SR experiment and keep color and motion data in floating-point textures. Streamline tagging is deliberately deferred until this contract is proven and tested.

## Data Flow

1. The executable starts and validates that the requested input and output dimensions are non-zero and different.
2. `D3D12Context` enumerates hardware adapters and rejects software adapters.
3. The context creates a D3D12 device and command objects.
4. `DlssResourceSet` creates all four committed textures.
5. The command list records resource clear operations where supported, closes, executes, and waits on a fence.
6. The validator checks every resource descriptor, native pointer, expected size, format, flags, and state.
7. The executable writes JSON to standard output and optionally to the path supplied by `--report <path>`.
8. The executable returns `0` for a complete resource path and a non-zero code for argument, adapter, device, command, resource, execution, or report-write failure.

## Report Contract

The report is a single JSON object with these stable top-level fields:

```json
{
  "schemaVersion": 1,
  "success": true,
  "backend": "D3D12",
  "adapter": {
    "name": "NVIDIA GeForce RTX 4070 Laptop GPU",
    "vendorId": 4318,
    "software": false
  },
  "deviceAvailable": true,
  "commandContextAvailable": true,
  "inputResolution": { "width": 1280, "height": 720 },
  "outputResolution": { "width": 1920, "height": 1080 },
  "resources": {
    "inputColor": true,
    "depth": true,
    "motionVectors": true,
    "outputColor": true
  },
  "commandSubmissionCompleted": true,
  "message": "D3D12 DLSS resource contract is available."
}
```

On failure, `success` is `false`, unavailable fields remain explicit, and `message` identifies the failed stage without including unstable pointer values.

## Error Handling

- Every HRESULT is checked at its call site.
- Native exceptions are caught at the executable boundary and converted into a non-zero exit code and failure JSON when report generation is still possible.
- Adapter selection fails clearly when no hardware D3D12 adapter is available.
- Partial initialization is released through RAII (`Microsoft::WRL::ComPtr` and a scoped Windows event handle).
- Report file failure does not suppress the JSON written to standard output.
- Debug-layer support is optional; absence of the debug layer is reported but does not fail the probe.

## Testing

Pure validation and JSON formatting are tested without requiring a GPU by constructing descriptor snapshots and checking exact outcomes. D3D12 creation and command submission are covered by a runtime smoke test on the RTX machine.

Verification consists of:

1. Native unit tests for dimension validation, descriptor validation, success JSON, and failure JSON.
2. A Release build through the existing Visual Studio CMake path.
3. A runtime probe that writes `outputs/d3d12-resource-probe-2026-07-10.json`.
4. JSON assertions that backend is `D3D12`, all four resources are true, command submission completed, adapter is not software, and input/output resolutions differ.
5. Existing Java, loader metadata, Gradle, and JNI probe checks to ensure the new target does not regress Milestones 0-3C.

## Acceptance Criteria

Milestone 4A is complete when:

- The project builds the JNI DLL, native tests, and `mc_dlss_resource_probe.exe` in Release.
- The executable selects the RTX 4070 Laptop rather than a software adapter.
- A D3D12 device and executable command context are valid.
- All four resources match the resource contract.
- A submitted command list completes through a fence.
- The committed report schema is covered by tests.
- A local JSON artifact records a successful run.
- Existing project verification remains green.

## Follow-On Milestone

Milestone 4B will optionally link Streamline through an explicit `STREAMLINE_ROOT`, tag the four resources, provide per-frame constants, and attempt one DLSS SR evaluation. That work begins only after this resource probe passes, so Streamline failures can be distinguished from D3D12 ownership and resource-lifecycle failures.
