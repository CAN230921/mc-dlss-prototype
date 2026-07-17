# Milestone 2 Render Path Investigation Design

## Goal

Determine whether Minecraft Java `1.21.1` with Iris `1.8.x` and Sodium `0.8.x` can directly provide NVIDIA Streamline/DLSS SR with compatible native graphics resources and a viable evaluation point.

## Scope

This investigation is read-only against upstream renderer sources. It does not implement Minecraft hooks, modify Iris/Sodium, or attempt a shaderpack-level DLSS path.

Target stack:

- Minecraft: `1.21.1`
- Iris: `1.8.x`
- Sodium: `0.8.x`
- Loader targets: Fabric and NeoForge
- Current pack versions: `iris-neoforge-1.8.14-beta.1+mc1.21.1`, `sodium-neoforge-0.8.12-beta.2+mc1.21.1`
- DLSS feature: Super Resolution only

## Approach

Use primary source code and official documentation only. Fetch the relevant Iris and Sodium repositories or release source artifacts, identify the closest tags/branches matching the target versions, and inspect render ownership around framebuffers, world rendering, shader pipelines, post-processing, and UI composition.

The key question is not whether Java can call native code; Milestone 0 already proved that. The question is whether the render backend exposes D3D/Vulkan command buffers and resources, or whether it remains OpenGL-only at the point DLSS SR would need color, depth, motion vectors, constants, and output targets.

## Evidence Model

The final note must cite exact local source files and line numbers where possible. It must distinguish between facts from source code and inferences from those facts.

Required evidence:

- Backend API evidence: OpenGL, Vulkan, D3D, LWJGL, or other native API ownership.
- Scene color/depth target ownership.
- World render and post-process boundaries.
- UI/HUD composition timing.
- Motion vector availability or absence.
- Feasibility of passing Streamline-compatible resources.

## Decision Rules

- If the backend is OpenGL-only and no D3D/Vulkan native resources exist, direct Streamline DLSS SR integration is not feasible in the current backend.
- If compatible resources exist only through a renderer fork or alternative backend, recommend that path before loader glue.
- If motion vectors are incomplete but resource access is feasible, document motion-vector limitations separately from backend feasibility.
- Do not recommend Frame Generation work in this phase even if the hardware supports it.
