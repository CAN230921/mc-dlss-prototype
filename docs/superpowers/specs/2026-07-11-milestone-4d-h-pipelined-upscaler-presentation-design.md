# Milestone 4D-H Pipelined Upscaler Presentation Design

## Goal

Convert the verified synchronous 4D-G DLSS presentation path into a low-overhead A/B GPU pipeline. The first 30 accepted frames retain full cross-API validation; subsequent frames avoid diagnostic output readbacks and CPU completion waits while preserving exact-size full-resolution presentation and reliable vanilla fallback.

The scheduling and resource boundary is vendor-neutral so a future D3D12 XeSS or FSR 3 upscaler adapter can consume the same Minecraft inputs. This milestone implements and accepts only NVIDIA Streamline DLSS Quality.

## Scope

### Included

- A `VALIDATING` execution mode for the existing full GL/D3D12 fingerprint checks.
- Automatic transition to `FAST` after 30 consecutive validated frames.
- Fast D3D12 evaluation without copying output to the diagnostic readback buffer.
- GPU-ordered OpenGL semaphore wait and output composite without `glFinish` or `glReadPixels`.
- A/B slot reuse checks and a nonblocking slot scheduler.
- Bounded busy-slot fallback with temporal reset instead of a CPU stall.
- A 300-frame fast-mode acceptance gate and validation/fast timing telemetry.
- One periodic fully validated frame every 300 fast frames.
- Backend-neutral session/resource/scheduling interfaces above the existing Streamline implementation.
- Success, busy-slot, and injected-evaluation-failure runtime evidence.

### Excluded

- Loading or evaluating Intel XeSS.
- Loading or evaluating AMD FSR 3.
- Frame generation, interpolation, or swap-chain replacement.
- Entity-local motion vectors, reactive masks, disocclusion masks, exposure textures, or jittered rendering.
- NeoForge parity, other quality modes, HDR certification, or release-grade Streamline shutdown.

## Alternatives Considered

### Remove Fingerprints Only

This is the smallest change but leaves explicit CPU/GPU waits in the frame path. It lowers bandwidth but does not establish a useful production scheduling model.

### Validate Then Use A/B GPU Ordering

This is the selected design. Thirty fully checked frames preserve 4D-G confidence. Fast mode removes per-frame readback and `glFinish`, keeps synchronization on the GPU, and falls back instead of blocking if neither slot is reusable.

### Triple-Buffered Deep Pipeline

Three or more slots could hide longer GPU latency but add memory, presentation latency, and temporal-history complexity before two-slot pressure has been measured. It is deferred.

## Architecture

### Vendor-Neutral Boundary

The common live-upscaler contract owns:

- render/output dimensions
- A/B color, depth, motion, output, and fence resources
- temporal constants
- slot availability and monotonically increasing fence values
- validation versus fast execution mode
- evaluation submission and structured stage results

Vendor adapters own feature initialization, optimal settings, resource tagging, constants conversion, and evaluation recording. The existing `LiveStreamlineRuntime` remains the only adapter implemented in 4D-H. The native JNI names may retain `LiveDlss` for compatibility, but new scheduling types and mode values must not encode Streamline-only behavior.

The common resource contract intentionally matches the shared subset useful to DLSS, XeSS, and FSR-style temporal upscalers: D3D12 FP16 color, R32F depth, RG16F motion, FP16 output, dimensions, reset, jitter, camera transforms, and motion scaling. Future adapters may reject this contract or request additional resources; 4D-H makes no compatibility claim until that adapter has its own tests and live evidence.

### Execution Modes

`VALIDATING` performs the exact 4D-G path:

1. Populate shared inputs.
2. Signal OpenGL ownership transfer.
3. Record evaluation and output readback.
4. Wait for the D3D12 fence.
5. Inspect native output and read OpenGL FP16 output.
6. Require matching hashes and valid finite/non-black/non-uniform output.
7. Composite the output.

After 30 consecutive valid frames, the controller enters `FAST`:

1. Populate shared inputs and signal the odd fence value.
2. Record evaluation without diagnostic readback.
3. Insert an OpenGL semaphore wait for the even fence value without `glFinish`.
4. Restore the full-resolution main framebuffer.
5. Queue the exact-size output blit after the semaphore wait.
6. Accept temporal history only after submission and presentation commands are successfully queued.

Every 300 fast frames, one frame uses `VALIDATING` operations as a health sample, then returns to `FAST` when hashes and output validity pass.

### Slot Scheduler

The scheduler scans the preferred alternating slot followed by the other slot. A slot is reusable when its native shared fence has completed the last submitted even value. The check is nonblocking.

If a slot is ready, it receives the next odd/even fence pair. If both slots are busy, the controller restores the original framebuffer, linearly scales the low-resolution world for that frame, increments `busyFallbackFrames`, resets temporal history, and tries again next frame. Eight consecutive busy fallbacks disable fast mode and restore vanilla rendering for the process.

The scheduler never reuses a D3D12 command allocator, command list, or shared texture before the slot fence completes.

## State And Telemetry

Presentation state retains the existing lifecycle and adds:

- `executionMode`: `VALIDATING`, `FAST`, or `FALLBACK`
- validated frames out of 30
- fast frames out of 300
- periodic validation count
- slot-busy count and consecutive busy count
- validation average/maximum milliseconds
- fast CPU submission/presentation average/maximum milliseconds
- last completed fence per slot
- project resources retained/released
- exact failure stage/message

`dlss-ready=true` requires 30 validated frames followed by 300 fast frames, A/B use, at least one periodic validation, valid matching sampled hashes, no unresolved failure, and the original framebuffer restored.

## Failure Handling

- Startup non-finite projection frames retain the existing bounded transitional fallback.
- A busy slot is recoverable and does not release the session unless it repeats eight consecutive frames.
- A failed evaluation, semaphore operation, validation sample, resource transition, or composite restores the original framebuffer, presents a valid linear fallback for the current frame, releases imported GL resources before native resources, and disables the upscaler.
- A resize rebuilds render/output resources, returns to `VALIDATING`, and resets temporal history and all acceptance counters.
- Process shutdown calls `slFreeResources` through session close but does not call `slShutdown`.

## Testing

### Pure Java

- Mode transition requires exactly 30 consecutive validated frames.
- Fast readiness requires 300 fast frames and a periodic validation.
- Preferred/alternate slot selection is deterministic.
- Two busy slots produce fallback without advancing temporal history.
- Eight consecutive busy frames transition to permanent fallback.
- Resize returns to validation and forces reset.
- Snapshot and HUD fields gate readiness correctly.

### Native Contract

- Validation mode records output readback and exposes a fingerprint.
- Fast mode records no output readback and inspection reports that no diagnostic sample was requested.
- Slot readiness is a nonblocking completed-fence comparison.
- Command allocators reset only after completed fence values.
- Consecutive frame tokens remain monotonic in both modes.
- Fake backend failure stages remain exact.

### Real GPU And Minecraft

- The existing two-frame real Streamline evaluation remains green.
- A native fast-mode test runs multiple alternating evaluations without output readback.
- Minecraft completes 30 validated frames, 300 fast frames, balanced A/B use, and at least one periodic validation.
- Sampled GL/D3D12 hashes match and remain finite/non-black/non-uniform.
- Fast CPU timing is recorded separately and is lower than validation timing on the same run.
- Busy-slot injection produces a visible fallback and recovers.
- Evaluation-failure injection restores vanilla rendering and releases resources.
- Java self-tests, default native CTest, Streamline CTest excluding the known shutdown probe, and the complete Gradle build pass.

## Acceptance Criteria

- `VALIDATING` completes 30 consecutive fully fingerprinted frames.
- `FAST` completes 300 frames without per-frame native readback, `glReadPixels`, or `glFinish`.
- A/B slots are both used and never reused before their even fence value completes.
- At least one periodic validation sample passes with matching GL/D3D12 hashes.
- Fast-mode CPU timing is lower than validation-mode timing in the acceptance run.
- Main framebuffer restoration remains true before native-resolution hand/HUD rendering.
- Busy-slot and evaluation-failure paths show a valid fallback frame and never present uninitialized output.
- All project-owned resources release on failure or client close; Streamline global state remains intentionally loaded until process exit.

## Non-Claims

Passing 4D-H proves a two-slot, GPU-ordered, low-readback Streamline DLSS presentation path and a backend-neutral integration boundary. It does not prove Intel XeSS compatibility, AMD FSR 3 compatibility, frame generation, entity-local motion quality, asynchronous overlap across Minecraft simulation frames, production FPS, latency targets, or release-grade multi-vendor support.
