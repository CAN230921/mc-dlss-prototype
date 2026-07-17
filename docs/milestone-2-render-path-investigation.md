# Milestone 2 Render Path Investigation

Date: 2026-07-10

## Decision

Direct DLSS Super Resolution integration is blocked in the current Iris/Sodium Minecraft 1.21.1 render backend.

The target stack exposes an OpenGL/LWJGL render path built around Minecraft `RenderTarget`, OpenGL texture IDs, and OpenGL framebuffer IDs. NVIDIA Streamline/DLSS SR requires a Direct3D or Vulkan device, command buffer/list, and tagged native D3D/Vulkan resources for color, output, depth, and motion vectors. The investigated Iris/Sodium sources do not expose a source-backed D3D/Vulkan resource path or an OpenGL-to-D3D/Vulkan interop bridge.

The least-bad next path is a renderer-backend spike or fork that can produce D3D12 or Vulkan resources first. Loader glue and shaderpack-level experiments should remain secondary until a native resource contract exists.

## Source Pins

Primary sources inspected locally:

- Iris official repository: `work/upstream/Iris`
  - Ref used: branch `1.21.1`
  - Commit: `eb7afb99f747cc8ed5ee4072119539035d33cefd`
  - Note: no Iris tag matching `1.8.14`, `1.8.x`, or `1.21.1` was visible from `git ls-remote --tags`; `1.21.1` branch head was used as the closest official source.
- Sodium official repository: `work/upstream/Sodium`
  - Ref used: tag `mc1.21.1-0.8.12-beta.2`
  - Commit: `ec15afd7c85f850552577739eb30858faea1e592`
- NVIDIA Streamline official repository: `work/upstream/Streamline`
  - Ref used: tag `v2.12.0`
  - Commit: `e8aaa6eaac968711fb62473d4ae8256dde20919b`

## Streamline/DLSS Requirements Checked

Streamline integration is framed around DirectX/DXGI or Vulkan. The general guide says the app must integrate through D3D/DXGI or Vulkan loading/interposition paths and call `slInit` before DirectX/DXGI/Vulkan API calls (`work/upstream/Streamline/docs/ProgrammingGuide.md:28`, `:31`, `:32`, `:115`, `:116`, `:406`). It also says `featuresToLoad` must include `sl::kFeatureDLSS` or no features are loaded (`work/upstream/Streamline/docs/ProgrammingGuide.md:231`, `:235`).

The Streamline API requires a real D3D/Vulkan device and command buffer:

- `slSetD3DDevice` must be called immediately after the main D3D device is created (`work/upstream/Streamline/include/sl_core_api.h:318`, `:325`, `:326`).
- `slSetTagForFrame`, `slSetConstants`, and `slEvaluateFeature` require a DX/VK device and a command buffer/list compatible with the feature device (`work/upstream/Streamline/include/sl_core_api.h:148`, `:151`, `:184`, `:253`, `:256`, `:261`, `:262`).
- Feature flags are expressed as D3D11, D3D12, and Vulkan support, not OpenGL support (`work/upstream/Streamline/include/sl_core_types.h:617`, `:618`, `:619`).

DLSS SR specifically requires:

- Render resolution chosen from DLSS output size and mode (`work/upstream/Streamline/docs/ProgrammingGuideDLSS.md:105`, `:115`, `:116`, `:123`).
- Depth, motion vectors, render-resolution input color, and final-resolution output color (`work/upstream/Streamline/docs/ProgrammingGuideDLSS.md:128`, `:130`).
- Resource tags for scaling input color, scaling output color, depth, and motion vectors (`work/upstream/Streamline/include/sl_core_types.h:65`, `:67`, `:71`, `:73`; `work/upstream/Streamline/docs/ProgrammingGuideDLSS.md:143`, `:144`, `:145`, `:150`, `:151`).
- Native resource pointers that are `ID3D11Resource`, `ID3D12Resource`, `VkBuffer`, or `VkImage`; native pointer and resource state are mandatory in the expected cases (`work/upstream/Streamline/include/sl_core_types.h:332`, `:333`, `:347`, `:353`, `:355`).
- Per-frame constants with jitter, motion-vector scaling, current/previous clip transforms, camera vectors, depth mode, and reset state (`work/upstream/Streamline/include/sl_consts.h:183`, `:184`, `:193`, `:201`, `:203`, `:207`, `:228`, `:230`, `:234`, `:240`; `work/upstream/Streamline/docs/ProgrammingGuideDLSS.md:206`, `:217`).
- Evaluation at the upscaling point, followed by host-side command-list state restoration (`work/upstream/Streamline/docs/ProgrammingGuideDLSS.md:224`, `:226`, `:236`, `:242`, `:243`).

## Iris Findings

Iris is OpenGL-facing in the inspected branch:

- `IrisRenderingPipeline` imports Minecraft `RenderTarget`, `GlStateManager`, `RenderSystem`, Iris `GlFramebuffer`, and LWJGL OpenGL classes (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java:5`, `:6`, `:7`, `:19`, `:102`, `:103`, `:104`, `:105`, `:106`, `:107`, `:108`).
- Iris initialization explicitly describes `RenderSystem#initRenderer` completion as the point where OpenGL can be safely accessed (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/Iris.java:111`, `:112`).
- `RenderTargets` stores and resizes OpenGL texture-backed color targets, creates `GlFramebuffer` objects, and attaches the Minecraft main depth texture (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/targets/RenderTargets.java:20`, `:24`, `:48`, `:62`, `:124`, `:125`, `:209`, `:214`, `:296`, `:305`, `:332`, `:337`, `:353`, `:357`).
- Iris `RenderTarget` allocates two OpenGL textures per logical render target via `GlStateManager._genTextures` and `IrisRenderSystem.texImage2D` (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/targets/RenderTarget.java:39`, `:40`, `:42`, `:43`, `:46`, `:47`, `:67`, `:68`).
- `GlFramebuffer` creates and binds OpenGL framebuffers and attaches GL textures (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/gl/framebuffer/GlFramebuffer.java:18`, `:19`, `:27`, `:34`, `:36`, `:42`, `:45`, `:84`, `:85`, `:88`, `:89`, `:92`, `:93`).

Frame boundary observations:

- Iris begins shader level rendering after Minecraft clears the main framebuffer (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/mixin/MixinLevelRenderer.java:79`, `:80`, `:122`, `:124`).
- Iris ends level rendering before later return injectors, renders translucent hand, and calls `pipeline.finalizeLevelRendering()` (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/mixin/MixinLevelRenderer.java:129`, `:131`, `:133`, `:135`).
- `finalizeLevelRendering()` runs all composite passes and then final pass (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java:1085`, `:1088`, `:1089`).
- `finalizeGameRendering()` later applies color-space conversion to the main render target color texture (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java:1092`, `:1093`, `:1094`; `work/upstream/Iris/common/src/main/java/net/irisshaders/iris/mixin/MixinGameRenderer.java:494`, `:496`).
- GUI rendering is separate from `renderLevel` and is grouped as `GUI` in Iris' `Gui` mixin (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/mixin/gui/MixinGui.java:34`, `:42`, `:44`, `:46`).

This means there is a plausible conceptual window after the world/composite/final pass and before GUI/HUD composition. However, that window is still OpenGL-only in the current backend. It does not provide the native D3D/Vulkan command buffer and resources that Streamline needs.

Resolution and buffer split observations:

- Iris sizes its render targets and final pass around `Minecraft.getInstance().getMainRenderTarget().width/height` (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java:226`, `:233`, `:265`, `:914`, `:920`, `:921`; `work/upstream/Iris/common/src/main/java/net/irisshaders/iris/pipeline/FinalPassRenderer.java:198`, `:199`, `:200`).
- Shaderpack viewport and texture scaling exist, but these are OpenGL shaderpack controls, not a DLSS render-resolution/output-resolution split (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/pipeline/CompositeRenderer.java:292`, `:293`, `:296`; `work/upstream/Iris/common/src/main/java/net/irisshaders/iris/uniforms/ViewportUniforms.java:27`, `:28`, `:36`).

Motion-vector observations:

- Iris exposes current/previous camera position uniforms and previous gbuffer matrices (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/uniforms/CameraUniforms.java:28`, `:30`, `:33`, `:90`, `:91`, `:92`; `work/upstream/Iris/common/src/main/java/net/irisshaders/iris/uniforms/MatrixUniforms.java:36`, `:37`, `:38`, `:66`, `:79`, `:81`).
- The only `velocity` source found in Iris common Java is a scalar camera-distance style custom uniform for shaderpacks (`work/upstream/Iris/common/src/main/java/net/irisshaders/iris/uniforms/HardcodedCustomUniforms.java:49`, `:54`, `:135`, `:136`, `:137`, `:138`, `:139`).
- Searches for source-backed motion-vector buffers in Iris/Sodium renderer code did not find a DLSS-ready motion vector texture. Camera-only reconstruction may be possible in a prototype but will not cover moving entities, particles, hands, water, animated blocks, or shader-driven displacement.

## Sodium Findings

Sodium's target tag is also OpenGL-facing:

- `RenderDevice.INSTANCE` is a `GLRenderDevice`, and the render device exposes OpenGL capabilities (`work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/client/gl/device/RenderDevice.java:6`, `:7`, `:22`).
- `GLRenderDevice` imports `org.lwjgl.opengl.*`, returns `GL.getCapabilities()`, and its command lists are immediate OpenGL wrappers around calls like `glBindVertexArray`, `glBufferData`, `glCopyBufferSubData`, fences, buffer storage, and multi-draw (`work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice.java:12`, `:16`, `:18`, `:27`, `:30`, `:56`, `:57`, `:89`, `:99`, `:107`, `:116`, `:249`, `:250`, `:269`, `:293`).
- Sodium's window/context workaround runs after `org.lwjgl.opengl.GL.createCapabilities()` and records WGL context state on Windows (`work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/mixin/workarounds/context_creation/WindowMixin.java:17`, `:18`, `:79`, `:81`, `:87`, `:88`).
- Sodium optimizes Minecraft `RenderTarget.blitToScreen` through OpenGL framebuffer blit (`work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/mixin/features/render/compositing/RenderTargetMixin.java:4`, `:6`, `:28`, `:40`, `:41`, `:44`, `:45`).

Sodium owns chunk/terrain replacement, not a D3D/Vulkan backend:

- `LevelRendererMixin` creates `SodiumWorldRenderer`, redirects chunk layer rendering to it, and wraps calls in `RenderDevice.enterManagedCode()` (`work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/mixin/core/render/world/LevelRendererMixin.java:65`, `:81`, `:122`, `:124`, `:127`, `:129`).
- `SodiumWorldRenderer` tracks camera position/projection for culling and sorting, then renders solid/cutout/translucent chunk layers (`work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.java:63`, `:66`, `:186`, `:188`, `:199`, `:201`, `:214`, `:219`, `:268`, `:270`, `:271`, `:273`).
- Block entities are still rendered through Minecraft block entity renderers and buffer sources, not through a DLSS motion-vector pass (`work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/mixin/core/render/world/LevelRendererMixin.java:210`, `:212`; `work/upstream/Sodium/common/src/main/java/net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.java:309`, `:314`, `:327`, `:329`, `:330`).

## Requirement Matrix

| DLSS SR requirement | Current Iris/Sodium status | Decision |
| --- | --- | --- |
| Native D3D/Vulkan device | Missing. Current source path is OpenGL/WGL/LWJGL. | Requires renderer backend replacement or fork. |
| Native command buffer/list for evaluation | Missing. Sodium `CommandList` is an immediate OpenGL wrapper, not a Streamline command buffer. | Requires backend replacement or fork. |
| Native color input/output resources | Missing. Current resources are GL texture IDs and GL framebuffers. | Requires backend replacement or proven interop layer. |
| Scene color before UI/HUD | Conceptually plausible after Iris final/composite and before GUI, but only as OpenGL main color texture. | Timing is useful, resource API is blocked. |
| Depth | Present as Minecraft main GL depth texture and Iris depth copies. | Needs D3D/Vulkan depth resource in a backend fork. |
| Motion vectors | Not present as a renderer-owned texture. Only previous camera/matrix uniforms and scalar camera velocity were found. | Requires new motion-vector pass; camera-only fallback would be limited. |
| Internal render size vs output size | Current main target width/height drives Iris targets; shaderpack scaling exists but is not DLSS render/output contract. | Requires render-size architecture work. |
| Jitter/reset/per-frame constants | Partial data exists for matrices/camera, but no DLSS constants path. | Implementable after backend resource problem. |
| UI/HUD at output resolution | Likely achievable in timing because GUI render is separate from world render. | Depends on backend and final composite restructuring. |

## Recommendation For Next Work

Do not write DLSS hooks against the current OpenGL Iris/Sodium backend.

Recommended next sequence:

1. Create a renderer-backend feasibility spike that can produce D3D12 or Vulkan color/depth resources and a command buffer compatible with Streamline. D3D12 is attractive because the official sample already ran successfully on the test machine, while Vulkan remains viable if a Minecraft backend fork makes it easier.
2. In parallel only as a small diagnostic, keep `mc-dlss-core` able to report `backend=openGL` and `dlssResourcePath=blocked`, so Fabric/NeoForge can surface a truthful debug status without pretending DLSS can evaluate.
3. If a backend fork is chosen, define the frame contract before mod-loader work: low-res scene color, depth, motion vectors, output scene color, jitter/reset, and UI/HUD composition after upscale.
4. Treat OpenGL-to-D3D/Vulkan interop as a research fallback, not the main path. The inspected source contains no such bridge, and Streamline still needs correct native D3D/Vulkan resources and command sequencing.

Milestone 2 acceptance is met: direct integration with the current backend is not feasible; a renderer fork or backend replacement is required before real DLSS SR evaluation.
