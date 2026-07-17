# Milestone 3A Diagnostic Surface Design

## Goal

Add a dependency-free diagnostic surface that Fabric and NeoForge adapters can expose before real Minecraft renderer hooks exist.

This is a bridge toward Milestone 3. It does not create an in-game overlay yet; it creates the shared status object and adapter methods that an overlay or debug command can render later.

## Selected Approach

Use a shared `mc-dlss-debug` snapshot factory and one adapter method per loader.

The factory consumes:

- `NativeProbeResult` from `mc-dlss-core`
- `DlssBackendDiagnostic` from `mc-dlss-core`

It produces:

- `DlssDebugSnapshot`, with native state, selected mode, dimensions, backend diagnostic, native version, and a current user-facing error/status message.

Each loader adapter adds:

- `debugSnapshot()`: probes native state and returns a snapshot with `DlssBackendDiagnostic.currentIrisSodiumOpenGlBlocked()`.

## Non-Goals

- No Fabric Loom or NeoForge Gradle plugin setup in this step.
- No Minecraft client launch.
- No mixins, HUD rendering, commands, keybinds, or config screen.
- No DLSS resource capture.
- No attempt to bypass the current OpenGL backend blocker.

## Data Flow

1. Loader adapter owns a `NativeBridge`.
2. `debugSnapshot()` calls `nativeBridge.probeSystem()`.
3. The adapter supplies the canonical current backend diagnostic.
4. `DlssDebugSnapshotFactory.fromProbe(...)` produces a normalized snapshot.
5. Future overlay/command code can render the snapshot without knowing Fabric or NeoForge details.

## Error Handling

- Null native probe results are treated as unavailable with version `not-loaded`.
- Null backend diagnostics default to `currentIrisSodiumOpenGlBlocked()`.
- If native probing is available but backend is blocked, the snapshot still reports `nativeAvailable=true` and uses the backend diagnostic message as the current blocker.
- If native probing is unavailable, the native probe message is the current blocker.

## Testing

Use dependency-free smoke tests:

- Debug factory test for unavailable native probe.
- Debug factory test for available native probe but blocked OpenGL backend.
- Fabric adapter test with a fake `NativeBridge`.
- NeoForge adapter test with a fake `NativeBridge`.

The existing `scripts/verify-java.ps1` should compile and run these smoke tests without Minecraft, Fabric, or NeoForge dependencies.
