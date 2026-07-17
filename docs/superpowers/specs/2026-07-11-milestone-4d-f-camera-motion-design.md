# Milestone 4D-F Camera Motion And Temporal Constants Design

Date: 2026-07-11

## Goal

Prove that Minecraft can produce stable per-frame Streamline temporal constants and a full-resolution camera-motion texture derived from the captured hardware depth, transfer that texture through the existing OpenGL-D3D12 bridge, and validate it for 120 consecutive frames.

## Selected Approach

Extend each 4D-E persistent frame slot with one shared `DXGI_FORMAT_R16G16_FLOAT` motion-vector texture. At `WorldRenderEvents.END`, capture Fabric's current projection and position matrices plus camera state. Preserve the previous accepted frame's matrices. A full-screen OpenGL shader reconstructs each visible pixel from current normalized hardware depth, transforms it into previous clip space, and writes current-to-previous displacement in render-pixel units.

Publish an immutable temporal-constants snapshot for the same frame. It contains current and previous transforms, their inverses and cross-frame clip transforms, camera basis/position, near/far planes, field of view, aspect ratio, jitter, motion-vector scale, and Streamline flags. No temporal jitter is introduced in this milestone, so jitter is zero and motion vectors are marked non-jittered.

## Alternatives Rejected

- Instrumenting terrain, entities, particles, and hand rendering to emit per-object motion would provide higher fidelity but requires renderer-specific hooks and shader changes before the temporal bridge itself is proven.
- Supplying only matrices and a zero motion texture would not validate reprojection or the shared motion resource.
- CPU reconstruction from depth would not prove the intended GPU production path and would add a large synchronous readback cost.
- Normalized motion vectors would be valid, but pixel-space vectors are easier to inspect and match the existing Streamline probe's `mvecScale = {1 / width, 1 / height}` contract.

## Scope

- Fabric client, Windows x64, Minecraft 1.21.1, and the existing NVIDIA OpenGL-D3D12 interop route.
- Camera-only motion for static world geometry and background pixels.
- One `RG16_FLOAT` motion texture per existing A/B frame slot, matching render resolution.
- One temporal-constants record per submitted frame.
- Exactly 120 verified frames followed by the existing 30-frame retention and clean release.
- No entity-local, particle, animated-block, held-item, or skinned motion.
- No render/output resolution split, jitter injection, Streamline evaluation inside Minecraft, upscaled output, or composition.

## Matrix Convention

Fabric's `projectionMatrix()` and `positionMatrix()` are copied at the render callback so later mutations cannot affect the frame. The implementation defines and tests one explicit JOML column-vector convention:

`currentClip = currentProjection * currentViewRelativePosition`

The previous-frame mapping is derived through inverse current view-projection and previous view-projection transforms. Matrix conversion into `sl::float4x4` is isolated and tested with non-symmetric matrices so row/column transposition errors are observable.

The first accepted frame after startup, resize, world change, camera discontinuity, or invalid matrix uses identity cross-frame transforms, a cleared zero motion texture, and `reset=true`. A normal consecutive frame uses the retained previous transforms and `reset=false`.

## Motion Reconstruction

For each pixel, the shader:

1. Fetches current hardware depth with `texelFetch`.
2. Converts pixel center and depth to current clip coordinates using OpenGL NDC conventions.
3. Applies the inverse current projection to reconstruct camera-relative position.
4. Applies the current-to-previous camera transform and previous projection.
5. Divides by previous clip `w` when finite and safely nonzero.
6. Converts current and previous NDC positions to render-pixel coordinates.
7. Writes `currentPixel - previousPixel` to `RG`.

Far/background depth, invalid depth, non-finite matrices, positions behind the camera, and unsafe homogeneous divides produce `(0, 0)`. Output components are clamped to `[-width, width]` and `[-height, height]` before half-float storage. The exact sign convention is verified by a synthetic camera-translation test and documented beside the Streamline tagging code.

## Temporal Constants

The frame snapshot supplies values corresponding to Streamline `sl::Constants`:

- `cameraViewToClip` and `clipToCameraView`
- `clipToPrevClip` and `prevClipToClip`
- zero `jitterOffset`
- `mvecScale = {1 / renderWidth, 1 / renderHeight}`
- camera position, up, right, and forward
- camera near/far, vertical FOV, and aspect ratio
- `depthInverted=false`
- `cameraMotionIncluded=true`
- `motionVectors3D=false`
- `motionVectorsJittered=false`
- `motionVectorsDilated=false`
- per-frame `reset`

Near/far and FOV must come from the projection/render configuration used for the captured frame or be derived from its projection matrix with guarded formulas. Constants are rejected if any required scalar or matrix element is non-finite, if either matrix is non-invertible, or if dimensions and aspect ratio disagree.

## Native Contract

Add a new motion-frame session API rather than changing the established 4D-E primitive-array contract. Session info reports color, depth, motion, and fence handles. Submission copies all three shared textures under the same queue wait and signal. Inspection returns color/depth fingerprints plus a motion fingerprint and statistics.

The motion fingerprint hashes raw logical `R16G16_FLOAT` bytes in row order, excluding D3D12 row padding. Statistics include finite vector count, nonzero vector count, minimum/maximum component values, maximum magnitude, and out-of-bound count. OpenGL reads the shared texture using half-float bytes so its hash is bit-identical to D3D12.

## Per-Frame Flow

1. Capture immutable current matrices and camera state from `WorldRenderContext`.
2. Determine whether this frame is consecutive or requires temporal reset.
3. Perform the existing paired color and depth extraction.
4. Generate the motion texture from current depth and current/previous camera transforms, or clear it on reset.
5. Fingerprint all three OpenGL textures and validate motion statistics.
6. Signal one odd fence value and submit one D3D12 command list copying color, depth, and motion.
7. Wait for the even value and inspect all three D3D12 readbacks.
8. Require exact GL/D3D hash agreement for every resource and matching motion statistics.
9. Retain current matrices only after the complete frame is accepted.
10. Restore every modified OpenGL state in `finally`.

## Validation And State

The existing `WAITING`, `CAPTURING`, `RETAINING`, `COMPLETE`, and `FAILED` lifecycle remains. Resize transactionally rebuilds both three-texture slots and forces a temporal reset.

Every accepted frame requires finite and bounded motion values, zero out-of-bound vectors, and exact cross-API motion hashes. Reset frames must contain only zero vectors. During a controlled runtime acceptance, at least one stationary interval must be predominantly zero and at least one camera-motion interval must contain nonzero vectors and a changed motion hash. Color and depth continue to satisfy all 4D-E checks.

## Testing

- Pure Java tests cover matrix copying, inversion failure, cross-frame transforms, reset decisions, constants validation, snapshot success gating, and overlay formatting.
- Shader-source tests cover depth fetch, OpenGL NDC reconstruction, previous-frame projection, homogeneous guards, sign convention, pixel-space output, bounds, and zero fallback.
- Pure native tests cover three-resource session creation, `R16G16_FLOAT` footprints, padded-row hashing, half-float statistics, fence validation, and idempotent close.
- Mathematical reference tests compare shader-equivalent CPU reprojection against known identity, translation, rotation, and invalid-depth cases.
- Runtime acceptance requires 120/120 frames, A/B 60/60, 30/30 retained, exact color/depth/motion GL-D3D hashes, valid motion statistics, observed zero and nonzero motion phases, and final resource release.
- Existing Java tests, native CTests, Fabric compilation, and complete Gradle build remain green.

## Acceptance Criteria

- Two persistent slots each expose shared color, depth, and `RG16_FLOAT` motion textures under one fence.
- All 120 frames transfer and validate the three resources coherently.
- Every frame publishes finite, invertible, internally consistent temporal constants.
- Reset frames produce zero motion; consecutive stationary frames are predominantly zero.
- Camera movement produces bounded nonzero motion and at least one motion-hash change.
- GL and D3D12 motion hashes and statistics match exactly on every accepted frame.
- Both slots remain alive for 30 idle frames and all resources release afterward.
- Minecraft continues rendering after cleanup.
- Backend remains `OPENGL`, `BLOCKED_UNSUPPORTED_BACKEND`, and `dlss-ready=false`.

## Non-Claim

Passing 4D-F proves camera-derived 2D motion vectors and Streamline-compatible temporal constants across the live Minecraft OpenGL-D3D12 bridge. It does not prove per-object motion, temporal jitter correctness, disocclusion/reactive masks, asynchronous production performance, render/output resolution separation, Streamline DLSS evaluation inside Minecraft, or final upscaled presentation.
