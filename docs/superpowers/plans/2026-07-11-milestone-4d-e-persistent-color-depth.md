# Milestone 4D-E Persistent Color And Depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and verify a resize-aware two-slot OpenGL-D3D12 bridge that transfers full-resolution pre-HUD color and hardware depth together for 120 frames, retains resources for 30 frames, and releases them cleanly.

**Architecture:** Keep all existing color-only APIs intact. Add a new native frame session containing shared RGBA8 color, shared R32_FLOAT depth data, one fence, and two readbacks; expose it through typed Java contracts. Fabric extracts Minecraft depth with a focused shader component and drives a new paired color/depth state machine from `WorldRenderEvents.END`.

**Tech Stack:** Java 21, Fabric API 0.116.13, Minecraft/Yarn 1.21.1, LWJGL OpenGL, JNI, C++17, D3D12, CMake/CTest, Gradle 9.6.1.

## Global Constraints

- Use two equal frame slots at Minecraft framebuffer dimensions, each limited to `1..8192` in both axes.
- Use `DXGI_FORMAT_R8G8B8A8_UNORM` for color and `DXGI_FORMAT_R32_FLOAT` for raw normalized hardware depth data.
- Use one shared fence per slot with pairs `1/2`, `3/4`, and a 5,000 ms native wait bound.
- Verify exactly 120 frames, retain both slots idle for exactly 30 world frames, then release.
- Preserve existing 4D-B, 4D-C, and 4D-D APIs and evidence behavior.
- Keep backend `OPENGL`, status `BLOCKED_UNSUPPORTED_BACKEND`, and `dlss-ready=false`.
- Do not run the optional Streamline/NGX shutdown regression.
- This workspace is not a Git repository, so commit steps are intentionally omitted.

---

### Task 1: Core Frame-Session Contracts

**Files:**
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativePersistentFrameSessionInfo.java`
- Create: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeFrameReadbackFingerprint.java`
- Create: `mc-dlss-core/src/testSmoke/java/dev/mcdlss/core/NativePersistentFrameSessionSelfTest.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeBridge.java`
- Modify: `mc-dlss-core/src/main/java/dev/mcdlss/core/NativeLibraryBridge.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: `NativePersistentFrameSessionInfo.available(long,long,long,long,int,int)`.
- Produces: `NativeFrameReadbackFingerprint.available(...)` and `unavailable(String)`.
- Produces: four `NativeBridge` frame-session methods used by Fabric.
- JNI open array: `[sessionId, colorHandle, depthHandle, fenceHandle]`.
- JNI inspect array: `[status, colorHash, colorNonUniform, colorNonBlack, depthHash, depthNonUniform, depthFinite, depthInRange, depthScene, depthFar, minDepthBits, maxDepthBits, completedFence, lastSubmittedFence]`.

- [ ] **Step 1: Write the failing contract test**

Add assertions with the desired API:

```java
NativePersistentFrameSessionInfo info = NativePersistentFrameSessionInfo.available(
        3L, 5L, 7L, 9L, 854, 480);
if (!info.available() || info.depthTextureHandle() != 7L || info.fenceHandle() != 9L) {
    throw new AssertionError("Expected complete frame session handles");
}

long[] ready = {
    1, 11, 1, 409920,
    13, 1, 409920, 409920, 350000, 59920,
    Float.floatToRawIntBits(0.2f), Float.floatToRawIntBits(1.0f), 2, 2
};
NativeFrameReadbackFingerprint fingerprint =
        NativeLibraryBridge.decodePersistentFrameFingerprint(ready, 2, 409920);
if (!fingerprint.available() || fingerprint.depthHash() != 13L
        || fingerprint.depthFiniteSampleCount() != 409920
        || fingerprint.minimumDepth() != 0.2f) {
    throw new AssertionError("Expected decoded color/depth fingerprint");
}
```

- [ ] **Step 2: Run the core test and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-java.ps1
```

Expected: compilation fails because the frame-session types and decoder do not exist.

- [ ] **Step 3: Implement immutable core records**

Use these record shapes:

```java
public record NativePersistentFrameSessionInfo(
        boolean available,
        long sessionId,
        long colorTextureHandle,
        long depthTextureHandle,
        long fenceHandle,
        int width,
        int height,
        String message) {
}

public record NativeFrameReadbackFingerprint(
        boolean available,
        long colorHash,
        boolean colorNonUniform,
        int colorNonBlackPixelCount,
        long depthHash,
        boolean depthNonUniform,
        int depthFiniteSampleCount,
        int depthInRangeSampleCount,
        int depthSceneSampleCount,
        int depthFarSampleCount,
        float minimumDepth,
        float maximumDepth,
        String message) {
}
```

Factories reject non-positive handles, dimensions outside `1..8192`, negative counts, non-finite min/max values, and `minimumDepth > maximumDepth`.

- [ ] **Step 4: Add bridge defaults and JNI wrappers**

Add exact methods to `NativeBridge`:

```java
default NativePersistentFrameSessionInfo openD3D12PersistentFrameSession(
        int width, int height) {
    return NativePersistentFrameSessionInfo.unavailable(
            "Persistent frame interop is unavailable");
}

default boolean submitD3D12PersistentFrameReadback(
        long sessionId, long waitValue, long signalValue) {
    return false;
}

default NativeFrameReadbackFingerprint inspectD3D12PersistentFrameReadback(
        long sessionId, long signalValue, int expectedPixelCount) {
    return NativeFrameReadbackFingerprint.unavailable(
            "Persistent frame inspection is unavailable");
}

default void closeD3D12PersistentFrameSession(long sessionId) {
}
```

Add native declarations and strict array decoding in `NativeLibraryBridge`; status `1` is ready and statuses `2..5` map to invalid request, timeout, missing session, and native exception messages with requested/completed/last-submitted values.

- [ ] **Step 5: Register and run the Java self-test**

Add `NativePersistentFrameSessionSelfTest` to `scripts/verify-java.ps1`, then rerun the script.

Expected: all existing tests plus the new frame-session test pass.

---

### Task 2: Native Depth Fingerprinting

**Files:**
- Create: `mc-dlss-native/include/depth_readback_fingerprint.h`
- Create: `mc-dlss-native/src/depth_readback_fingerprint.cpp`
- Create: `mc-dlss-native/tests/depth_readback_fingerprint_test.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `DepthReadbackFingerprint fingerprintDepthReadback(const uint8_t*, size_t, uint32_t, uint32_t) noexcept`.
- Uses the same FNV-1a byte order as Java and ignores D3D12 row padding.

- [ ] **Step 1: Write a failing padded-row test**

```cpp
const float values[] = {0.25f, 1.0f, 0.5f, 1.0f};
std::vector<std::uint8_t> padded(32, 0x7f);
std::memcpy(padded.data(), values, 8);
std::memcpy(padded.data() + 16, values + 2, 8);
const auto result = mc_dlss::fingerprintDepthReadback(
    padded.data(), 16, 2, 2);
if (!result.available || !result.nonUniform || result.finiteSampleCount != 4
    || result.inRangeSampleCount != 4 || result.sceneSampleCount != 2
    || result.farSampleCount != 2 || result.minimum != 0.25f
    || result.maximum != 1.0f) {
    return 1;
}
```

Also test NaN, infinity, negative, and greater-than-one values are counted as invalid rather than silently accepted.

- [ ] **Step 2: Build the target and verify RED**

Run:

```powershell
[System.Environment]::SetEnvironmentVariable('PATH',$null,[System.EnvironmentVariableTarget]::Process)
& 'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe' --build build\native --config Release --target mc_dlss_depth_readback_fingerprint_test
```

Expected: CMake target or fingerprint API is missing.

- [ ] **Step 3: Implement byte-exact depth statistics**

Define:

```cpp
struct DepthReadbackFingerprint {
    bool available = false;
    std::uint64_t hash = 0;
    bool nonUniform = false;
    std::uint32_t finiteSampleCount = 0;
    std::uint32_t inRangeSampleCount = 0;
    std::uint32_t sceneSampleCount = 0;
    std::uint32_t farSampleCount = 0;
    float minimum = 0.0f;
    float maximum = 0.0f;
};
```

Hash each logical float's four raw bytes. Use `std::isfinite`, range `[0.0f, 1.0f]`, and far threshold `0.9999f`. Initialize min/max from the first finite sample and set `available=true` only when dimensions, pointer, and row pitch are valid.

- [ ] **Step 4: Register the library and CTest target**

Create `mc_dlss_depth_readback_support`, link it into the new test and later frame session, and add CTest name `depth_readback_fingerprint`.

- [ ] **Step 5: Run the focused test green**

Run the build command and:

```powershell
& 'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\ctest.exe' --test-dir build\native -C Release -R depth_readback_fingerprint --output-on-failure
```

Expected: one test passes.

---

### Task 3: Native Two-Texture Frame Session

**Files:**
- Create: `mc-dlss-native/include/live_d3d12_frame_session.h`
- Create: `mc-dlss-native/src/live_d3d12_frame_session.cpp`
- Create: `mc-dlss-native/include/live_d3d12_frame_registry.h`
- Create: `mc-dlss-native/src/live_d3d12_frame_registry.cpp`
- Create: `mc-dlss-native/tests/live_d3d12_frame_session_test.cpp`
- Modify: `mc-dlss-native/include/mc_dlss_native.h`
- Modify: `mc-dlss-native/src/mc_dlss_native.cpp`
- Modify: `mc-dlss-native/CMakeLists.txt`

**Interfaces:**
- Produces: `LiveD3D12FrameSessionInfo` with session/color/depth/fence handles.
- Produces: one submit method and one paired inspection result.
- Consumes: `validPersistentFencePair`, `fingerprintInteropReadback`, `fingerprintDepthReadback`, and `interopReadbackRangeEnd`.

- [ ] **Step 1: Write failing lifecycle and validation tests**

Require that `openPersistentD3D12FrameSession(854, 480)` returns four positive handles, `0` and `8193` dimensions fail, inspection before submission returns `invalidRequest`, missing IDs return `missingSession`, and close is idempotent.

```cpp
const auto session = mc_dlss::openPersistentD3D12FrameSession(854, 480);
if (!session.available() || session.colorTextureHandle == 0
    || session.depthTextureHandle == 0 || session.fenceHandle == 0) {
    return 1;
}
const auto rejected = mc_dlss::inspectPersistentD3D12FrameReadback(
    session.sessionId, 2);
if (rejected.status != mc_dlss::InteropReadbackStatus::invalidRequest) {
    return 1;
}
```

- [ ] **Step 2: Build and verify RED**

Build target `mc_dlss_live_d3d12_frame_session_test` with Visual Studio CMake.

Expected: missing frame-session and registry symbols.

- [ ] **Step 3: Implement the frame session**

Create two shared default-heap textures:

```cpp
createSharedTexture(DXGI_FORMAT_R8G8B8A8_UNORM, width, height, colorTexture);
createSharedTexture(DXGI_FORMAT_R32_FLOAT, width, height, depthTexture);
```

Create one shared fence, separate copyable footprints/readback buffers, and one completion event. Submission records barriers for both textures, copies both, returns both to `COMMON`, executes one command list, and signals once. Inspection waits for the exact even value and fingerprints both mapped readbacks with logical final-row ranges.

- [ ] **Step 4: Implement registry and JNI exports**

Add positive monotonic registry IDs and these JNI methods:

```cpp
nativeOpenD3D12PersistentFrameSession(int width, int height)
nativeSubmitD3D12PersistentFrameReadback(long sessionId, long wait, long signal)
nativeInspectD3D12PersistentFrameReadback(long sessionId, long signal)
nativeCloseD3D12PersistentFrameSession(long sessionId)
```

Return the exact arrays documented in Task 1. Convert min/max floats through `std::bit_cast<std::uint32_t>` or `memcpy` because the project remains C++17.

- [ ] **Step 5: Build and run all native tests**

Run full Release build and CTest.

Expected: the existing eight tests plus depth fingerprint and frame session all pass; the real 4C GPU probe remains green.

---

### Task 4: Java Depth Fingerprint And Shader Source

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/DepthFrameFingerprint.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftDepthExtractionShaderSource.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/DepthFrameFingerprintSelfTest.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/MinecraftDepthExtractionShaderSourceSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: `DepthFrameFingerprint.fromR32f(float[])` and `fromR32fBytes(byte[])`.
- Produces: fixed GLSL 150 vertex/fragment strings consumed by the runtime shader wrapper.

- [ ] **Step 1: Write failing known-value depth tests**

```java
float[] values = {0.25f, 1.0f, 0.5f, 1.0f};
DepthFrameFingerprint result = DepthFrameFingerprint.fromR32f(values);
if (!result.nonUniform() || result.finiteSampleCount() != 4
        || result.sceneSampleCount() != 2 || result.farSampleCount() != 2
        || result.minimum() != 0.25f || result.maximum() != 1.0f) {
    throw new AssertionError("Depth statistics mismatch");
}
```

Require the same FNV hash from raw little-endian bytes and reject NaN/infinite/out-of-range samples in success gating.

- [ ] **Step 2: Write failing shader-source tests**

Assert the fragment source contains `#version 150`, `uniform sampler2D DepthSampler`, `texelFetch`, `ivec2(gl_FragCoord.xy)`, and `outDepth = texelFetch(...).r`, and does not contain linearization math or `texture(` filtering.

- [ ] **Step 3: Run Java verification and confirm RED**

Expected: missing fingerprint and shader-source classes.

- [ ] **Step 4: Implement pure Java helpers**

Use `Float.floatToRawIntBits`, emit bytes least-significant first, use the established FNV constants, and apply the `0.9999f` far threshold. Shader source must draw a full-screen triangle from `gl_VertexID` and use exact integer fetches.

- [ ] **Step 5: Register tests and run green**

Add both production files and tests to `scripts/verify-java.ps1` and require all Java self-tests to pass.

---

### Task 5: Fabric Snapshot And State Contracts

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/PersistentFrameCaptureState.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftPersistentFrameCaptureSnapshot.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftPersistentFrameCaptureOverlayLines.java`
- Create: `mc-dlss-fabric/src/testSmoke/java/dev/mcdlss/fabric/MinecraftPersistentFrameCaptureSnapshotSelfTest.java`
- Modify: `scripts/verify-java.ps1`

**Interfaces:**
- Produces: immutable progress/success contract used by the client mod and runtime probe.
- Consumes: 120/30 thresholds and color/depth validation metrics.

- [ ] **Step 1: Write failing success-gating tests**

Construct a complete snapshot with `120/120`, `30/30`, `60/60`, positive color/depth hash changes, matching GL/D3D hashes, valid depth counts, and released resources; require `success=true`. Change each critical field individually and require failure.

- [ ] **Step 2: Run Java verification and confirm RED**

Expected: snapshot and overlay classes are missing.

- [ ] **Step 3: Implement snapshot validation**

The `success()` method must require:

```java
return state == PersistentFrameCaptureState.COMPLETE
        && successfulFrames == targetFrames
        && retainedFrames == targetRetainedFrames
        && slotAUses > 0 && slotBUses > 0
        && colorHashChanges > 0 && depthHashChanges > 0
        && openGlColorHash.equals(d3d12ColorHash)
        && openGlDepthHash.equals(d3d12DepthHash)
        && depthFiniteSampleCount == width * height
        && depthInRangeSampleCount == width * height
        && depthSceneSampleCount > 0 && depthFarSampleCount > 0
        && resourcesReleased;
```

- [ ] **Step 4: Implement compact overlay lines**

Include state, `frames`, `retained`, `slots`, color/depth changes, depth range/sample counts, timing, release, and message. Keep lines short enough for the existing 570-pixel-wide test window.

- [ ] **Step 5: Register and pass the test**

Run `scripts/verify-java.ps1` and require all prior snapshot tests to remain green.

---

### Task 6: Fabric Runtime Depth Extraction And Paired Capture

**Files:**
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftDepthExtractionShader.java`
- Create: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/MinecraftPersistentFrameCaptureProbe.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricEntrypoint.java`
- Modify: `mc-dlss-fabric/src/main/java/dev/mcdlss/fabric/McDlssFabricClientMod.java`

**Interfaces:**
- Consumes: core frame-session bridge, shader source, depth fingerprint, fence schedule, and snapshot contracts.
- Produces: one `onWorldFrame()`/`onClientTick()` state machine and `[mc_dlss/fabric/persistent-frame]` lifecycle logs.

- [ ] **Step 1: Add frame-session delegation to the entrypoint**

Expose the four `NativeBridge` methods with no additional policy.

- [ ] **Step 2: Implement shader compile/link lifecycle**

`MinecraftDepthExtractionShader` owns program and VAO, includes complete shader compile/link info logs in exceptions, binds Minecraft depth attachment to texture unit 0, renders three vertices, and deletes program/VAO idempotently.

- [ ] **Step 3: Implement transactional frame slots**

Each slot owns native session ID, two memory objects, two imported textures, one semaphore, color/depth FBOs, GL readback buffers, and use count. Save and restore bindings around pair creation. If either texture or second slot fails, release the complete partial pair.

- [ ] **Step 4: Implement paired per-frame capture**

At `WorldRenderEvents.END`:

```java
copyWorldColor(source, slot);
extractDepth(source.getDepthAttachment(), slot);
DepthFrameFingerprint glDepth = readAndFingerprintDepth(slot);
signalBothTextures(slot, fenceValues.waitValue());
submitFrameReadback(slot, fenceValues);
waitForBothTextures(slot, fenceValues.signalValue());
NativeFrameReadbackFingerprint nativeFrame = inspectFrame(slot, fenceValues);
validateColorAndDepth(glColor, glDepth, nativeFrame);
```

Semaphore barrier arrays contain both texture IDs and `GL_LAYOUT_GENERAL_EXT` for both resources.

- [ ] **Step 5: Restore complete OpenGL state**

Capture and restore read/draw framebuffer, program, VAO, active texture, texture binding on the used unit, viewport, color mask, and enabled states for depth, blend, scissor, and cull. Restoration runs in `finally` before any failure is published.

- [ ] **Step 6: Implement 120/30 lifecycle and resize**

Alternate slots, count color/depth hash changes, rebuild both slots on dimension changes, retain for 30 frames, release shader and slots on completion/failure/world unload, and cache terminal state.

- [ ] **Step 7: Integrate client logging and HUD**

Start the new probe only after 4D-D reaches `COMPLETE`. Log only state transitions with prefix `[mc_dlss/fabric/persistent-frame]`. Append the new overlay after the color-only overlay.

- [ ] **Step 8: Compile Fabric and run full Gradle build**

Run outside the managed sandbox cache when required:

```powershell
.\gradlew.bat :mc-dlss-fabric:compileJava --console=plain --no-daemon --no-parallel
.\gradlew.bat build --console=plain --no-daemon --no-parallel
```

Expected: `BUILD SUCCESSFUL` and all previous modules compile.

---

### Task 7: Runtime Acceptance And Documentation

**Files:**
- Create: `outputs/fabric-persistent-full-resolution-color-depth-2026-07-11.log`
- Modify: `docs/local-status-2026-07-10.md`
- Modify: `docs/backend-feasibility-spike-criteria.md`
- Modify: `docs/windows-toolchain.md`

**Interfaces:**
- Consumes: final `[mc_dlss/fabric/persistent-frame]` log line.
- Produces: durable runtime evidence and updated next-step status.

- [ ] **Step 1: Run fresh automated verification**

Run Java verification, full native Release build, all CTests, and full Gradle build. Require zero failures; do not rerun optional Streamline.

- [ ] **Step 2: Launch the Fabric client and enter `New World`**

Use the approved Windows Computer control path. Wait for 4D-D color completion and then 4D-E frame capture.

- [ ] **Step 3: Verify live acceptance**

Require the final line to contain:

```text
state=COMPLETE success=true frames=120/120 retained=30/30
slotAUses=60 slotBUses=60 colorHashChanges>0 depthHashChanges>0
depthFinite=<width*height> depthInRange=<width*height>
depthScene>0 depthFar>0 resourcesReleased=true
```

Require matching GL/D3D color hashes and matching GL/D3D depth hashes. If depth hashes remain static, move the camera and rerun in a fresh process.

- [ ] **Step 4: Preserve evidence and close cleanly**

Copy `mc-dlss-fabric/run/logs/latest.log` to the output path, save and quit the world, quit Minecraft, and confirm no Minecraft window remains. Treat Computer screenshots as live visual verification only; do not decode incremental screenshot payloads to disk.

- [ ] **Step 5: Update project status documents**

Record exact dimensions, hashes, depth range/counts, timing, tests, and remaining non-claims. Change the next practical step to motion vectors plus camera/jitter constants; keep `dlss-ready=false`.

## Self-Review

- Every requirement in the 4D-E design is assigned to a task: typed JNI contract, R32_FLOAT depth extraction, one-fence paired copy, exact depth hashing/statistics, 120/30 lifecycle, resize, cleanup, HUD/logs, tests, runtime evidence, and docs.
- Existing color-only JNI methods remain unchanged; all new primitive arrays have fixed field order and decoder tests.
- Java and native depth hashing both use raw little-endian IEEE-754 bytes, logical rows only, and threshold `0.9999f`.
- Runtime work starts only after pure contract, fingerprint, native lifecycle, and snapshot tests are green.
- No placeholders, unspecified handlers, or conflicting method names remain.
