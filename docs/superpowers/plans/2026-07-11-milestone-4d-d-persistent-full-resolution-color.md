# Milestone 4D-D Persistent Full-Resolution Color Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Verify 120 full-resolution pre-HUD frames through two alternating persistent OpenGL-D3D12 shared slots, retain them for 30 frames, then release cleanly.

**Architecture:** Preserve fixed 4D-B/C APIs and add dynamic persistent JNI methods. Generalize the native session for repeated allocator/list resets and exact fence values; drive two imported slots from a Fabric render-thread state machine with resize rebuild, per-frame hashes, and timing metrics.

**Tech Stack:** Java 21, Fabric rendering/tick events, LWJGL, JNI, C++17, D3D12, CMake/CTest.

## Constraints

- Dimensions 1..8192; two equal full-resolution RGBA8 slots.
- 120 verified frames plus 30 idle retained frames.
- Per-slot fence pairs 1/2, 3/4, and so on; 5,000 ms native bound.
- Existing fixed probes and `dlss-ready=false` remain unchanged.
- No Git commit steps because this workspace is not a repository.

### Task 1: Java Contracts And State Logic

- [ ] Add failing tests for persistent session validation, per-slot fence values, 120/30 state thresholds, resize/hash/timing metrics, and overlay success gating.
- [ ] Run Java verification and confirm RED failures for missing APIs.
- [ ] Implement core persistent session/JNI wrappers plus dependency-free schedule, state, snapshot, and overlay types.
- [ ] Run all Java tests green.

### Task 2: Reusable Native Sessions

- [ ] Add failing native tests for dynamic dimensions and repeated fence-pair validation.
- [ ] Generalize session allocation to 1..8192 while keeping fixed registry open at 64 x 64.
- [ ] Add repeated submit/reset and exact-signal inspect APIs, persistent registry methods, and JNI exports.
- [ ] Rebuild and pass all native CTests including existing 4C/4D probes.

### Task 3: Fabric Double-Buffer State Machine

- [ ] Implement transactional slot-pair creation/import/FBO attachment at Minecraft framebuffer dimensions.
- [ ] Alternate slots for 120 `WorldRenderEvents.END` frames, full-frame blit/read/hash/sync/inspect each frame, and collect timing/hash changes.
- [ ] Detect dimensions before every capture, release/recreate both slots on change, and count resizes.
- [ ] Retain both slots idle for 30 world frames, then release and publish final snapshot.
- [ ] Abort and release on world unload; cache terminal failure without retry.
- [ ] Integrate HUD progress and transition/final logging, then pass Fabric compilation and full Gradle build.

### Task 4: Runtime Acceptance And Documentation

- [ ] Run Java, metadata, Gradle, native CTest, and standalone 4C regressions.
- [ ] Enter a world, move the camera, require 120/120 verified frames, both slots used, hash changes, 30/30 retained frames, and resources released.
- [ ] Capture F2/log evidence and confirm continued rendering.
- [ ] Update status, feasibility, and toolchain docs with metrics and remaining non-claims.

## Self-Review

- The plan covers full resolution, double buffering, repeated fences, resize, 120/30 lifecycle, timing, cleanup, regressions, and evidence.
- Fixed 4D-B/C methods remain separate from persistent APIs.
- No placeholders or inconsistent method meanings remain.
