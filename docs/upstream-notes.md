# Upstream Notes

Checked on 2026-07-09.

## NVIDIA Streamline/DLSS

Sources:

- https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuide.md
- https://github.com/NVIDIA-RTX/Streamline/blob/main/docs/ProgrammingGuideDLSS.md

Observed requirements relevant to this prototype:

- Streamline documentation identifies version `2.12.0`.
- The baseline machine requirement is Windows 10 RS3 64-bit or newer.
- Streamline integration is framed around native DirectX/DXGI and Vulkan paths.
- `slInit` must happen very early, before DirectX/DXGI/Vulkan API calls that Streamline needs to interpose or observe.
- `sl::Preferences::featuresToLoad` must request the feature, such as `sl::kFeatureDLSS`; otherwise no feature is loaded by default.
- Feature support must be checked with Streamline APIs and can depend on OS, driver, adapter, device, and rendering API.
- DLSS SR requires tagged render resources: input color, output color, depth, motion vectors, and optional exposure.
- DLSS also needs per-frame constants, including camera/motion vector scaling and jitter/reset-related state.
- The evaluation call belongs at the upscaling point, and the host must restore command list/buffer state afterward.

Impact:

The prototype should continue to treat OpenGL resource ownership as the central technical risk. The Java/native JNI bridge is useful, but DLSS SR cannot be considered feasible until a compatible D3D/Vulkan resource path or renderer fork is identified.

## Iris And Sodium

Sources:

- https://github.com/IrisShaders/Iris
- https://github.com/CaffeineMC/sodium

Milestone 2 source pins:

- Iris branch `1.21.1`, commit `eb7afb99f747cc8ed5ee4072119539035d33cefd`.
  - Exact `1.8.14-beta.1+mc1.21.1` source tag was not visible from `git ls-remote --tags`; branch `1.21.1` was used as the closest official source.
- Sodium tag `mc1.21.1-0.8.12-beta.2`, commit `ec15afd7c85f850552577739eb30858faea1e592`.

Observed repository shape and render-path facts:

- Iris is a Minecraft shader mod with shared `common` code and loader-specific `fabric` and `neoforge` modules.
- Iris repository topics include OpenGL, and the project is explicitly tied to shader pipeline integration.
- Sodium is a Minecraft rendering engine replacement focused on performance.
- Iris' inspected pipeline uses Minecraft `RenderTarget`, `GlStateManager`, `RenderSystem`, LWJGL `org.lwjgl.opengl.*`, Iris `GlFramebuffer`, and OpenGL texture/framebuffer IDs.
- Sodium's inspected render device is `RenderDevice.INSTANCE = new GLRenderDevice()`, with command lists implemented as immediate OpenGL calls.
- No source-backed D3D/Vulkan backend or OpenGL-to-D3D/Vulkan interop path was found in the inspected Iris/Sodium sources.
- Iris exposes a conceptual world-render-to-GUI boundary through `finalizeLevelRendering()` and later GUI rendering, but the resources at that point are still OpenGL resources.
- A DLSS-ready motion-vector texture was not found. Iris exposes previous camera/matrix uniforms and a shaderpack scalar `velocity` uniform; this is not enough for robust DLSS SR motion vectors.

Impact:

Fabric and NeoForge should remain thin adapters in this prototype. Direct DLSS SR integration against the current OpenGL Iris/Sodium backend is blocked. The next useful work is a renderer-backend spike or fork that can provide D3D12/Vulkan resources and command sequencing before any real DLSS evaluation hook is attempted.

## Backend Diagnostic Contract

Added on 2026-07-10:

- `mc-dlss-core` records backend compatibility through `RenderBackendKind`, `DlssResourcePathStatus`, and `DlssBackendDiagnostic`.
- The current source-backed status is `DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked()`.
- `OPENGL` is deliberately marked as not supporting direct Streamline DLSS SR.
- `D3D12` and `VULKAN` are marked as possible Streamline-compatible backend kinds, but a diagnostic is only ready when native resources, command context, depth, motion vectors, and UI-after-upscale timing are all available.

Impact:

Future Fabric/NeoForge UI can show the blocker truthfully while backend work continues. The diagnostic does not reduce the renderer risk; it makes the risk visible and testable.
