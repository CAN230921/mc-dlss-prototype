# Milestone 3C Client Overlay Design

## Goal

Render the existing diagnostic snapshot in the Minecraft client HUD for both Fabric and NeoForge development builds.

This continues Milestone 3 by moving from startup logging to an actual client-side diagnostic display. It remains a diagnostic feature only; it does not add renderer hooks, Iris/Sodium integration, resource capture, or DLSS evaluation.

## Selected Approach

Use a shared text model in `mc-dlss-debug` and small loader-specific render adapters:

- `DlssOverlayLines.fromSnapshot(DlssDebugSnapshot)` returns short, stable lines suitable for HUD drawing.
- Fabric registers a client entrypoint using Fabric API's `HudRenderCallback`.
- NeoForge registers a GUI layer with `RegisterGuiLayersEvent` above the vanilla debug overlay.
- Both loaders draw the same lines in the top-left corner.

## Version Choices

- Minecraft: `1.21.1`
- Fabric API: `0.116.13+1.21.1`, latest `+1.21.1` entry from Fabric Maven metadata.
- Fabric HUD API: `net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback`
- NeoForge GUI API: `net.neoforged.neoforge.client.event.RegisterGuiLayersEvent`

## Display Content

The overlay shows:

- `MC DLSS Prototype`
- `native: <available/unavailable> <version>`
- `backend: <backend> <resourcePathStatus>`
- `dlss-ready: <true/false>`
- `message: <current blocker>`

The message line is truncated to keep the HUD compact.

## Non-Goals

- No toggle/keybind yet.
- No config screen.
- No Iris/Sodium detection yet.
- No frame counter/current world yet.
- No screenshot automation yet.
- No DLSS resource or render-size mutation.

## Testing

- Add dependency-free smoke tests for `DlssOverlayLines`.
- Keep metadata verification.
- Compile Fabric and NeoForge loader modules with Gradle.
- If a dev client is launched, use it as manual visual validation only; build verification remains the automated gate for this step.
