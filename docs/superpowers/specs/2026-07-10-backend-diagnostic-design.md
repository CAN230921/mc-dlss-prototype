# Backend Diagnostic Design

## Goal

Turn the Milestone 2 render-path conclusion into a small, loader-neutral Java contract that can be surfaced by Fabric, NeoForge, or debug tooling.

The contract must clearly report that the current Iris/Sodium path is OpenGL-backed and therefore cannot directly evaluate Streamline DLSS SR, because Streamline requires D3D/Vulkan native resources and a compatible command buffer/list.

## Scope

This is a diagnostic and planning step only.

In scope:

- Add backend/resource status types to `mc-dlss-core`.
- Add a canonical status for the investigated Iris/Sodium OpenGL backend.
- Keep the contract dependency-free and usable by both loader modules.
- Document what a D3D12 or Vulkan renderer-backend spike must prove next.

Out of scope:

- Minecraft mixins or loader hooks.
- Iris/Sodium source modifications.
- OpenGL interop implementation.
- DLSS evaluation calls.
- Motion-vector generation.

## Contract Requirements

The core diagnostic must distinguish:

- Backend kind: OpenGL, D3D12, Vulkan, or unknown.
- Overall DLSS resource-path status.
- Native device availability.
- Native command-buffer/list availability.
- Native color and depth resource availability.
- Motion-vector texture availability.
- Whether UI/HUD-after-upscale timing remains conceptually possible.
- A human-readable explanation suitable for debug overlays/logs.

## Decision Rules

- OpenGL is reported as unsupported for direct Streamline DLSS SR.
- D3D12 and Vulkan are reported as Streamline-compatible backend kinds, but not automatically ready without resources, command context, and motion vectors.
- A ready diagnostic must be reserved for a future backend that has all required resources and command context.

## Acceptance

- The dependency-free Java verification fails before implementation and passes after implementation.
- The canonical OpenGL diagnostic reports `blocked` and explains the Streamline resource mismatch.
- Documentation states the next renderer-backend spike criteria.
