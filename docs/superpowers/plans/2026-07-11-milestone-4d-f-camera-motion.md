# Milestone 4D-F Camera Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add camera-derived pixel-space motion vectors and validated Streamline temporal constants to the persistent Minecraft OpenGL-D3D12 frame bridge.

**Architecture:** Fabric snapshots immutable current/previous render matrices and produces an `RG16_FLOAT` motion texture from current depth in a full-screen pass. A new native three-resource session transfers color, depth, and motion under one fence and returns exact fingerprints/statistics. Existing 4D-E APIs remain unchanged for regression coverage.

**Tech Stack:** Java 21, Fabric API 1.21.1, JOML, LWJGL OpenGL/NV interop, JNI, C++20, D3D12, CMake/CTest, PowerShell smoke tests.

## Global Constraints

- Windows x64 and the existing NVIDIA OpenGL-D3D12 interop route only.
- Motion format is `DXGI_FORMAT_R16G16_FLOAT` / `GL_RG16F` at render resolution.
- Motion is current-to-previous displacement in render-pixel units; `mvecScale = {1 / width, 1 / height}`.
- 4D-F covers camera motion only; entity-local and animated-object motion remain out of scope.
- Startup, resize, world change, and discontinuity force zero motion and `reset=true`.
- Existing 4D-E contracts and tests remain available and green.
- No Streamline evaluation, jitter injection, render/output split, upscaling, or composition in this milestone.

---

### Task 1: Temporal Matrix And Reprojection Model

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/CameraTemporalFrame.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/CameraTemporalReprojection.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/CameraTemporalReprojectionSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: JOML `Matrix4f`, camera position/basis, framebuffer dimensions.
- Produces: immutable `CameraTemporalFrame`; `CameraTemporalReprojection.between(previous, current)` with `clipToPrevClip`, `prevClipToClip`, reset state, and pixel-space reference projection.

- [ ] **Step 1: Write failing reference tests**

Cover identity reprojection, known horizontal translation sign, rotation, non-invertible matrices, dimension change reset, and invalid depth zero fallback. Assert copied matrices cannot be mutated through source aliases.

- [ ] **Step 2: Run the focused self-test and verify failure**

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-java.ps1`

Expected: compilation fails because `CameraTemporalFrame` and `CameraTemporalReprojection` do not exist.

- [ ] **Step 3: Implement immutable frame and guarded reprojection**

Use defensive `new Matrix4f(source)` copies, `Float.isFinite` validation, determinant/inversion guards, and explicit column-vector multiplication. Return reset reprojection with identity cross-frame transforms when continuity requirements fail.

- [ ] **Step 4: Run Java smoke tests**

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-java.ps1`

Expected: all Java self-tests pass, including the new mathematical cases.

### Task 2: Streamline Temporal Constants Snapshot

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeTemporalConstants.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativeTemporalConstantsSelfTest.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftTemporalConstants.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/MinecraftTemporalConstantsSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: `CameraTemporalFrame` and `CameraTemporalReprojection`.
- Produces: validated constants with 16-float matrices, camera vectors/scalars, `mvecScale`, dimensions, and Streamline boolean flags.

- [ ] **Step 1: Write failing constants tests**

Assert matrix layout using a non-symmetric matrix; verify inverses, reciprocal dimensions, zero jitter, finite values, aspect ratio, and exact flags. Reject NaN, singular transforms, nonpositive dimensions, and inconsistent aspect ratio.

- [ ] **Step 2: Verify tests fail for missing types**

Run the Java verification script and confirm compilation failure names the new constants types.

- [ ] **Step 3: Implement validated constants conversion**

Keep native-facing arrays defensive. Derive near/far/FOV from the frame inputs, preserve `reset`, and expose only explicit accessors required by eventual JNI/Streamline conversion.

- [ ] **Step 4: Run Java smoke tests**

Expected: all Java self-tests pass.

### Task 3: Native Half-Float Motion Fingerprint

**Files:**
- Create: `mc-dlss-native/include/motion_readback_fingerprint.h`
- Create: `mc-dlss-native/src/motion_readback_fingerprint.cpp`
- Create: `mc-dlss-native/tests/motion_readback_fingerprint_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `MotionReadbackFingerprint fingerprintMotionReadback(const std::byte*, uint32_t width, uint32_t height, size_t rowPitch)`.
- Statistics: FNV-1a hash, finite/nonzero/out-of-bound counts, min/max components, maximum magnitude.

- [ ] **Step 1: Add failing C++ tests**

Use exact IEEE-754 half encodings for zero, positive, negative, infinity, and NaN. Include a padded final row and prove padding is excluded from the hash.

- [ ] **Step 2: Configure/build and verify the focused target fails**

Run the existing native CMake configure/build command and expect an unresolved missing fingerprint implementation.

- [ ] **Step 3: Implement half conversion, logical-row hashing, and statistics**

Use checked byte arithmetic, hash exactly `width * 4` bytes per row, decode both components, count finite and bounded vectors, and avoid reading trailing row padding.

- [ ] **Step 4: Run focused and full native tests**

Run: `ctest --test-dir work/native-build --output-on-failure`

Expected: all native tests pass with the new motion test included.

### Task 4: Three-Resource D3D12 Frame Session

**Files:**
- Create: `mc-dlss-native/include/live_d3d12_motion_frame_session.h`
- Create: `mc-dlss-native/src/live_d3d12_motion_frame_session.cpp`
- Create: `mc-dlss-native/include/live_d3d12_motion_frame_registry.h`
- Create: `mc-dlss-native/src/live_d3d12_motion_frame_registry.cpp`
- Create: `mc-dlss-native/tests/live_d3d12_motion_frame_session_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: open/submit/inspect/close operations with color, depth, motion, and fence handles.
- Consumes: Task 3 motion fingerprint and existing color/depth fingerprint behavior.

- [ ] **Step 1: Write failing session tests**

Assert `R8G8B8A8_UNORM`, `R32_FLOAT`, and `R16G16_FLOAT` resources have matching dimensions and shared handles; validate odd/even fences, three readback footprints, timeout diagnostics, and idempotent close.

- [ ] **Step 2: Build and observe failure**

Expected: test target fails because the motion-frame session API is absent.

- [ ] **Step 3: Implement transactional session and registry**

Create all three shared textures before publishing a session. Submit one command list that waits once, transitions/copies/restores all resources, and signals once. On any failure, release the entire partially built session.

- [ ] **Step 4: Run full native CTest suite**

Expected: all tests pass, including prior 4D-E frame-session tests.

### Task 5: JNI And Java Motion Session Contract

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativePersistentMotionFrameSessionInfo.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeMotionFrameReadbackFingerprint.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativePersistentMotionFrameSessionSelfTest.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Modify: `mc-dlss-native/include/mc_dlss_native.h`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: `openD3D12PersistentMotionFrameSession`, `submitD3D12PersistentMotionFrameReadback`, `inspectD3D12PersistentMotionFrameReadback`, and `closeD3D12PersistentMotionFrameSession`.

- [ ] **Step 1: Write failing Java decode tests**

Cover valid fixed-length arrays, missing symbols, malformed arrays, bit-preserving hashes/floats, unavailable results, and idempotent close behavior.

- [ ] **Step 2: Verify Java tests fail**

Expected: missing Java record and bridge methods.

- [ ] **Step 3: Implement Java/JNI boundary**

Use versioned fixed array layouts and structured unavailable messages. Declare every JNI export in the C++ header before definition to preserve C linkage.

- [ ] **Step 4: Run Java, native, and native-symbol checks**

Expected: Java tests and CTest pass; exported JNI names are unmangled.

### Task 6: OpenGL Motion Shader And State Isolation

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftMotionVectorShaderSource.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftMotionVectorShader.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/MinecraftMotionVectorShaderSourceSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: current depth texture and Task 1 cross-frame matrices.
- Produces: bounded current-to-previous vectors in `GL_RG16F`, or exact zero for reset/invalid pixels.

- [ ] **Step 1: Write failing shader-source tests**

Require `texelFetch`, OpenGL NDC conversion, inverse current projection, previous projection, guarded `w`, pixel-space conversion, the documented sign, clamping, and zero fallback.

- [ ] **Step 2: Verify source tests fail**

Expected: missing shader source class.

- [ ] **Step 3: Implement source and managed shader**

Compile one full-screen triangle program and upload matrices/dimensions/reset uniforms. Save and restore program, VAO, active texture, texture binding, framebuffer, viewport, masks, and depth/blend/scissor/cull state in `finally`.

- [ ] **Step 4: Run Java tests and Fabric compilation**

Run: `.\gradlew.bat :mc-dlss-fabric:compileJava --console=plain --no-daemon --no-parallel`

Expected: successful compilation and all shader-source tests pass.

### Task 7: Persistent Minecraft Motion Probe

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MotionFrameFingerprint.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftPersistentMotionFrameCaptureProbe.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftPersistentMotionFrameCaptureSnapshot.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftPersistentMotionFrameCaptureOverlayLines.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/MotionFrameFingerprintSelfTest.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/MinecraftPersistentMotionFrameCaptureSnapshotSelfTest.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Consumes: `WorldRenderContext`, Tasks 1-2 temporal model, Task 5 native session, Task 6 shader.
- Produces: 120-frame/30-retained lifecycle snapshot and concise HUD/log diagnostics.

- [ ] **Step 1: Write failing fingerprint and lifecycle tests**

Cover raw half-byte hashing, reset-zero gating, stationary/nonzero observations, A/B 60/60 gating, exact GL/D3D matches, resize reset, retention, release, and failure cleanup.

- [ ] **Step 2: Verify focused tests fail**

Expected: missing motion probe types.

- [ ] **Step 3: Implement three-texture slots and capture flow**

Create/import `GL_RG16F`, capture immutable context matrices, run color/depth/motion passes, fingerprint raw half bytes, exchange one fence pair, compare native results, and retain previous temporal state only after full acceptance.

- [ ] **Step 4: Integrate after successful 4D-E completion**

Pass the actual `WorldRenderContext` into the new probe from `WorldRenderEvents.END`. Log transitions under `[mc_dlss/fabric/persistent-motion]` and keep existing overlays unchanged.

- [ ] **Step 5: Run Java tests and full Gradle build**

Run: `.\gradlew.bat build --console=plain --no-daemon --no-parallel`

Expected: build successful and all Java self-tests pass.

### Task 8: Live Acceptance And Documentation

**Files:**
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`
- Modify: `docs/windows-toolchain.md`
- Create: `outputs/fabric-persistent-camera-motion-2026-07-11.log`

**Interfaces:**
- Consumes: completed runtime probe.
- Produces: reproducible acceptance evidence and updated project status.

- [ ] **Step 1: Run Minecraft client with native library path configured**

Use the established hidden `runClient` launch and monitor the persistent-motion lifecycle log.

- [ ] **Step 2: Exercise stationary and camera-motion phases**

Keep the camera still long enough to observe predominantly zero vectors, then move/rotate it to produce bounded nonzero vectors and motion hash changes. Do not require entity animation.

- [ ] **Step 3: Verify final metrics**

Require `frames=120/120`, `slotAUses=60`, `slotBUses=60`, `retained=30/30`, matching color/depth/motion hashes, zero out-of-bound motion, observed stationary/nonzero phases, `resourcesReleased=true`, and continued rendering.

- [ ] **Step 4: Close Minecraft and run fresh regression suites**

Run Java verification, full CTest, and full Gradle build. Expected: every suite passes and no Minecraft process remains.

- [ ] **Step 5: Update documentation with exact evidence**

Record dimensions, frame counts, hash changes, vector statistics, timings, release state, log path, and the explicit camera-only non-claim. Keep `dlss-ready=false` until Streamline evaluation is integrated.
